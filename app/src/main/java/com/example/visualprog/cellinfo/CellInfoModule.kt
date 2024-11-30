package com.example.visualprog.cellinfo

import android.annotation.SuppressLint
import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.telephony.*
import android.widget.Toast
import androidx.core.app.ActivityCompat
//import kotlinx.coroutines.DefaultExecutor.isActive
import kotlinx.coroutines.NonCancellable.isActive
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class CellInfoModule(
    private val context: Context,
    private val serverUrl: String,
    private val onCellInfoFetched: (String) -> Unit
) {

    private val telephonyManager: TelephonyManager =
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private var fetchJob: Job? = null
    private val fetchInterval = 1000L // Интервал 1 секунда

    // Стартуем периодический запрос данных
    fun startFetching() {
        fetchJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                fetchCellInfo()
                delay(fetchInterval) // Задержка 1 секунда между запросами
            }
        }
    }

    // Останавливаем периодический запрос данных
    fun stopFetching() {
        fetchJob?.cancel()
    }

    @SuppressLint("MissingPermission")
    fun fetchCellInfo() {
        if (ActivityCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val cellInfoList = telephonyManager.allCellInfo
            val cellInfoJsonArray = JSONArray()
            val cellInfoStringBuilder = StringBuilder()

            if (cellInfoList != null) {
                for (cellInfo in cellInfoList) {
                    val cellInfoJson = JSONObject()
                    when (cellInfo) {
                        is CellInfoGsm -> {
                            cellInfoJson.put("type", "GSM")
                            cellInfoJson.put("cellId", cellInfo.cellIdentity.cid)
                            cellInfoJson.put("signalStrength", cellInfo.cellSignalStrength.dbm)
                            cellInfoJson.put("locationAreaCode", cellInfo.cellIdentity.lac)
                            cellInfoJson.put("operator", cellInfo.cellIdentity.mobileNetworkOperator)
                            cellInfoStringBuilder.append("Type: GSM\n")
                                .append("Cell ID: ${cellInfo.cellIdentity.cid}\n")
                                .append("Signal Strength: ${cellInfo.cellSignalStrength.dbm} dBm\n")
                                .append("Location Area Code: ${cellInfo.cellIdentity.lac}\n")
                                .append("Operator: ${cellInfo.cellIdentity.mobileNetworkOperator}\n\n")
                        }

                        is CellInfoLte -> {
                            cellInfoJson.put("type", "LTE")
                            cellInfoJson.put("cellId", cellInfo.cellIdentity.ci)
                            cellInfoJson.put("signalStrength", cellInfo.cellSignalStrength.dbm)
                            cellInfoJson.put("trackingAreaCode", cellInfo.cellIdentity.tac)
                            cellInfoJson.put("operator", cellInfo.cellIdentity.mobileNetworkOperator)
                            cellInfoStringBuilder.append("Type: LTE\n")
                                .append("Cell ID: ${cellInfo.cellIdentity.ci}\n")
                                .append("Signal Strength: ${cellInfo.cellSignalStrength.dbm} dBm\n")
                                .append("Tracking Area Code: ${cellInfo.cellIdentity.tac}\n")
                                .append("Operator: ${cellInfo.cellIdentity.mobileNetworkOperator}\n\n")
                        }

                        is CellInfoWcdma -> {
                            cellInfoJson.put("type", "WCDMA")
                            cellInfoJson.put("cellId", cellInfo.cellIdentity.cid)
                            cellInfoJson.put("signalStrength", cellInfo.cellSignalStrength.dbm)
                            cellInfoJson.put("locationAreaCode", cellInfo.cellIdentity.lac)
                            cellInfoJson.put("operator", cellInfo.cellIdentity.mobileNetworkOperator)
                            cellInfoStringBuilder.append("Type: WCDMA\n")
                                .append("Cell ID: ${cellInfo.cellIdentity.cid}\n")
                                .append("Signal Strength: ${cellInfo.cellSignalStrength.dbm} dBm\n")
                                .append("Location Area Code: ${cellInfo.cellIdentity.lac}\n")
                                .append("Operator: ${cellInfo.cellIdentity.mobileNetworkOperator}\n\n")
                        }
                    }
                    cellInfoJsonArray.put(cellInfoJson)
                }

                val cellInfoString = cellInfoStringBuilder.toString()
                onCellInfoFetched(cellInfoString) // Обновляем UI
                sendToServer(cellInfoJsonArray) // Отправляем на сервер
            } else {
                val errorJson = JSONArray().put(JSONObject().put("error", "No cell info available"))
                onCellInfoFetched("No cell info available") // Обновляем UI
                sendToServer(errorJson) // Отправляем ошибку на сервер
            }
        }
    }

    private fun sendToServer(data: JSONArray) {
        thread {
            var connection: HttpURLConnection? = null
            var attempt = 0
            val maxAttempts = 5  // Максимальное количество попыток
            val delayBetweenAttempts = 2000L  // Задержка между попытками в миллисекундах (2 секунды)

            // 1. Пробуем сразу отправить данные на /api/cellinfo
            while (attempt < maxAttempts) {
                try {
                    // Попытка отправить данные на эндпоинт /api/cellinfo
                    val url = URL("$serverUrl/api/cellinfo")
                    connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "POST"
                    connection.setRequestProperty(
                        "Content-Type",
                        "application/json; charset=utf-8"
                    )
                    connection.doOutput = true

                    val outputStream: OutputStream = connection.outputStream
                    outputStream.use { it.write(data.toString().toByteArray()) }

                    val responseCode = connection.responseCode
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        // Успешная отправка данных
                        println("Data sent successfully")
                        return@thread
                    } else {
                        // Ошибка при отправке данных
                        println("Server returned response code: $responseCode")
                    }
                } catch (e: Exception) {
                    // Ошибка при попытке отправки данных
                    e.printStackTrace()
                    println("Error during data sending: ${e.message}")
                }

                // Если отправка данных не удалась, пробуем проверку через /api/health
                attempt++
                if (attempt < maxAttempts) {
                    println("Attempt $attempt failed. Retrying in $delayBetweenAttempts ms...")
                    Thread.sleep(delayBetweenAttempts)
                }
            }

            // Если отправка данных не удалась, начинаем проверку через /api/health
            var healthCheckAttempt = 0
            val healthCheckMaxAttempts = 12  // 12 попыток (по одной в 5 секунд) = 1 минута
            var serverAvailable = false

            while (healthCheckAttempt < healthCheckMaxAttempts && !serverAvailable) {
                try {
                    // Проверка соединения с сервером на эндпоинт /api/health
                    val healthCheckUrl = URL("$serverUrl/api/health")
                    val healthConnection = healthCheckUrl.openConnection() as HttpURLConnection
                    healthConnection.requestMethod = "GET"
                    healthConnection.connectTimeout = 5000 // 5 секунд на подключение
                    healthConnection.readTimeout = 5000 // 5 секунд на получение данных

                    val healthResponseCode = healthConnection.responseCode
                    if (healthResponseCode == HttpURLConnection.HTTP_OK) {
                        // Сервер доступен, повторяем отправку данных
                        println("Server is healthy, retrying data send.")
                        sendDataAgain(data)  // Попытка повторной отправки данных
                        serverAvailable = true
                    } else {
                        println("Health check failed. Server returned response code: $healthResponseCode")
                    }
                } catch (e: Exception) {
                    println("Error during health check: ${e.message}")
                }

                // Задержка между попытками проверки /api/health (5 секунд)
                healthCheckAttempt++
                if (healthCheckAttempt < healthCheckMaxAttempts) {
                    Thread.sleep(5000)  // Задержка между проверками /api/health
                }
            }

            // Если сервер не доступен после всех попыток
            if (!serverAvailable) {
                Toast.makeText(context, "Server is unavailable. Please try again later.", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Метод для повторной отправки данных
    private fun sendDataAgain(data: JSONArray) {
        thread {
            var connection: HttpURLConnection? = null
            try {
                // Повторная попытка отправки данных на /api/cellinfo
                val url = URL("$serverUrl/api/cellinfo")
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty(
                    "Content-Type",
                    "application/json; charset=utf-8"
                )
                connection.doOutput = true

                val outputStream: OutputStream = connection.outputStream
                outputStream.use { it.write(data.toString().toByteArray()) }

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    println("Data sent successfully after server recovery.")
                } else {
                    println("Server returned response code: $responseCode")
                }
            } catch (e: Exception) {
                println("Error during data sending after server recovery: ${e.message}")
            }
        }
    }

}