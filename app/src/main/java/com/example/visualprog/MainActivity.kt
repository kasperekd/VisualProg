package com.example.visualprog

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.location.*

class MainActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var latitudeTextView: TextView
    private lateinit var longitudeTextView: TextView
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    /**
     * Called when the activity is starting.
     * Initializes the layout, sets up the location client, and requests location permissions.
     *
     * @param savedInstanceState If the activity is being re-initialized after previously being shut down
     * by the system, this Bundle contains the data it most recently supplied in
     * [onSaveInstanceState]. Otherwise, it is null.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Initialize TextView for latitude and longitude display
        latitudeTextView = findViewById(R.id.Latitude)
        longitudeTextView = findViewById(R.id.Longitude)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize the FusedLocationProviderClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Initialize LocationRequest
        locationRequest = LocationRequest.create().apply {
            interval = 1000 // 1 second
            fastestInterval = 1000 // 1 second
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        // Initialize LocationCallback
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    // Update the TextViews with the new location
                    latitudeTextView.text = "Latitude: ${location.latitude}"
                    longitudeTextView.text = "Longitude: ${location.longitude}"
                }
            }
        }

        // Request location permission
        requestLocationPermission()
    }

    /**
     * Requests permission to access the user's location.
     * If permission is granted, it starts location updates.
     */
    private fun requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Request permission
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            // Permission already granted, start location updates
            startLocationUpdates()
        }
    }

    /**
     * Launcher for the permission request.
     * Handles the result of the permission request.
     *
     * @param isGranted Indicates whether the permission was granted or denied.
     */
    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted, start location updates
            startLocationUpdates()
        } else {
            Toast.makeText(this, "Permission to access location denied", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Starts location updates using the FusedLocationProviderClient.
     * Updates the TextViews with the current location if permission is granted.
     */
    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
        }
    }

    /**
     * Called when the activity is paused.
     * Stops location updates to conserve battery and resources.
     */
    override fun onPause() {
        super.onPause()
        // Stop location updates when the activity is not in the foreground
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    /**
     * Called when the activity is resumed.
     * Restarts location updates when the activity comes to the foreground.
     */
    override fun onResume() {
        super.onResume()
        // Restart location updates when the activity comes to the foreground
        startLocationUpdates()
    }
}
