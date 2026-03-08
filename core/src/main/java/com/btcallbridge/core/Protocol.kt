package com.btcallbridge.core

object Protocol {
    // Host → Client
    const val INCOMING_CALL  = "INCOMING"   // INCOMING|+923001234567|John Doe
    const val CALL_ENDED     = "ENDED"      // ENDED
    const val CALL_CONNECTED = "CONNECTED"  // CONNECTED  (other side picked up)

    // Client → Host
    const val ANSWER         = "ANSWER"     // ANSWER
    const val REJECT         = "REJECT"     // REJECT

    // Bidirectional
    const val HEARTBEAT      = "PING"       // PING  (sent every 5s to keep connection alive)
    const val HEARTBEAT_ACK  = "PONG"       // PONG

    const val DELIMITER = "\n"

    fun parse(raw: String): Pair<String, List<String>> {
        val parts = raw.trim().split("|")
        return parts[0] to parts.drop(1)
    }

    fun incoming(number: String, name: String) = "INCOMING|$number|$name\n"
}
