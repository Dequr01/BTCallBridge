package com.btcallbridge.client

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.util.Log
import com.btcallbridge.core.BTConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class BTClient(
    private val targetDeviceAddress: String,
    private val onConnected: (signal: BluetoothSocket, audio: BluetoothSocket) -> Unit,
    private val onFailed: () -> Unit
) {

    private val adapter = BluetoothAdapter.getDefaultAdapter()

    @SuppressLint("MissingPermission")
    fun connect() {
        val btAdapter = adapter ?: run {
            onFailed()
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val device = btAdapter.getRemoteDevice(targetDeviceAddress)

                // Connect signal channel
                val signalSocket = device.createRfcommSocketToServiceRecord(BTConstants.SIGNAL_UUID)
                signalSocket.connect()
                Log.d("BTClient", "Signal channel connected")

                // Connect audio channel
                val audioSocket = device.createRfcommSocketToServiceRecord(BTConstants.AUDIO_UUID)
                audioSocket.connect()
                Log.d("BTClient", "Audio channel connected")

                withContext(Dispatchers.Main) {
                    onConnected(signalSocket, audioSocket)
                }
            } catch (e: IOException) {
                Log.e("BTClient", "Connect failed: ${e.message}")
                withContext(Dispatchers.Main) { onFailed() }
            }
        }
    }
}
