package com.example.visualprog

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.telephony.CellInfoCdma
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoWcdma
import android.telephony.TelephonyManager
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*

class MainActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var latitudeTextView: TextView
    private lateinit var longitudeTextView: TextView
    private lateinit var cellInfoTextView: TextView
    private lateinit var telephonyManager: TelephonyManager
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        latitudeTextView = findViewById(R.id.Latitude)
        longitudeTextView = findViewById(R.id.Longitude)
        cellInfoTextView = findViewById(R.id.CellInfo)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager

        locationRequest = LocationRequest.create().apply {
            interval = 1000
            fastestInterval = 1000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        locationCallback = object : LocationCallback() {
            @SuppressLint("SetTextI18n")
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    latitudeTextView.text = "Latitude: ${location.latitude}"
                    longitudeTextView.text = "Longitude: ${location.longitude}"
                    getCellInfo() // Получение информации о базовых станциях
                }
            }
        }

        requestLocationPermission()
    }

    private fun requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            startLocationUpdates()
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (isGranted) {
            startLocationUpdates()
        } else {
            Toast.makeText(this, "Permission to access location denied", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun getCellInfo() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val cellInfoList = telephonyManager.allCellInfo
            if (cellInfoList != null) {
                val cellInfoStringBuilder = StringBuilder()
                for (cellInfo in cellInfoList) {
                    when (cellInfo) {
                        is CellInfoGsm -> {
                            cellInfoStringBuilder.append("Type: GSM\n")
                            cellInfoStringBuilder.append("Cell ID: ${cellInfo.cellIdentity.cid}\n")
                            cellInfoStringBuilder.append("Signal Strength: ${cellInfo.cellSignalStrength.dbm} dBm\n")
                            cellInfoStringBuilder.append("Location Area Code: ${cellInfo.cellIdentity.lac}\n")
                        }
                        is CellInfoLte -> {
                            cellInfoStringBuilder.append("Type: LTE\n")
                            cellInfoStringBuilder.append("Cell ID: ${cellInfo.cellIdentity.ci}\n")
                            cellInfoStringBuilder.append("Signal Strength: ${cellInfo.cellSignalStrength.dbm} dBm\n")
                            cellInfoStringBuilder.append("Tracking Area Code: ${cellInfo.cellIdentity.tac}\n")
                        }
                        is CellInfoCdma -> {
                            cellInfoStringBuilder.append("Type: CDMA\n")
                            cellInfoStringBuilder.append("Cell ID: ${cellInfo.cellIdentity.basestationId}\n")
                            cellInfoStringBuilder.append("Signal Strength: ${cellInfo.cellSignalStrength.dbm} dBm\n")
                        }
                        is CellInfoWcdma -> {
                            cellInfoStringBuilder.append("Type: WCDMA\n")
                            cellInfoStringBuilder.append("Cell ID: ${cellInfo.cellIdentity.cid}\n")
                            cellInfoStringBuilder.append("Signal Strength: ${cellInfo.cellSignalStrength.dbm} dBm\n")
                            cellInfoStringBuilder.append("Location Area Code: ${cellInfo.cellIdentity.lac}\n")
                        }
                    }
                    cellInfoStringBuilder.append("\n")
                }
                cellInfoTextView.text = "Cell Info:\n$cellInfoStringBuilder"
            } else {
                cellInfoTextView.text = "No cell info available"
            }
        } else {
            requestLocationPermission()
        }
    }

    override fun onPause() {
        super.onPause()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    override fun onResume() {
        super.onResume()
        startLocationUpdates()
    }
}
