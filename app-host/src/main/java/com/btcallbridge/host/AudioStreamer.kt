package com.btcallbridge.host

import android.annotation.SuppressLint
import android.bluetooth.BluetoothSocket
import android.media.*
import android.util.Log
import com.btcallbridge.core.AudioConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException

class AudioStreamer(private val audioSocket: BluetoothSocket) {

    private var isRunning = false

    @SuppressLint("MissingPermission")
    fun start() {
        if (isRunning) return
        isRunning = true
        val bufferSize = AudioConfig.BUFFER_SIZE

        // SEND: Redmi mic → Xperia speaker
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val recorder = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    AudioConfig.SAMPLE_RATE,
                    AudioConfig.CHANNEL_IN,
                    AudioConfig.ENCODING,
                    bufferSize
                )
                recorder.startRecording()
                val buffer = ByteArray(bufferSize)
                val out = audioSocket.outputStream

                Log.d("AudioStreamer", "Recording started")
                while (isRunning) {
                    val read = recorder.read(buffer, 0, bufferSize)
                    if (read > 0) {
                        out.write(buffer, 0, read)
                    }
                }
                recorder.stop()
                recorder.release()
                Log.d("AudioStreamer", "Recording stopped")
            } catch (e: Exception) {
                Log.e("AudioStreamer", "Record error: ${e.message}")
                stop()
            }
        }

        // RECEIVE: Xperia mic → Redmi speaker
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val player = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build())
                    .setAudioFormat(AudioFormat.Builder()
                        .setSampleRate(AudioConfig.SAMPLE_RATE)
                        .setEncoding(AudioConfig.ENCODING)
                        .setChannelMask(AudioConfig.CHANNEL_OUT)
                        .build())
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                player.play()
                val buffer = ByteArray(bufferSize)
                val inp = audioSocket.inputStream

                Log.d("AudioStreamer", "Playback started")
                while (isRunning) {
                    val read = inp.read(buffer)
                    if (read > 0) {
                        player.write(buffer, 0, read)
                    } else if (read == -1) {
                        break
                    }
                }
                player.stop()
                player.release()
                Log.d("AudioStreamer", "Playback stopped")
            } catch (e: Exception) {
                Log.e("AudioStreamer", "Playback error: ${e.message}")
                stop()
            }
        }
    }

    fun stop() {
        isRunning = false
    }
}
