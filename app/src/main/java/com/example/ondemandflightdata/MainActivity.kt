package com.example.ondemandflightdata

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.ondemandflightdata.ui.theme.OnDemandFlightDataTheme
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// Room Entity
@Entity
data class FlightDurationEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val flightCode: String,
    val durationMinutes: Int,
    val departureAirport: String,
    val arrivalAirport: String,
    val timestamp: Long = System.currentTimeMillis()
)

// Room DAO
@Dao
interface FlightDurationDataAccessObject {
    @Insert
    suspend fun insertDuration(duration: FlightDurationEntity)

    @androidx.room.Query("SELECT * FROM FlightDurationEntity WHERE flightCode = :flightCode AND timestamp > :weekAgo")
    suspend fun getDurationsForWeek(flightCode: String, weekAgo: Long): List<FlightDurationEntity>

    @androidx.room.Query("SELECT * FROM FlightDurationEntity WHERE flightCode = :flightCode")
    suspend fun getAllDurationsForFlight(flightCode: String): List<FlightDurationEntity>
}

// Room Database
@Database(entities = [FlightDurationEntity::class], version = 2)
abstract class AppDatabase : RoomDatabase() {
    abstract fun flightDurationDao(): FlightDurationDataAccessObject

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "flight_database"
                ).addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE FlightDurationEntity ADD COLUMN timestamp INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}

// API Data Models
data class AviationStackResponse(
    val data: List<Flight>
)

data class Flight(
    val flight: FlightInfo?,
    val departure: AirportInfo?,
    val arrival: AirportInfo?,
    val airline: AirlineInfo?,
    val flight_status: String?,
    val live: LiveData?
)

data class LiveData(
    val updated: String?,
    val latitude: Double?,
    val longitude: Double?,
    val altitude: Double?,
    val direction: Double?,
    val speed_horizontal: Double?,
    val speed_vertical: Double?,
    val is_ground: Boolean?
)

data class FlightInfo(val iata: String?, val icao: String?)
data class AirportInfo(val iata: String?, val icao: String?, val airport: String?)
data class AirlineInfo(val name: String?)

// Retrofit API Service
interface FlightApiService {
    @GET("flights")
    suspend fun fetchFlightData(
        @Query("access_key") accessKey: String,
        @Query("dep_iata") departure: String,
        @Query("arr_iata") arrival: String
    ): AviationStackResponse

    @GET("flights")
    suspend fun fetchFlightByCode(
        @Query("access_key") accessKey: String,
        @Query("flight_iata") flight_iata: String
    ): AviationStackResponse
}

// Retrofit Instance
object RetrofitInstance {
    val api: FlightApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.aviationstack.com/v1/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FlightApiService::class.java)
    }
}

// ViewModel
class FlightViewModel : ViewModel() {
    var flightList = mutableStateOf(emptyList<Flight>())
    var averageDuration = mutableStateOf(0)
    var lastRequestedFlightCode = mutableStateOf("")
    var errorMessage = mutableStateOf<String?>(null)

    fun deduplicateFlightsByIATA(flights: List<Flight>): List<Flight> {
        return flights
            .filter { it.flight?.iata != null }
            .distinctBy { it.flight?.iata }
            .associateBy { it.flight?.iata }
            .values
            .toList()
    }

