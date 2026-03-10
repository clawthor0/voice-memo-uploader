package com.example.voicememouploader

data class VoiceMemo(
    val id: Long,
    val title: String,
    val path: String,
    val duration: Long,
    val dateAdded: Long,
    val size: Long
)
