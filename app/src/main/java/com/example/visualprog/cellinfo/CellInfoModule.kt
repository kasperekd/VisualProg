package com.example.visualprog.cellinfo;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.telephony.*;
import android.util.Log
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import com.example.visualprog.location.LocationModule;
import com.example.visualprog.ui.UIManager
import kotlinx.coroutines.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedList;
import java.util.Queue;
import kotlin.concurrent.thread;

class CellInfoModule(
    private val context: Context,
    private val serverUrl: String,
    private val uiManager: UIManager,
    private val onCellInfoFetched: (String) -> Unit
) {

    private val telephonyManager: TelephonyManager =
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private var fetchJob: Job? = null
    private val fetchInterval = 500L

    private val buffer: Queue<JSONObject> = LinkedList()
    private val bufferSize = 10 // Размер буфера
    private var isSending = false

    fun startFetching() {
        Log.d("CellInfoModule", "startFetching")
        val locationModule = LocationModule(context) { latitude, longitude ->
            fetchCellInfo(latitude, longitude) { cellInfoJson ->
                uiManager.updateCellInfo(cellInfoJson.toString())
                uiManager.updateLocation(latitude, longitude)
            }
        }

        locationModule.startLocationUpdates()

        fetchJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                locationModule.getCurrentLocation { latitude, longitude ->
                    fetchCellInfo(latitude, longitude) { cellInfoJson ->
                        uiManager.updateCellInfo(cellInfoJson.toString())
                        uiManager.updateLocation(latitude, longitude)

                        synchronized(buffer) {
                            buffer.add(cellInfoJson)
                            if (buffer.size >= bufferSize && !isSending) {
                                sendBufferedData()
                            }
                        }
                    }
                }
                delay(fetchInterval)
            }
        }
    }

    fun stopFetching() {
        fetchJob?.cancel()
    }

    @SuppressLint("MissingPermission")
    fun fetchCellInfo(latitude: Double, longitude: Double, onCellInfoReady: (JSONObject) -> Unit) {
        if (ActivityCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val cellInfoList = telephonyManager.allCellInfo
//            Log.d("CellInfoModule", "fetchCellInfo")
            if (cellInfoList != null) {
                for (cellInfo in cellInfoList) {
                    val cellInfoJson = JSONObject()
                    when (cellInfo) {
                        is CellInfoGsm -> populateCellInfoJson(cellInfoJson, cellInfo, "GSM", latitude, longitude)
                        is CellInfoLte -> populateCellInfoJson(cellInfoJson, cellInfo, "LTE", latitude, longitude)
                        is CellInfoWcdma -> populateCellInfoJson(cellInfoJson, cellInfo, "WCDMA", latitude, longitude)
                    }

                    if (cellInfoJson.optInt("cellId") != 268435455 && cellInfoJson.optString("operator") != "Unknown") {
                        onCellInfoReady(cellInfoJson)
                    } else {
//                        Log.d("CellInfoModule", "Skipped invalid cell info: $cellInfoJson")
                    }
                }
            }
        }
    }

    private fun populateCellInfoJson(cellInfoJson: JSONObject, cellInfo: CellInfo, type: String, latitude: Double, longitude: Double) {
        cellInfoJson.put("type", type)
        cellInfoJson.put("coordinates", "$latitude, $longitude")
        cellInfoJson.put("timestamp", System.currentTimeMillis()) // Временная метка

        when (cellInfo) {
            is CellInfoGsm -> {
                cellInfoJson.put("cellId", cellInfo.cellIdentity.cid)
                cellInfoJson.put("signalStrength", cellInfo.cellSignalStrength.dbm)
                cellInfoJson.put("locationAreaCode", cellInfo.cellIdentity.lac)
                cellInfoJson.put("operator", cellInfo.cellIdentity.mobileNetworkOperator ?: "Unknown")
                cellInfoJson.put("RSRP", null)
                cellInfoJson.put("RSRQ", null)
                cellInfoJson.put("SINR", null)
            }
            is CellInfoLte -> {
                cellInfoJson.put("cellId", cellInfo.cellIdentity.ci)
                cellInfoJson.put("signalStrength", cellInfo.cellSignalStrength.dbm)
                cellInfoJson.put("trackingAreaCode", cellInfo.cellIdentity.tac)
                cellInfoJson.put("operator", cellInfo.cellIdentity.mobileNetworkOperator ?: "Unknown")
                cellInfoJson.put("RSRP", cellInfo.cellSignalStrength.rsrp)
                cellInfoJson.put("RSRQ", cellInfo.cellSignalStrength.rsrq)
            }
            is CellInfoWcdma -> {
                cellInfoJson.put("cellId", cellInfo.cellIdentity.cid)
                cellInfoJson.put("signalStrength", cellInfo.cellSignalStrength.dbm)
                cellInfoJson.put("locationAreaCode", cellInfo.cellIdentity.lac)
                cellInfoJson.put("operator", cellInfo.cellIdentity.mobileNetworkOperator ?: "Unknown")
                cellInfoJson.put("RSRP", null)
                cellInfoJson.put("RSRQ", null)
                cellInfoJson.put("SINR", null)
            }
        }
        uiManager.updateCellInfo(cellInfoJson.toString())
        uiManager.updateLocation(latitude, longitude)
    }

    private fun sendBufferedData() {
        isSending = true
        val dataToSend: JSONArray
        synchronized(buffer) {
            dataToSend = JSONArray()
            while (buffer.isNotEmpty() && dataToSend.length() < bufferSize) {
                dataToSend.put(buffer.poll())
            }
        }

        CoroutineScope(Dispatchers.IO).launch {
            var connection: HttpURLConnection? = null
            try {
                val url = URL("$serverUrl/api/cellinfo")
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.doOutput = true

                val outputStream: OutputStream = connection.outputStream
                outputStream.use { it.write(dataToSend.toString().toByteArray()) }

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    Log.d("CellInfoModule", "Data sent successfully")
                } else {
                    Log.e("CellInfoModule", "Server returned response code: $responseCode")
                }
            } catch (e: Exception) {
                Log.e("CellInfoModule", "Error during data sending: ${e.message}", e)
            } finally {
                isSending = false
                synchronized(buffer) {
                    if (buffer.size >= bufferSize && !isSending) {
                        sendBufferedData()
                    }
                }
            }
        }
    }
}