    fun fetchFlights(departure: String, arrival: String) {
        viewModelScope.launch {
            try {
                val response = RetrofitInstance.api.fetchFlightData(
                    accessKey = "7b8a8a5360149db1133ade6ca2f89fb0",
                    departure = departure,
                    arrival = arrival
                )
                val deduped = deduplicateFlightsByIATA(response.data)
                if (deduped.isNotEmpty()) {
                    flightList.value = deduped
                    errorMessage.value = null
                } else {
                    flightList.value = emptyList()
                    errorMessage.value = "No flights found for $departure to $arrival"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                flightList.value = emptyList()
                errorMessage.value = "Error fetching flights. Please try again."
            }
        }
    }

    fun fetchFlightByCode(flight_iata: String) {
        viewModelScope.launch {
            try {
                val response = RetrofitInstance.api.fetchFlightByCode(
                    accessKey = "7b8a8a5360149db1133ade6ca2f89fb0",
                    flight_iata = flight_iata
                )
                val deduped = deduplicateFlightsByIATA(response.data)
                if (deduped.isNotEmpty()) {
                    flightList.value = deduped
                    errorMessage.value = null
                } else {
                    flightList.value = emptyList()
                    errorMessage.value = "Please enter a valid flight number (e.g., AA1004)"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                flightList.value = emptyList()
                errorMessage.value = "Error fetching flight data. Please try again."
            }
        }
    }

    fun calculateAverageDuration(context: Context, flightCode: String) {
        viewModelScope.launch {
            val dao = AppDatabase.getDatabase(context).flightDurationDao()
            val weekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
            val durations = dao.getDurationsForWeek(flightCode, weekAgo)

            if (durations.isNotEmpty()) {
                averageDuration.value = durations.map { it.durationMinutes }.average().toInt()
                errorMessage.value = null
                Log.d("FlightTracker", "Using stored durations for $flightCode: $durations")
            } else {
                try {
                    val response = RetrofitInstance.api.fetchFlightByCode(
                        accessKey = "7b8a8a5360149db1133ade6ca2f89fb0",
                        flight_iata = flightCode
                    )
                    val flights = response.data
                    Log.d("FlightTracker", "Fetched flights for $flightCode: $flights")
                    if (flights.isNotEmpty()) {
                        val durationsFromApi = flights.mapNotNull { flight ->
                            val duration = calculateFlightDuration(flight)
                            if (duration > 0) {
                                val entity = FlightDurationEntity(
                                    flightCode = flightCode,
                                    durationMinutes = duration,
                                    departureAirport = flight.departure?.iata ?: "N/A",
                                    arrivalAirport = flight.arrival?.iata ?: "N/A"
                                )
                                dao.insertDuration(entity)
                                duration
                            } else null
                        }
                        averageDuration.value = if (durationsFromApi.isNotEmpty()) {
                            durationsFromApi.average().toInt()
                        } else {
                            0
                        }
                        errorMessage.value = null
                    } else {
                        averageDuration.value = 0
                        errorMessage.value = "Please enter a valid flight number"
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    averageDuration.value = 0
                    errorMessage.value = "Error fetching flight data. Please try again."
                }
            }
            lastRequestedFlightCode.value = flightCode
        }
    }

    fun calculateFlightDuration(flight: Flight): Int {
        return flight.live?.speed_horizontal?.let { speed ->
            val dummyDistance = 4000
            (dummyDistance / speed * 60).toInt()
        } ?: 120
    }
}

// Main Activity
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        scheduleFlightDataWorker()
        setContent {
            OnDemandFlightDataTheme {
                val navController = rememberNavController()
                val items = listOf(Screen.Home, Screen.TrackFlight, Screen.AverageFlightTime)

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
                            items.forEach { screen ->
                                NavigationBarItem(
                                    icon = { Icon(screen.icon, contentDescription = screen.title) },
                                    label = { Text(screen.title) },
                                    selected = currentRoute == screen.route,
                                    onClick = {
                                        navController.navigate(screen.route) {
                                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Home.route,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable(Screen.Home.route) { HomeScreen() }
                        composable(Screen.TrackFlight.route) { TrackFlightScreen() }
                        composable(Screen.AverageFlightTime.route) { AverageFlightTimeScreen() }

                    }
                }
            }
        }
    }

    private fun scheduleFlightDataWorker() {
        val workManager = WorkManager.getInstance(this)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val flightDataRequest = PeriodicWorkRequestBuilder<FlightDataWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            "flightDataWork",
            ExistingPeriodicWorkPolicy.KEEP,
            flightDataRequest
        )
    }
}

// Background Worker
class FlightDataWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val viewModel = FlightViewModel()
        val dao = AppDatabase.getDatabase(applicationContext).flightDurationDao()
        val flightCode = viewModel.lastRequestedFlightCode.value

        if (flightCode.isNotBlank()) {
            try {
                val response = RetrofitInstance.api.fetchFlightByCode(
                    accessKey = "7b8a8a5360149db1133ade6ca2f89fb0",
                    flight_iata = flightCode
                )
                val flights = response.data
                if (flights.isNotEmpty()) {
                    flights.forEach { flight ->
                        val duration = viewModel.calculateFlightDuration(flight)
                        if (duration > 0) {
                            val entity = FlightDurationEntity(
                                flightCode = flightCode,
                                durationMinutes = duration,
                                departureAirport = flight.departure?.iata ?: "N/A",
                                arrivalAirport = flight.arrival?.iata ?: "N/A"
                            )
                            dao.insertDuration(entity)
                            Log.d("FlightDataWorker", "Inserted: $entity")
                        }
                    }
                }
                return Result.success()
            } catch (e: Exception) {
                e.printStackTrace()
                return Result.retry()
            }
        }
        return Result.success()
    }
}

