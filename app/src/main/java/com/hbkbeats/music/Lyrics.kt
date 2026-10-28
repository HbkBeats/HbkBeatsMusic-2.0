package com.hbkbeats.music

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs

class Lyrics(val raw: String, val lines: List<Pair<Long, String>>, val synced: Boolean) {
    val plain: String get() = if (synced) lines.joinToString("\n") { it.second } else raw
}

/**
 * Lyrics come from (1) text you saved/pasted, (2) a free online lookup (LRCLIB). Results are cached
 * on the phone. Private tracks are never looked up online.
 */
object LyricsRepo {
    private val stamp = Regex("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?]")

    fun parse(text: String): Lyrics {
        val out = mutableListOf<Pair<Long, String>>()
        for (line in text.lines()) {
            val ms = stamp.findAll(line).toList()
            if (ms.isEmpty()) continue
            val txt = line.replace(stamp, "").trim()
            for (m in ms) {
                val min = m.groupValues[1].toLong()
                val sec = m.groupValues[2].toLong()
                val frac = m.groupValues[3]
                val f = if (frac.isEmpty()) 0L else frac.padEnd(3, '0').take(3).toLong()
                out += (min * 60_000 + sec * 1_000 + f) to txt
            }
        }
        return if (out.size >= 2) Lyrics(text, out.sortedBy { it.first }, true) else Lyrics(text, emptyList(), false)
    }

    private fun file(ctx: Context, t: Track): File {
        val dir = File(ctx.filesDir, "lyrics")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "a${t.id}.txt")
    }

    fun save(ctx: Context, t: Track, text: String) {
        val f = file(ctx, t)
        if (text.isBlank()) f.delete() else f.writeText(text)
    }

    suspend fun get(ctx: Context, t: Track, force: Boolean): Lyrics? = withContext(Dispatchers.IO) {
        val f = file(ctx, t)
        val local = if (f.exists()) f.readText() else null
        if (local != null && !force) return@withContext parse(local)
        if (PrivateStore.isPrivate(t)) return@withContext local?.let { parse(it) }
        val online = try { fetchOnline(t) } catch (_: Exception) { null }
        if (online != null) {
            f.writeText(online)
            parse(online)
        } else {
            local?.let { parse(it) }
        }
    }

    private fun str(o: JSONObject, k: String): String = if (o.isNull(k)) "" else o.optString(k, "")

    private fun search(url: String, secs: Double): String? {
        val c = URL(url).openConnection() as HttpURLConnection
        return try {
            c.setRequestProperty("User-Agent", "HbkBeatsMusic/1.0")
            c.connectTimeout = 8000
            c.readTimeout = 8000
            if (c.responseCode != 200) return null
            val arr = JSONArray(c.inputStream.bufferedReader().use { it.readText() })
            var best: JSONObject? = null
            var bestScore = Double.MAX_VALUE
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val synced = str(o, "syncedLyrics")
                val plain = str(o, "plainLyrics")
                if (synced.isEmpty() && plain.isEmpty()) continue
                val d = abs(o.optDouble("duration", secs) - secs) + (if (synced.isEmpty()) 8.0 else 0.0)
                if (d < bestScore) { bestScore = d; best = o }
            }
            best?.let { str(it, "syncedLyrics").ifEmpty { str(it, "plainLyrics") } }?.takeIf { it.isNotBlank() }
        } finally {
            c.disconnect()
        }
    }

    private fun fetchOnline(t: Track): String? {
        val artist = t.artist.takeIf { it.isNotBlank() && !it.contains("unknown", true) }
        val title = t.title.replace(Regex("\\(.*?\\)|\\[.*?\\]"), "").trim().ifEmpty { t.title }
        val secs = t.durationMs / 1000.0
        val base = "https://lrclib.net/api/search?track_name=" + URLEncoder.encode(title, "UTF-8")
        if (artist != null) {
            search(base + "&artist_name=" + URLEncoder.encode(artist, "UTF-8"), secs)?.let { return it }
        }
        return search(base, secs)
    }
}
