package com.btcallbridge.host

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.btcallbridge.core.Protocol
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintWriter
import kotlin.coroutines.coroutineContext

class HostService : Service() {

    private var signalSocket: BluetoothSocket? = null
    private var audioSocket: BluetoothSocket? = null
    private var signalOut: PrintWriter? = null
    private var signalIn: BufferedReader? = null
    private var audioStreamer: AudioStreamer? = null
    private var btServer: BTServer? = null
    private var callListener: CallListener? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, createNotification("Starting server..."), 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, createNotification("Starting server..."), 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(1, createNotification("Starting server..."))
        }
        
        btServer = BTServer { signal, audio ->
            onClientConnected(signal, audio)
        }
        btServer?.startListening()

        callListener = CallListener(
            onIncoming = { number -> notifyIncomingCall(number) },
            onEnded = { notifyCallEnded() }
        )
        val filter = IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
        ContextCompat.registerReceiver(this, callListener, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun onClientConnected(signal: BluetoothSocket, audio: BluetoothSocket) {
        signalSocket = signal
        audioSocket = audio
        signalOut = PrintWriter(signal.outputStream, true)
        signalIn  = BufferedReader(InputStreamReader(signal.inputStream))
        audioStreamer = AudioStreamer(audio)

        updateNotification("Client connected")

        // Start listening for commands from Client
        scope.launch {
            listenForCommands()
        }

        // Start heartbeat
        scope.launch {
            while (scope.isActive) {
                delay(5000)
                try {
                    signalOut?.print(Protocol.HEARTBEAT + Protocol.DELIMITER)
                    signalOut?.flush()
                    if (signalOut?.checkError() == true) {
                        Log.e("HostService", "Signal socket write error")
                        handleDisconnect()
                        break
                    }
                } catch (e: Exception) {
                    Log.e("HostService", "Heartbeat error: ${e.message}")
                    handleDisconnect()
                    break
                }
            }
        }
    }

    private suspend fun listenForCommands() {
        try {
            while (coroutineContext.isActive) {
                val line = signalIn?.readLine() ?: break
                val (cmd, _) = Protocol.parse(line)
                Log.d("HostService", "Received: $cmd")
                when (cmd) {
                    Protocol.ANSWER   -> answerCall()
                    Protocol.REJECT   -> rejectCall()
                    Protocol.HEARTBEAT_ACK -> { /* alive */ }
                }
            }
        } catch (e: IOException) {
            Log.e("HostService", "Disconnected: ${e.message}")
        } finally {
            handleDisconnect()
        }
    }

    fun notifyIncomingCall(number: String) {
        val name = number // Lookup contact name logic could go here
        Log.d("HostService", "Notifying incoming call: $number")
        signalOut?.print(Protocol.incoming(number, name))
        signalOut?.flush()
    }

    fun notifyCallEnded() {
        Log.d("HostService", "Notifying call ended")
        signalOut?.print(Protocol.CALL_ENDED + Protocol.DELIMITER)
        signalOut?.flush()
        audioStreamer?.stop()
    }

    @SuppressLint("MissingPermission")
    private fun answerCall() {
        Log.d("HostService", "Answering call...")
        val telecom = getSystemService(TELECOM_SERVICE) as TelecomManager
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                telecom.acceptRingingCall()
                // Wait for offhook state to start audio
                // In some cases we might send CONNECTED signal here
                signalOut?.print(Protocol.CALL_CONNECTED + Protocol.DELIMITER)
                signalOut?.flush()
                audioStreamer?.start()
            }
        } catch (e: Exception) {
            Log.e("HostService", "Could not answer call", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun rejectCall() {
        Log.d("HostService", "Rejecting call...")
        val telecom = getSystemService(TELECOM_SERVICE) as TelecomManager
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                telecom.endCall()
            } else {
                // Older methods would require reflection
            }
        } catch (e: Exception) {
            Log.e("HostService", "Could not end call", e)
        }
    }

    private fun handleDisconnect() {
        Log.w("HostService", "Client disconnected, restarting server...")
        audioStreamer?.stop()
        signalSocket?.close()
        audioSocket?.close()
        signalOut = null
        signalIn = null
        updateNotification("Listening — Client disconnected")
        btServer?.startListening()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                "btcallbridge", "BTCallBridge Host",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(content: String): android.app.Notification {
        return NotificationCompat.Builder(this, "btcallbridge")
            .setContentTitle("BTCallBridge Active")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(content: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(1, createNotification(content))
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        btServer?.stop()
        audioStreamer?.stop()
        unregisterReceiver(callListener)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