// Navigation Screens
sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Default.Home)
    object TrackFlight : Screen("track", "Track", Icons.AutoMirrored.Filled.Send)
    object AverageFlightTime : Screen("average", "Average Time", Icons.Default.DateRange)

}

// Composables
@Composable
fun HomeScreen(viewModel: FlightViewModel = viewModel()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Welcome to Flight Tracker!!!")
        Text(text = "This will help you track your friend!!!")
        FlightScreen(viewModel)
    }
}

@Composable
fun FlightScreen(viewModel: FlightViewModel) {
    var departure by remember { mutableStateOf("") }
    var arrival by remember { mutableStateOf("") }
    var showDialog by remember { mutableStateOf(false) }

    if (viewModel.errorMessage.value != null && showDialog) {
        AlertDialog(
            onDismissRequest = {
                showDialog = false
                viewModel.errorMessage.value = null
            },
            title = { Text("Error") },
            text = { Text(viewModel.errorMessage.value ?: "Unknown error") },
            confirmButton = {
                TextButton(onClick = {
                    showDialog = false
                    viewModel.errorMessage.value = null
                }) {
                    Text("OK")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = departure,
            onValueChange = { departure = it },
            label = { Text("Departure Airport IATA Code") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = arrival,
            onValueChange = { arrival = it },
            label = { Text("Arrival Airport IATA Code") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (departure.isNotBlank() && arrival.isNotBlank()) {
                    viewModel.fetchFlights(departure, arrival)
                    showDialog = true
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Search Flights")
        }

        Spacer(modifier = Modifier.height(16.dp))

        FlightList(viewModel.flightList.value)
    }
}

@Composable
fun FlightList(flights: List<Flight>) {
    if (flights.isEmpty()) {
        Text(
            text = "No flights found",
            modifier = Modifier.padding(16.dp)
        )
    }
    LazyColumn {
        items(flights) { flight ->
            Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Flight: ${flight.flight?.iata ?: "Unknown"}", fontWeight = FontWeight.Bold)
                    Text("Airline: ${flight.airline?.name ?: "N/A"}")
                    Text("From: ${flight.departure?.airport ?: "N/A"} (${flight.departure?.iata ?: ""})")
                    Text("To: ${flight.arrival?.airport ?: "N/A"} (${flight.arrival?.iata ?: ""})")
                    Text("Status: ${flight.flight_status ?: "Unknown"}")
                    flight.live?.let { live ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Divider()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("📍 Live Flight Data", fontWeight = FontWeight.Bold)
                        Text("Last Updated: ${live.updated ?: "N/A"}")
                        Text("Latitude: ${live.latitude ?: "N/A"}")
                        Text("Longitude: ${live.longitude ?: "N/A"}")
                        Text("Altitude: ${live.altitude?.toInt() ?: "N/A"} meters")
                        Text("Direction: ${live.direction?.toInt() ?: "N/A"}°")
                        Text("Speed (H): ${live.speed_horizontal?.toInt() ?: "N/A"} km/h")
                        Text("Speed (V): ${live.speed_vertical?.toInt() ?: "N/A"} km/h")
                        Text("On Ground: ${if (live.is_ground == true) "Yes" else "No"}")
                    }
                }
            }
        }
    }
}

@Composable
fun TrackFlightScreen(viewModel: FlightViewModel = viewModel()) {
    var flight_iata by remember { mutableStateOf("") }
    var showDialog by remember { mutableStateOf(false) }

    if (viewModel.errorMessage.value != null && showDialog) {
        AlertDialog(
            onDismissRequest = {
                showDialog = false
                viewModel.errorMessage.value = null
            },
            title = { Text("Error") },
            text = { Text(viewModel.errorMessage.value ?: "Unknown error") },
            confirmButton = {
                TextButton(onClick = {
                    showDialog = false
                    viewModel.errorMessage.value = null
                }) {
                    Text("OK")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Track Flight by Flight Code", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = flight_iata,
            onValueChange = { flight_iata = it },
            label = { Text("Flight Code (e.g. AA1004)") }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            if (flight_iata.isNotBlank()) {
                viewModel.fetchFlightByCode(flight_iata)
                showDialog = true
            }
        }) {
            Text("Track")
        }

        Spacer(modifier = Modifier.height(24.dp))

        FlightList(viewModel.flightList.value)
    }
}

@Composable
fun AverageFlightTimeScreen(viewModel: FlightViewModel = viewModel()) {
    var flightCode by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    var databaseEntries by remember { mutableStateOf<List<FlightDurationEntity>>(emptyList()) }
    val coroutineScope = rememberCoroutineScope() // Add coroutine scope for manual launches

    // Fetch database entries when flightCode changes
    LaunchedEffect(flightCode) {
        if (flightCode.isNotBlank()) {
            val dao = AppDatabase.getDatabase(context).flightDurationDao()
            databaseEntries = dao.getAllDurationsForFlight(flightCode)
            Log.d("DatabaseEntries", "Entries for $flightCode: $databaseEntries")
        }
    }

    if (viewModel.errorMessage.value != null && showDialog) {
        AlertDialog(
            onDismissRequest = {
                showDialog = false
                viewModel.errorMessage.value = null
            },
            title = { Text("Error") },
            text = { Text(viewModel.errorMessage.value ?: "Unknown error") },
            confirmButton = {
                TextButton(onClick = {
                    showDialog = false
                    viewModel.errorMessage.value = null
                }) {
                    Text("OK")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Average Flight Time (Last Week)", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = flightCode,
            onValueChange = { flightCode = it },
            label = { Text("Flight Code (e.g. AA1004)") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (flightCode.isNotBlank()) {
                    viewModel.calculateAverageDuration(context, flightCode)
                    showDialog = true
                    // Refresh database entries after calculation
                    coroutineScope.launch {
                        val dao = AppDatabase.getDatabase(context).flightDurationDao()
                        databaseEntries = dao.getAllDurationsForFlight(flightCode)
                        Log.d("DatabaseEntries", "Entries after calculation for $flightCode: $databaseEntries")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Calculate Average Time")
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (viewModel.averageDuration.value > 0) {
            Text(
                text = "Average Duration: ${viewModel.averageDuration.value} minutes",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(8.dp)
            )
        } else if (viewModel.errorMessage.value == null) {
            Text(
                text = "No data available for this flight code in the last week.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(8.dp)
            )
        }

        // Display database entries
        Spacer(modifier = Modifier.height(16.dp))
        Text("Database Entries for $flightCode", style = MaterialTheme.typography.titleMedium)
        if (databaseEntries.isEmpty()) {
            Text("No entries found in database.", modifier = Modifier.padding(8.dp))
        } else {
            LazyColumn {
                items(databaseEntries) { entry ->
                    Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("ID: ${entry.id}")
                            Text("Flight Code: ${entry.flightCode}")
                            Text("Duration: ${entry.durationMinutes} minutes")
                            Text("Departure: ${entry.departureAirport}")
                            Text("Arrival: ${entry.arrivalAirport}")
                            Text("Timestamp: ${entry.timestamp}")
                        }
                    }
                }
            }
        }
    }
}

