package com.hbkbeats.music

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.util.Random

/** One shared libVLC player for music and video. */
object PlayerManager {
    private lateinit var appContext: Context
    private var libVLC: LibVLC? = null
    private var player: MediaPlayer? = null
    private var pfd: ParcelFileDescriptor? = null
    private var videoLayout: VLCVideoLayout? = null
    private var equalizer: MediaPlayer.Equalizer? = null
    private var pendingSeek = 0L
    private var pendingPause = false
    private var stopped = true
    private val main = Handler(Looper.getMainLooper())
    private val random = Random()

    var queue by mutableStateOf<List<Track>>(emptyList()); private set
    var index by mutableIntStateOf(-1); private set
    var isPlaying by mutableStateOf(false); private set
    var position by mutableLongStateOf(0L); private set
    var duration by mutableLongStateOf(0L); private set
    var speed by mutableFloatStateOf(1f); private set
    var shuffle by mutableStateOf(false); private set
    var repeatMode by mutableIntStateOf(0); private set   // 0 off, 1 all, 2 one

    val current: Track? get() = queue.getOrNull(index)

    fun init(ctx: Context) {
        if (player != null) return
        appContext = ctx.applicationContext
        Prefs.init(appContext)
        AudioFx.load(appContext)
        create()
    }

    private fun create() {
        var lib: LibVLC? = null
        val tiers = AudioFx.optionTiers()
        for ((i, opts) in tiers.withIndex()) {
            try {
                lib = LibVLC(appContext, ArrayList(opts))
                AudioFx.fxStatus = if (AudioFx.anyEffect) i else 0
                break
            } catch (_: Exception) {
            }
        }
        libVLC = lib ?: LibVLC(appContext, arrayListOf<String>())
        player = MediaPlayer(libVLC).apply { setEventListener { onEvent(it) } }
        applyEq()
        videoLayout?.let { player?.attachViews(it, null, true, false) }
    }

    /** Re-creates libVLC so changed effects take effect; resumes where playback was. */
    fun rebuildAudioEngine() {
        val t = current
        val resume = position
        val wasPlaying = isPlaying
        try {
            player?.stop()
            player?.detachViews()
            player?.release()
            libVLC?.release()
        } catch (_: Exception) {
        }
        create()
        if (t != null && !stopped) {
            pendingSeek = resume
            pendingPause = !wasPlaying
            load()
        }
    }

    fun applyEq() {
        val p = player ?: return
        try {
            if (!AudioFx.eqEnabled) {
                p.setEqualizer(null)
                return
            }
            val eq = equalizer ?: MediaPlayer.Equalizer.create().also { equalizer = it }
            eq.setPreAmp(AudioFx.preamp)
            val n = MediaPlayer.Equalizer.getBandCount()
            for (i in 0 until minOf(n, AudioFx.gains.size)) eq.setAmp(i, AudioFx.gains[i])
            p.setEqualizer(eq)
        } catch (_: Exception) {
        }
    }

    private fun onEvent(e: MediaPlayer.Event) {
        when (e.type) {
            MediaPlayer.Event.Playing -> {
                isPlaying = true
                if (pendingSeek > 0) {
                    val s = pendingSeek
                    pendingSeek = 0
                    main.post { player?.time = s }
                }
                if (pendingPause) {
                    pendingPause = false
                    main.post { player?.pause() }
                }
                main.post { refreshService() }
            }
            MediaPlayer.Event.Paused, MediaPlayer.Event.Stopped -> {
                isPlaying = false
                main.post { refreshService() }
            }
            MediaPlayer.Event.EndReached -> main.post { onEnded() }
            MediaPlayer.Event.TimeChanged -> position = e.timeChanged
            MediaPlayer.Event.LengthChanged -> duration = e.lengthChanged
        }
    }

    private fun onEnded() {
        if (repeatMode == 2) load() else next(auto = true)
    }

    fun play(list: List<Track>, start: Int) {
        queue = list
        index = start
        load()
    }

    fun playShuffled(list: List<Track>) {
        if (list.isEmpty()) return
        shuffle = true
        play(list, random.nextInt(list.size))
    }

    private fun load() {
        val t = current ?: return
        val p = player ?: return
        try {
            pfd?.close()
            pfd = appContext.contentResolver.openFileDescriptor(t.uri, "r")
            val media = Media(libVLC, pfd!!.fileDescriptor)
            p.media = media
            media.release()
            stopped = false
            position = 0
            duration = t.durationMs
            p.play()
            p.rate = speed
            if (!t.isVideo) Prefs.pushRecent(t.id)
            refreshService()
        } catch (_: Exception) {
            next(auto = true)
        }
    }

    fun toggle() {
        val p = player ?: return
        if (p.isPlaying) p.pause() else p.play()
    }

    fun pause() { player?.pause() }

    fun next(auto: Boolean = false) {
        if (queue.isEmpty()) return
        if (shuffle && queue.size > 1) {
            var n: Int
            do { n = random.nextInt(queue.size) } while (n == index)
            index = n
            load()
        } else if (index < queue.lastIndex) {
            index++
            load()
        } else if (repeatMode == 1 || !auto) {
            index = 0
            load()
        } else {
            stop()
        }
    }

    fun prev() {
        if (position > 3000 || index <= 0) seekTo(0) else { index--; load() }
    }

    fun seekTo(ms: Long) { player?.time = ms }

    fun toggleShuffle() { shuffle = !shuffle }
    fun cycleRepeat() { repeatMode = (repeatMode + 1) % 3 }

    fun cycleSpeed() {
        val steps = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
        speed = steps[(steps.indexOf(speed) + 1) % steps.size]
        player?.rate = speed
    }

    fun stop() {
        stopped = true
        player?.stop()
        isPlaying = false
        try { appContext.stopService(Intent(appContext, PlaybackService::class.java)) } catch (_: Exception) {}
    }

    fun attachVideo(layout: VLCVideoLayout) {
        videoLayout = layout
        player?.attachViews(layout, null, true, false)
    }

    fun detachVideo() {
        videoLayout = null
        player?.detachViews()
    }

    private fun refreshService() {
        if (stopped || current == null) return
        try {
            ContextCompat.startForegroundService(
                appContext,
                Intent(appContext, PlaybackService::class.java).setAction(PlaybackService.ACTION_REFRESH)
            )
        } catch (_: Exception) {}
    }
}
