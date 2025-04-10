OnDemandFlightData

OnDemandFlightData is an Android application built with Jetpack Compose that allows users to track live flight information and calculate average flight durations using the AviationStack API (https://aviationstack.com/). The app stores flight data in a local Room database and uses WorkManager for periodic background updates.

Features
--------
- Flight Search: Search flights by departure and arrival airport IATA codes.
- Flight Tracking: Track flights by flight code (e.g., "AA1004") with live data such as altitude, speed, and location.
- Average Flight Time: Calculate the average duration of a flight over the past week, based on stored data or fresh API calls.
- Database Persistence: Stores flight durations in a local Room database, persisting across app restarts.
- Background Updates: Periodically fetches flight data in the background using WorkManager (daily updates).
- Error Handling: Displays user-friendly error messages for invalid inputs or API failures.

Screenshots
-----------
(Add screenshots here if available, e.g., Home screen, Track Flight screen, Average Time screen with database entries)
Example:
- Home Screen: screenshots/home_screen.png
- Track Flight: screenshots/track_flight.png
- Average Time: screenshots/average_time.png

Prerequisites
-------------
- Android Studio: Version 2023.1.1 or later (e.g., Flamingo).
- Kotlin: Version 1.9.0 or higher.
- API Key: A valid AviationStack API key (https://aviationstack.com/) (currently hardcoded as "7b8a8a5360149db1133ade6ca2f89fb0").
- Internet Connection: Required for API calls and background updates.

Setup Instructions
------------------
1. Clone the Repository:
   git clone https://github.com/yourusername/ondemandflightdata.git
   cd ondemandflightdata

2. Open in Android Studio:
    - Launch Android Studio and select "Open an existing project".
    - Navigate to the cloned directory and open it.

3. Configure API Key:
    - The app uses a hardcoded AviationStack API key ("7b8a8a5360149db1133ade6ca2f89fb0"). For production use, replace this with your own key:
        - Open FlightViewModel.kt.
        - Update the accessKey parameter in fetchFlightData and fetchFlightByCode calls.
    - Alternatively, move the key to a secure location (e.g., local.properties or Gradle properties).

4. Sync and Build:
    - Sync the project with Gradle by clicking "Sync Project with Gradle Files".
    - Build the project (Build > Make Project).

5. Run the App:
    - Connect an Android device or start an emulator.
    - Click Run > Run 'app' to launch the app.

Usage
-----
Home Screen
- Enter departure and arrival IATA codes (e.g., "JFK" and "LAX").
- Click "Search Flights" to view flight details (airline, status, live data if available).

Track Flight Screen
- Enter a flight code (e.g., "AA1004").
- Click "Track" to see detailed flight information.

Average Flight Time Screen
- Enter a flight code (e.g., "AA1004").
- Click "Calculate Average Time" to:
    - Display the average duration over the last week (if data exists in the database).
    - Fetch fresh data from the API if no recent data is available.
- View all database entries for the flight code below the average duration, including:
    - ID
    - Flight Code
    - Duration (minutes)
    - Departure Airport
    - Arrival Airport
    - Timestamp

Background Updates
- The app schedules a daily background task (FlightDataWorker) to fetch and store flight data for the last requested flight code.
- Check Logcat with tag FlightDataWorker to monitor insertions.

Project Structure
-----------------
- FlightViewModel.kt: Manages app state and handles API calls, database operations, and duration calculations.
- AppDatabase.kt: Defines the Room database, entity (FlightDurationEntity), and DAO (FlightDurationDataAccessObject).
- RetrofitInstance.kt: Sets up Retrofit for AviationStack API calls.
- FlightDataWorker.kt: Implements background data fetching with WorkManager.
- MainActivity.kt: Contains the UI (Jetpack Compose) and navigation logic.

Dependencies
------------
- Jetpack Compose: UI toolkit for building the interface.
- Room: Local database for storing flight durations.
- Retrofit: HTTP client for API requests.
- WorkManager: Background task scheduling.
- Kotlin Coroutines: Asynchronous programming.

Add these to your build.gradle (app-level) if not already present:
dependencies {
implementation "androidx.core:core-ktx:1.12.0"
implementation "androidx.activity:activity-compose:1.9.0"
implementation "androidx.compose.material3:material3:1.2.1"
implementation "androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0"
implementation "androidx.navigation:navigation-compose:2.7.7"
implementation "androidx.room:room-runtime:2.6.1"
kapt "androidx.room:room-compiler:2.6.1"
implementation "androidx.room:room-ktx:2.6.1"
implementation "com.squareup.retrofit2:retrofit:2.9.0"
implementation "com.squareup.retrofit2:converter-gson:2.9.0"
implementation "androidx.work:work-runtime-ktx:2.9.0"
}

Limitations
-----------
- API Key Exposure: Hardcoded API key should be secured in production.
- Dummy Distance: Flight duration calculation uses a fixed 4000 km distance due to limited API data (no actual departure/arrival times).
- Background Task Timing: Daily updates may not run immediately; use a one-time work request for testing:
  val flightDataRequest = OneTimeWorkRequestBuilder<FlightDataWorker>().setConstraints(constraints).build()
  WorkManager.getInstance(this).enqueue(flightDataRequest)

Troubleshooting
---------------
- No Flights Found: Ensure valid IATA codes or flight codes are used (e.g., "AA1004"). Check API key validity.
- Database Not Updating: Verify internet connectivity and check Logcat (FlightTracker, DatabaseEntries, FlightDataWorker).
- Errors: Review error messages in the UI or Logcat for details.

Future Enhancements
-------------------
- Secure API key storage.
- Use real flight duration data from the API (if available).
- Add a manual refresh button for database entries.
- Implement a settings screen to configure background update frequency.

Contributing
------------
1. Fork the repository.
2. Create a feature branch (git checkout -b feature-name).
3. Commit changes (git commit -m "Add feature").
4. Push to the branch (git push origin feature-name).
5. Open a pull request.

License
-------
This project is licensed under the MIT License - see the LICENSE file for details.

Acknowledgments
---------------
- AviationStack (https://aviationstack.com/) for flight data.
- Jetpack Compose and Android team for modern development tools.