package com.hbkbeats.music

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object MediaRepository {
    /** Everything on the device, including private items. Screens must filter via PrivateStore. */
    var songs by mutableStateOf<List<Track>>(emptyList())
    var videos by mutableStateOf<List<Track>>(emptyList())

    fun load(ctx: Context) {
        songs = query(ctx, audio = true)
        videos = query(ctx, audio = false)
    }

    private fun query(ctx: Context, audio: Boolean): List<Track> {
        val base = if (audio) MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val proj = if (audio) arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA
        ) else arrayOf(
            MediaStore.Video.Media._ID, MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DURATION, MediaStore.Video.Media.DATA
        )
        val list = mutableListOf<Track>()
        try {
            ctx.contentResolver.query(base, proj, null, null, "TITLE COLLATE NOCASE ASC")?.use { c ->
                val dataCol = if (audio) 4 else 3
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val title = c.getString(1) ?: "Unknown"
                    val artist = if (audio) (c.getString(2) ?: "Unknown artist") else ""
                    val dur = c.getLong(if (audio) 3 else 2)
                    val path = c.getString(dataCol) ?: ""
                    val folderKey = path.substringBeforeLast('/', "unknown")
                    val folderName = folderKey.substringAfterLast('/', folderKey).ifEmpty { "Unknown" }
                    list += Track(
                        id, title, artist, ContentUris.withAppendedId(base, id),
                        dur, !audio, folderKey, folderName
                    )
                }
            }
        } catch (_: Exception) {
        }
        return list
    }
}
