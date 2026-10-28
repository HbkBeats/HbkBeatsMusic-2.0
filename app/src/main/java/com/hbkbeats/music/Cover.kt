package com.hbkbeats.music

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Loads embedded album art (audio) or a frame thumbnail (video), with an in-memory cache. */
object CoverLoader {
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }
    private val missing = HashSet<String>()

    private fun key(t: Track) = (if (t.isVideo) "v" else "a") + t.id

    fun peek(t: Track): Bitmap? = cache.get(key(t))

    suspend fun load(ctx: Context, t: Track): Bitmap? = withContext(Dispatchers.IO) {
        val k = key(t)
        cache.get(k)?.let { return@withContext it }
        if (synchronized(missing) { k in missing }) return@withContext null
        var bmp: Bitmap? = null
        try {
            if (t.isVideo) {
                if (Build.VERSION.SDK_INT >= 29) {
                    bmp = ctx.contentResolver.loadThumbnail(t.uri, Size(480, 270), null)
                }
            } else {
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(ctx, t.uri)
                    val bytes = r.embeddedPicture
                    if (bytes != null) bmp = decode(bytes)
                } finally {
                    r.release()
                }
            }
        } catch (_: Exception) {
        }
        if (bmp != null) cache.put(k, bmp) else synchronized(missing) { missing.add(k) }
        bmp
    }

    private fun decode(b: ByteArray): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(b, 0, b.size, o)
        var s = 1
        while (o.outWidth / s > 800 || o.outHeight / s > 800) s *= 2
        return BitmapFactory.decodeByteArray(b, 0, b.size, BitmapFactory.Options().apply { inSampleSize = s })
    }
}

@Composable
fun rememberCover(t: Track?): Bitmap? {
    val ctx = LocalContext.current
    val s = produceState<Bitmap?>(t?.let { CoverLoader.peek(it) }, t?.id, t?.isVideo) {
        value = if (t == null) null else CoverLoader.peek(t) ?: CoverLoader.load(ctx, t)
    }
    return s.value
}

/** Small square artwork for lists and cards: cover art if tagged, otherwise an icon. */
@Composable
fun Thumb(t: Track, size: Dp, modifier: Modifier = Modifier, corner: Dp = 6.dp) {
    val bmp = rememberCover(t)
    Box(
        modifier.size(size).clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (bmp != null) {
            val img = remember(bmp) { bmp.asImageBitmap() }
            Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(
                if (t.isVideo) Icons.Default.Movie else Icons.Default.MusicNote,
                null, tint = ThemeState.accent
            )
        }
    }
}

/** Big artwork: the tagged cover if there is one, otherwise a spinning vinyl record. */
@Composable
fun NowArt(t: Track?, playing: Boolean, modifier: Modifier = Modifier) {
    val bmp = rememberCover(t)
    if (bmp != null) {
        val img = remember(bmp) { bmp.asImageBitmap() }
        Image(
            img, null, contentScale = ContentScale.Crop,
            modifier = modifier.aspectRatio(1f)
                .shadow(16.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
        )
    } else {
        Vinyl(playing, ThemeState.accent, modifier)
    }
}

@Composable
fun Vinyl(playing: Boolean, label: Color, modifier: Modifier = Modifier) {
    var angle by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing) {
        if (playing) {
            var last = 0L
            while (true) {
                withFrameNanos { n ->
                    if (last != 0L) angle = (angle + (n - last) / 1_000_000_000f * 72f) % 360f
                    last = n
                }
            }
        }
    }
    Canvas(modifier.aspectRatio(1f).shadow(14.dp, CircleShape)) {
        val r = size.minDimension / 2f
        val c = center
        rotate(angle, c) {
            drawCircle(Color(0xFF0B0B0B), r, c)
            var rr = r * 0.97f
            while (rr > r * 0.38f) {
                drawCircle(Color(0xFF1E1E1E), rr, c, style = Stroke(width = 1.4f))
                rr -= r * 0.032f
            }
            drawCircle(
                brush = Brush.sweepGradient(
                    listOf(
                        Color.Transparent, Color(0x33FFFFFF), Color.Transparent,
                        Color(0x33FFFFFF), Color.Transparent
                    ), c
                ),
                radius = r * 0.97f, center = c
            )
            drawCircle(label, r * 0.33f, c)
            drawCircle(Color(0x55000000), r * 0.33f, c, style = Stroke(width = 3f))
            drawCircle(Color.White.copy(alpha = 0.85f), r * 0.035f, Offset(c.x + r * 0.2f, c.y))
            drawCircle(Color(0xFF0B0B0B), r * 0.045f, c)
        }
    }
}
