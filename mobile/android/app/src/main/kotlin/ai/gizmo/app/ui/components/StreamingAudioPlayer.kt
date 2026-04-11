package ai.gizmo.app.ui.components

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.nio.ByteBuffer
import java.nio.ByteOrder

class StreamingAudioPlayer {

    private var audioTrack: AudioTrack? = null
    private var sampleRate = 24000

    fun start(sampleRate: Int = 24000) {
        this.sampleRate = sampleRate
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        ) * 2

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(bufferSize)
            .build()

        audioTrack?.play()
    }

    fun writeChunk(pcmBytes: ByteArray) {
        val track = audioTrack ?: return
        // Convert byte array to float array (PCM float32, little-endian)
        val floatCount = pcmBytes.size / 4
        val floatArray = FloatArray(floatCount)
        val buffer = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until floatCount) {
            floatArray[i] = buffer.getFloat()
        }
        track.write(floatArray, 0, floatArray.size, AudioTrack.WRITE_NON_BLOCKING)
    }

    fun stop() {
        try {
            audioTrack?.stop()
        } catch (_: Exception) { }
    }

    fun release() {
        stop()
        audioTrack?.release()
        audioTrack = null
    }
}
