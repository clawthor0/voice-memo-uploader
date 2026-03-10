package com.example.voicememouploader

import android.content.Context
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.*

class MediaStoreRepository(private val context: Context) {

    fun getVoiceMemos(): List<VoiceMemo> {
        val memos = mutableListOf<VoiceMemo>()
        
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.SIZE
        )

        val selection = "${MediaStore.Audio.Media.MIME_TYPE} = ? OR ${MediaStore.Audio.Media.MIME_TYPE} = ?"
        val selectionArgs = arrayOf("audio/mpeg", "audio/wav")
        
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        val cursor = context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )

        cursor?.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val pathColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val durationColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dateColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)

            while (it.moveToNext()) {
                memos.add(
                    VoiceMemo(
                        id = it.getLong(idColumn),
                        title = it.getString(nameColumn),
                        path = it.getString(pathColumn),
                        duration = it.getLong(durationColumn),
                        dateAdded = it.getLong(dateColumn),
                        size = it.getLong(sizeColumn)
                    )
                )
            }
        }

        return memos
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
