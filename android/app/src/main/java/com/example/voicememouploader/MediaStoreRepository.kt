package com.example.voicememouploader

import android.content.Context
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.*

class MediaStoreRepository(private val context: Context) {

    data class ScanOptions(
        val recordingsOnly: Boolean = true,
        val topLevelFolder: String? = null,
        val minDurationMs: Long = 10_000,
        val minSizeBytes: Long = 50 * 1024
    )

    fun getVoiceMemos(options: ScanOptions = ScanOptions()): List<VoiceMemo> {
        val memos = mutableListOf<VoiceMemo>()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} = 0"
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        val cursor = context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            sortOrder
        )

        cursor?.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val pathColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val durationColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dateColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val mimeColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)

            while (it.moveToNext()) {
                val path = it.getString(pathColumn) ?: continue
                val duration = it.getLong(durationColumn)
                val size = it.getLong(sizeColumn)
                val mime = (it.getString(mimeColumn) ?: "").lowercase(Locale.getDefault())
                val name = (it.getString(nameColumn) ?: "").lowercase(Locale.getDefault())

                if (!isLikelyVoiceMemo(path, name, mime, options)) continue
                if (duration < options.minDurationMs || size < options.minSizeBytes) continue

                memos.add(
                    VoiceMemo(
                        id = it.getLong(idColumn),
                        title = it.getString(nameColumn),
                        path = path,
                        duration = duration,
                        dateAdded = it.getLong(dateColumn),
                        size = size
                    )
                )
            }
        }

        return memos
    }

    private fun isLikelyVoiceMemo(
        path: String,
        name: String,
        mime: String,
        options: ScanOptions
    ): Boolean {
        val normalizedPath = path.lowercase(Locale.getDefault())

        // Restrict scan to a chosen top-level folder when selected.
        options.topLevelFolder?.let { folder ->
            if (!normalizedPath.contains("/$folder/".lowercase(Locale.getDefault()))) {
                return false
            }
        }

        val excludedTokens = listOf(
            "notifications",
            "ringtones",
            "alarms",
            "ui",
            "slack",
            "discord",
            "telegram",
            "whatsapp",
            "sound_effect"
        )
        if (excludedTokens.any { normalizedPath.contains(it) || name.contains(it) }) return false

        val allowedMime = listOf("audio/mpeg", "audio/mp4", "audio/wav", "audio/3gpp", "audio/amr", "audio/aac", "audio/x-wav")
        val isAllowedMime = allowedMime.any { mime.startsWith(it) }
        if (!isAllowedMime) return false

        if (!options.recordingsOnly) return true

        val recordingHints = listOf(
            "recordings",
            "voice",
            "recorder",
            "sound recorder",
            "notes",
            "memo"
        )

        return recordingHints.any { normalizedPath.contains(it) || name.contains(it) }
    }

    fun getTopLevelFolders(): List<String> {
        val folders = mutableSetOf<String>()
        val projection = arrayOf(MediaStore.Audio.Media.DATA)
        val cursor = context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            null
        )

        cursor?.use {
            val pathColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            while (it.moveToNext()) {
                val path = it.getString(pathColumn) ?: continue
                extractTopLevelFolder(path)?.let { folder -> folders.add(folder) }
            }
        }

        return folders.sorted()
    }

    fun estimateVoiceLevel(durationMs: Long, sizeBytes: Long): Int {
        // Simple proxy score 1-5 (not true waveform amplitude).
        val bitrate = if (durationMs > 0) (sizeBytes * 8) / (durationMs / 1000.0) else 0.0
        return when {
            bitrate >= 128_000 -> 5
            bitrate >= 96_000 -> 4
            bitrate >= 64_000 -> 3
            bitrate >= 32_000 -> 2
            else -> 1
        }
    }

    private fun extractTopLevelFolder(path: String): String? {
        val marker = "/storage/emulated/0/"
        val idx = path.indexOf(marker)
        if (idx == -1) return null
        val rest = path.substring(idx + marker.length)
        val slash = rest.indexOf('/')
        return if (slash > 0) rest.substring(0, slash) else null
    }

    fun formatDuration(milliseconds: Long): String {
        val seconds = milliseconds / 1000
        val minutes = seconds / 60
        val secs = seconds % 60
        return String.format("%d:%02d", minutes, secs)
    }

    fun formatDate(seconds: Long): String {
        return SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
            .format(Date(seconds * 1000))
    }
}
