package com.hbkbeats.music

import android.net.Uri

data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val uri: Uri,
    val durationMs: Long,
    val isVideo: Boolean,
    val folderKey: String,
    val folderName: String
)

fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
