package com.btcallbridge.client

import android.app.*
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.btcallbridge.core.Protocol
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintWriter
import kotlin.coroutines.coroutineContext

class ClientService : Service() {

    companion object {
        var instance: ClientService? = null
    }

    private var signalSocket: BluetoothSocket? = null
    private var audioSocket: BluetoothSocket? = null
    private var signalOut: PrintWriter? = null
    private var signalIn: BufferedReader? = null
    private var audioReceiver: AudioReceiver? = null
    private var btClient: BTClient? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        startForeground(1, createNotification("Connecting to Redmi..."))

        val prefs = getSharedPreferences("bt_prefs", Context.MODE_PRIVATE)
        val hostMac = prefs.getString("host_mac", null)

        if (hostMac != null) {
            connectToHost(hostMac)
        } else {
            updateNotification("No host MAC saved")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val mac = intent?.getStringExtra("host_mac")
        if (mac != null) {
            connectToHost(mac)
        }
        return START_STICKY
    }

    private fun connectToHost(mac: String) {
        btClient = BTClient(mac,
            onConnected = { signal, audio ->
                onConnected(signal, audio)
            },
            onFailed = {
                handleDisconnect()
            }
        )
        btClient?.connect()
    }

    private fun onConnected(signal: BluetoothSocket, audio: BluetoothSocket) {
        signalSocket = signal
        audioSocket = audio
        signalOut = PrintWriter(signal.outputStream, true)
        signalIn  = BufferedReader(InputStreamReader(signal.inputStream))
        audioReceiver = AudioReceiver(audio)

        updateNotification("Connected to Redmi")

        scope.launch {
            listenForSignals()
        }
    }

    private suspend fun listenForSignals() {
        try {
            while (coroutineContext.isActive) {
                val line = signalIn?.readLine() ?: break
                val (cmd, params) = Protocol.parse(line)
                Log.d("ClientService", "Received: $cmd")
                when (cmd) {
                    Protocol.INCOMING_CALL -> {
                        val number = params.getOrElse(0) { "Unknown" }
                        val name   = params.getOrElse(1) { number }
                        showIncomingCallUI(number, name)
                    }
                    Protocol.CALL_ENDED     -> dismissCallUI()
                    Protocol.CALL_CONNECTED -> startAudioStream()
                    Protocol.HEARTBEAT      -> {
                        signalOut?.print(Protocol.HEARTBEAT_ACK + Protocol.DELIMITER)
                        signalOut?.flush()
                    }
                }
            }
        } catch (e: IOException) {
            Log.e("ClientService", "Signal read error: ${e.message}")
        } finally {
            handleDisconnect()
        }
    }

    fun sendCommand(cmd: String) {
        scope.launch {
            signalOut?.print(cmd + Protocol.DELIMITER)
            signalOut?.flush()
        }
    }

    private fun showIncomingCallUI(number: String, name: String) {
        val intent = Intent(this, InCallUI::class.java).apply {
            putExtra("number", number)
            putExtra("name", name)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val notification = NotificationCompat.Builder(this, "calls")
            .setContentTitle("Incoming Call")
            .setContentText(name)
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setOngoing(true)
            .build()

        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(2, notification)
        startActivity(intent)
    }

    private fun dismissCallUI() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(2)
        audioReceiver?.stop()
        sendBroadcast(Intent("com.btcallbridge.DISMISS_CALL"))
    }

    private fun startAudioStream() {
        audioReceiver?.start()
    }

    private fun handleDisconnect() {
        Log.w("ClientService", "Disconnected, retrying...")
        audioReceiver?.stop()
        signalSocket?.close()
        audioSocket?.close()
        signalOut = null
        signalIn = null
        updateNotification("Reconnecting...")
        
        scope.launch {
            delay(3000)
            val prefs = getSharedPreferences("bt_prefs", Context.MODE_PRIVATE)
            val hostMac = prefs.getString("host_mac", null)
            if (hostMac != null && scope.isActive) {
                connectToHost(hostMac)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                "btcallbridge", "BTCallBridge Client",
                NotificationManager.IMPORTANCE_LOW
            )
            val callChannel = NotificationChannel(
                "calls", "Incoming Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setSound(null, null)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
            manager.createNotificationChannel(callChannel)
        }
    }

    private fun createNotification(content: String): android.app.Notification {
        return NotificationCompat.Builder(this, "btcallbridge")
            .setContentTitle("BTCallBridge Active")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(content: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(1, createNotification(content))
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        scope.cancel()
        audioReceiver?.stop()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
