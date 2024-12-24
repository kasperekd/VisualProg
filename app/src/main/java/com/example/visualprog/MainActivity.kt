package com.example.visualprog

import kotlinx.coroutines.*
import android.Manifest
import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.visualprog.cellinfo.CellInfoModule
import com.example.visualprog.location.LocationModule
import com.example.visualprog.permissions.PermissionManager
import com.example.visualprog.ui.UIManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var locationModule: LocationModule
    private lateinit var cellInfoModule: CellInfoModule
    private lateinit var permissionManager: PermissionManager
    private lateinit var uiManager: UIManager

    private var currentServerUrl: String? = null
    private var job: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val latitudeTextView: TextView = findViewById(R.id.Latitude)
        val longitudeTextView: TextView = findViewById(R.id.Longitude)
        val cellInfoTextView: TextView = findViewById(R.id.CellInfo)
        val ipEditText: EditText = findViewById(R.id.ip_text)
        val checkButton: Button = findViewById(R.id.button_Check)

        uiManager = UIManager(latitudeTextView, longitudeTextView, cellInfoTextView)

        locationModule = LocationModule(this) { latitude, longitude ->
            uiManager.updateLocation(latitude, longitude)
        }

        permissionManager = PermissionManager(this) {
//            locationModule.startLocationUpdates()
//            cellInfoModule.fetchCellInfo()
//            cellInfoModule.startFetching()

        }

        checkButton.setOnClickListener {
            val ipAddress = ipEditText.text.toString().trim()

            if (isValidIPAddressWithPort(ipAddress)) {
                if (ipAddress != currentServerUrl) {
                    checkServerHealth(ipAddress) { isHealthy ->
                        if (isHealthy) {
                            currentServerUrl = ipAddress

                            cellInfoModule = CellInfoModule(this, "http://$ipAddress", uiManager) { cellInfo ->
                                uiManager.updateCellInfo(cellInfo)
                            }

                            permissionManager.checkAndRequestPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                            Toast.makeText(this, "Сервер доступен, IP принят", Toast.LENGTH_SHORT).show()

                            startProcesses()
                        } else {
                            Toast.makeText(this, "Сервер недоступен. Проверьте адрес.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(this, "Сервер уже настроен", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Введите корректный адрес с портом (например, 192.168.0.3:9000)", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startProcesses() {
        job?.cancel()
        cellInfoModule.startFetching()
        job = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    locationModule.startLocationUpdates()

                    delay(500)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            cellInfoModule.stopFetching()
        }
    }
    override fun onPause() {
        super.onPause()
        job?.cancel()
    }

    override fun onResume() {
        super.onResume()
        if (currentServerUrl != null) {
            startProcesses()
        }
    }

    private fun isValidIPAddressWithPort(address: String): Boolean {
        val regex = Regex("^([0-9]{1,3}\\.){3}[0-9]{1,3}:[0-9]{1,5}$")
        if (!regex.matches(address)) return false

        val ip = address.substringBefore(":")
        val port = address.substringAfter(":").toIntOrNull()

        return Patterns.IP_ADDRESS.matcher(ip).matches() && port != null && port in 1..65535
    }

    private fun checkServerHealth(address: String, callback: (Boolean) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val isPingSuccessful = isPingSuccessful("google.com")
                if (!isPingSuccessful) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@MainActivity,
                            "Нет доступа к интернету. Проверьте подключение.",
                            Toast.LENGTH_SHORT
                        ).show()
                        callback(false)
                    }
                    return@launch
                }

                val url = URL("http://$address/api/health")
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.requestMethod = "GET"

                val responseCode = connection.responseCode
                withContext(Dispatchers.Main) {
                    if (responseCode == 200) {
                        callback(true)
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            "Ошибка сервера: $responseCode ${connection.responseMessage}",
                            Toast.LENGTH_LONG
                        ).show()
                        callback(false)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Ошибка подключения: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    callback(false)
                }
            }
        }
    }

    private fun isPingSuccessful(host: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("ping -c 1 $host")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            reader.close()
            process.waitFor()
            process.exitValue() == 0
        } catch (e: Exception) {
            false
        }
    }
}