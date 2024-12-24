package com.example.visualprog.location

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.*

class LocationModule(
    context: Context,
    private val onLocationUpdated: (Double, Double) -> Unit
) {

    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
    private val locationRequest: LocationRequest = LocationRequest.create().apply {
        interval = 250
        fastestInterval = 250
        priority = LocationRequest.PRIORITY_HIGH_ACCURACY
    }
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            for (location in locationResult.locations) {
                onLocationUpdated(location.latitude, location.longitude)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
    }

    fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    @SuppressLint("MissingPermission")
    fun getCurrentLocation(onLocationReceived: (Double, Double) -> Unit) {
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                onLocationReceived(location.latitude, location.longitude)
            } else {
                onLocationReceived(0.0, 0.0)
            }
        }.addOnFailureListener { exception ->
            exception.printStackTrace()
            onLocationReceived(0.0, 0.0)
        }
    }
}
