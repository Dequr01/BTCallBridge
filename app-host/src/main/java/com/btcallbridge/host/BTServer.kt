package com.btcallbridge.host

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.util.Log
import com.btcallbridge.core.BTConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException

@SuppressLint("MissingPermission")
class BTServer(private val onClientConnected: (signal: BluetoothSocket, audio: BluetoothSocket) -> Unit) {

    private val adapter = BluetoothAdapter.getDefaultAdapter()
    private var signalServerSocket: BluetoothServerSocket? = null
    private var audioServerSocket: BluetoothServerSocket? = null
    private var isListening = false

    fun startListening() {
        if (isListening) return
        val btAdapter = adapter ?: return
        isListening = true
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Open two RFCOMM server sockets — one for signals, one for audio
                signalServerSocket = btAdapter.listenUsingRfcommWithServiceRecord(
                    "BTCallBridge-Signal", BTConstants.SIGNAL_UUID)

                audioServerSocket = btAdapter.listenUsingRfcommWithServiceRecord(
                    "BTCallBridge-Audio", BTConstants.AUDIO_UUID)

                Log.d("BTServer", "Listening for connections...")

                // Block and wait for client to connect on both channels
                // In a real app, you might want to accept multiple clients or handle timeouts
                val signalSocket = signalServerSocket!!.accept()
                Log.d("BTServer", "Signal channel connected")
                
                val audioSocket  = audioServerSocket!!.accept()
                Log.d("BTServer", "Audio channel connected")

                onClientConnected(signalSocket, audioSocket)

            } catch (e: IOException) {
                Log.e("BTServer", "Accept failed: ${e.message}")
            } finally {
                isListening = false
            }
        }
    }

    fun stop() {
        try {
            signalServerSocket?.close()
            audioServerSocket?.close()
        } catch (e: IOException) {
            Log.e("BTServer", "Could not close server sockets", e)
        }
        isListening = false
    }
}
