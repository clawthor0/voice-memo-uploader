package com.example.voicememouploader

import android.content.Context
import android.media.MediaRecorder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecorderHelper(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var currentPath: String? = null

    fun startRecording(name: String): Result<String> {
        return try {
            val recordingsDir = File(context.filesDir, "recordings").apply { mkdirs() }
            val cleanName = sanitizeName(name)
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(recordingsDir, "${cleanName}_$stamp.m4a")

            val r = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }

            recorder = r
            currentPath = file.absolutePath
            Result.success(file.absolutePath)
        } catch (e: Exception) {
            stopRecordingSilently()
            Result.failure(e)
        }
    }

    fun stopRecording(): Result<String> {
        return try {
            recorder?.apply {
                stop()
                reset()
                release()
            }
            recorder = null
            val path = currentPath ?: return Result.failure(IllegalStateException("No recording path"))
            currentPath = null
            Result.success(path)
        } catch (e: Exception) {
            stopRecordingSilently()
            Result.failure(e)
        }
    }

    private fun stopRecordingSilently() {
        try { recorder?.stop() } catch (_: Exception) {}
        try { recorder?.reset() } catch (_: Exception) {}
        try { recorder?.release() } catch (_: Exception) {}
        recorder = null
    }

    private fun sanitizeName(name: String): String {
        val n = name.trim().ifBlank { "voice_memo" }
        return n.replace(" ", "_")
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .replace(Regex("_+"), "_")
    }
}
