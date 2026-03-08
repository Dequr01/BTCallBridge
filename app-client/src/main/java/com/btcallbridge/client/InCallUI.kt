package com.btcallbridge.client

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.btcallbridge.core.Protocol

class InCallUI : AppCompatActivity() {

    private val dismissReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        
        setContentView(R.layout.activity_incall)

        val name   = intent.getStringExtra("name") ?: "Unknown"
        val number = intent.getStringExtra("number") ?: ""

        findViewById<TextView>(R.id.callerName).text = name
        findViewById<TextView>(R.id.callerNumber).text = number

        findViewById<Button>(R.id.btnAnswer).setOnClickListener {
            ClientService.instance?.sendCommand(Protocol.ANSWER)
            // Call is answered on host side, audio will start via CONNECTED signal
            // But for better UX we might want to change UI state here
            findViewById<Button>(R.id.btnAnswer).isEnabled = false
            findViewById<TextView>(R.id.callerName).text = "Active Call: $name"
        }

        findViewById<Button>(R.id.btnReject).setOnClickListener {
            ClientService.instance?.sendCommand(Protocol.REJECT)
            finish()
        }

        registerReceiver(dismissReceiver, IntentFilter("com.btcallbridge.DISMISS_CALL"))
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(dismissReceiver)
        } catch (e: Exception) {}
    }
}
