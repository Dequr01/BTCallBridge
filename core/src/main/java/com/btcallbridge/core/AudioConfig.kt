package com.btcallbridge.core

import android.media.AudioFormat

object AudioConfig {
    const val SAMPLE_RATE    = 8000        // 8kHz — narrowband, good enough for voice, low BT load
    const val CHANNEL_IN     = AudioFormat.CHANNEL_IN_MONO
    const val CHANNEL_OUT    = AudioFormat.CHANNEL_OUT_MONO
    const val ENCODING       = AudioFormat.ENCODING_PCM_16BIT
    const val BUFFER_SIZE    = 640         // 40ms frames at 8kHz — balance of latency vs stability
}
