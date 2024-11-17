package com.example.visualprog.cellinfo

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.telephony.*
import android.widget.Toast
import androidx.core.app.ActivityCompat
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
    private val handler = Handler(Looper.getMainLooper())
    private val fetchInterval = 1 * 1000L // 1 секунда

    fun startFetching() {
        handler.post(fetchRunnable)
    }

    fun stopFetching() {
        handler.removeCallbacks(fetchRunnable)
    }

    private val fetchRunnable = object : Runnable {
        override fun run() {
            fetchCellInfo()
            handler.postDelayed(this, fetchInterval)
        }
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
                            cellInfoJson.put(
                                "operator",
                                cellInfo.cellIdentity.mobileNetworkOperator
                            )
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
                            cellInfoJson.put(
                                "operator",
                                cellInfo.cellIdentity.mobileNetworkOperator
                            )
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
                            cellInfoJson.put(
                                "operator",
                                cellInfo.cellIdentity.mobileNetworkOperator
                            )
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

            while (attempt < maxAttempts) {
                try {
                    // Проверка соединения с сервером на эндпоинт /api/health
                    val healthCheckUrl = URL("$serverUrl/api/health")
                    val healthConnection = healthCheckUrl.openConnection() as HttpURLConnection
                    healthConnection.requestMethod = "GET"
                    healthConnection.connectTimeout = 5000 // 5 секунд на подключение
                    healthConnection.readTimeout = 5000 // 5 секунд на получение данных

                    val healthResponseCode = healthConnection.responseCode
                    if (healthResponseCode == HttpURLConnection.HTTP_OK) {
                        // Сервер доступен, продолжаем отправку данных
                        connection = URL(serverUrl).openConnection() as HttpURLConnection
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
                            return@thread  // Выход из потока
                        } else {
                            println("Server returned response code: $responseCode")
                        }
                    } else {
                        println("Health check failed. Server returned response code: $healthResponseCode")
                    }

                    // Сервер не доступен или ошибка, пытаемся снова
                    attempt++
                    if (attempt < maxAttempts) {
                        println("Attempt $attempt failed. Retrying in $delayBetweenAttempts ms...")
                        Thread.sleep(delayBetweenAttempts)  // Задержка между попытками
                    }
                } catch (e: Exception) {
                    // Ошибка при попытке соединения с сервером
                    e.printStackTrace()
                    println("Connection error: ${e.message}")
                    attempt++
                    if (attempt < maxAttempts) {
                        println("Attempt $attempt failed. Retrying in $delayBetweenAttempts ms...")
                        Thread.sleep(delayBetweenAttempts)  // Задержка между попытками
                    }
                } finally {
                    // НЕ вызываем disconnect, чтобы продолжить попытки
                    // connection?.disconnect()  // Убираем этот вызов
                }
            }

            // Если все попытки не удались, показываем уведомление
            if (attempt >= maxAttempts) {
                showServerUnavailableToast()  // Показываем уведомление
            }
        }
    }

    // Функция для отображения уведомления Toast
    private fun showServerUnavailableToast() {
        handler.post {
            // Показываем всплывающее уведомление (Toast) в главном потоке
            Toast.makeText(context, "Server is unavailable. Please try again later.", Toast.LENGTH_LONG).show()
        }
    }


}