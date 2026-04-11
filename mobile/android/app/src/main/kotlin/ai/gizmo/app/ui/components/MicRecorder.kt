package ai.gizmo.app.ui.components

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class MicRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun start(): Boolean {
        return try {
            val file = File.createTempFile("recording", ".m4a", context.cacheDir)
            outputFile = file

            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mr.setAudioSamplingRate(44100)
            mr.setOutputFile(file.absolutePath)
            mr.prepare()
            mr.start()
            recorder = mr
            true
        } catch (e: Exception) {
            cleanup()
            false
        }
    }

    fun stop(): File? {
        return try {
            recorder?.stop()
            recorder?.release()
            recorder = null
            outputFile
        } catch (_: Exception) {
            cleanup()
            null
        }
    }

    fun cancel() {
        cleanup()
    }

    private fun cleanup() {
        try {
            recorder?.stop()
        } catch (_: Exception) { }
        try {
            recorder?.release()
        } catch (_: Exception) { }
        recorder = null
        outputFile?.delete()
        outputFile = null
    }
}
