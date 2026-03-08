package com.btcallbridge.host

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log

class CallListener(private val onIncoming: (number: String) -> Unit,
                   private val onEnded: () -> Unit) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val state  = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: "Unknown"

        Log.d("CallListener", "State: $state, Number: $number")

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                onIncoming(number)
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                // Call answered or outgoing call
            }
            TelephonyManager.EXTRA_STATE_IDLE    -> {
                onEnded()
            }
        }
    }
}
