//package com.example.ondemandflightdata
//
//import android.content.Context
//import androidx.lifecycle.viewModelScope
//import androidx.work.CoroutineWorker
//import androidx.work.Worker
//import androidx.work.WorkerParameters
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.launch
//import kotlinx.coroutines.withContext
//
////class FlightDataWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
////    override suspend fun doWork(): Result {
////        val viewModel = FlightViewModel()
////        val dao = AppDatabase.getDatabase(applicationContext).flightDurationDao()
////
////        try {
////            val response = RetrofitInstance.api.fetchFlightData(
////                accessKey = "7b8a8a5360149db1133ade6ca2f89fb0",
////                departure = "JFK",
////                arrival = "LAX"
////            )
////            val flights = viewModel.deduplicateFlightsByIATA(response.data).take(3)
////
////            withContext(Dispatchers.IO) {
////                flights.forEach { flight ->
////                    val durationMinutes = calculateFlightDuration(flight) // Implement this
////                    if (durationMinutes > 0) {
////                        dao.insertDuration(
////                            FlightDurationEntity(
////                                flightCode = flight.flight?.iata ?: "Unknown",
////                                durationMinutes = durationMinutes,
////                                departureAirport = flight.departure?.iata ?: "N/A",
////                                arrivalAirport = flight.arrival?.iata ?: "N/A"
////                            )
////                        )
////                    }
////                }
////            }
////            return Result.success()
////        } catch (e: Exception) {
////            e.printStackTrace()
////            return Result.retry()
////        }
////    }
////
////    private fun calculateFlightDuration(flight: Flight): Int {
////        // Placeholder: Replace with real logic using API data (e.g., departure/arrival times)
////        return flight.live?.speed_horizontal?.let { speed ->
////            val dummyDistance = 4000 // Assume 4000 km
////            (dummyDistance / speed * 60).toInt() // Convert to minutes
////        } ?: 120 // Default to 120 minutes if no live data
////    }
////}
//
//class FlightDataWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
//    override suspend fun doWork(): Result {
//        val viewModel = FlightViewModel()
//        val dao = AppDatabase.getDatabase(applicationContext).flightDurationDao()
//        val flightCode = viewModel.lastRequestedFlightCode.value
//
//        if (flightCode.isNotBlank()) {
//            try {
//                val response = RetrofitInstance.api.fetchFlightByCode(
//                    accessKey = "7b8a8a5360149db1133ade6ca2f89fb0",
//                    flight_iata = flightCode
//                )
//                val flights = viewModel.deduplicateFlightsByIATA(response.data)
//                if (flights.isNotEmpty()) {
//                    val flight = flights.first()
//                    val duration = viewModel.calculateFlightDuration(flight)
//                    if (duration > 0) {
//                        val entity = FlightDurationEntity(
//                            flightCode = flightCode,
//                            durationMinutes = duration,
//                            departureAirport = flight.departure?.iata ?: "N/A",
//                            arrivalAirport = flight.arrival?.iata ?: "N/A"
//                        )
//                        dao.insertDuration(entity)
//                    }
//                }
//                return Result.success()
//            } catch (e: Exception) {
//                e.printStackTrace()
//                return Result.retry()
//            }
//        }
//        return Result.success()
//    }
//}