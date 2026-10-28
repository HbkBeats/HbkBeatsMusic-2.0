package com.hbkbeats.music

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/** Equalizer + effects settings. EQ is live; effects rebuild the audio engine when applied. */
object AudioFx {
    val bandLabels = listOf("31 Hz", "62 Hz", "125 Hz", "250 Hz", "500 Hz", "1 kHz", "2 kHz", "4 kHz", "8 kHz", "16 kHz")

    class Preset(val name: String, val gains: List<Float>)

    // 10 bands, in dB. Genre curves are starting points - tweak to taste.
    val presets = listOf(
        Preset("Flat", listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
        Preset("Reggae", listOf(5f, 4f, 1f, -2f, -2f, 0f, 2f, 3f, 3f, 2f)),
        Preset("Afrobeats", listOf(5f, 4f, 2f, 0f, -1f, 1f, 3f, 3f, 3f, 2f)),
        Preset("Amapiano", listOf(6f, 5f, 3f, 1f, -1f, 0f, 2f, 3f, 4f, 3f)),
        Preset("Afro House", listOf(6f, 5f, 2f, 0f, -1f, 0f, 2f, 3f, 4f, 3f)),
        Preset("Dancehall", listOf(5f, 4f, 2f, 0f, -2f, 0f, 2f, 3f, 3f, 2f)),
        Preset("Drill", listOf(7f, 5f, 1f, -3f, -2f, 0f, 2f, 4f, 4f, 1f)),
        Preset("Trap", listOf(7f, 5f, 0f, -3f, -2f, 0f, 2f, 3f, 4f, 2f)),
        Preset("Hip-Hop", listOf(6f, 5f, 2f, 1f, -1f, -1f, 1f, 2f, 3f, 2f)),
        Preset("R&B", listOf(3f, 4f, 3f, 1f, 0f, 1f, 2f, 2f, 2f, 1f)),
        Preset("Soul", listOf(3f, 3f, 2f, 1f, 0f, 1f, 2f, 2f, 2f, 2f)),
        Preset("Kwaito", listOf(6f, 5f, 3f, 1f, -1f, -1f, 1f, 2f, 3f, 2f)),
        Preset("Gospel", listOf(2f, 2f, 1f, 1f, 2f, 3f, 3f, 2f, 2f, 1f)),
        Preset("Pop", listOf(-1f, 0f, 2f, 3f, 3f, 2f, 0f, -1f, -1f, -1f)),
        Preset("Rock", listOf(5f, 4f, 2f, -1f, -2f, -1f, 2f, 3f, 4f, 4f)),
        Preset("EDM", listOf(6f, 5f, 2f, 0f, -2f, 2f, 1f, 2f, 5f, 6f)),
        Preset("Jazz", listOf(3f, 2f, 1f, 2f, -2f, -2f, 0f, 1f, 2f, 3f)),
        Preset("Classical", listOf(3f, 2f, 1f, 0f, 0f, 0f, -1f, -1f, 2f, 3f)),
        Preset("Acoustic", listOf(3f, 3f, 2f, 1f, 1f, 1f, 2f, 2f, 3f, 2f)),
        Preset("Lo-fi", listOf(3f, 3f, 2f, 1f, 0f, -1f, -2f, -3f, -4f, -5f)),
        Preset("Bass Boost", listOf(8f, 7f, 5f, 2f, 0f, 0f, 0f, 0f, 0f, 0f)),
        Preset("Treble Boost", listOf(0f, 0f, 0f, 0f, 0f, 1f, 3f, 5f, 7f, 8f)),
        Preset("Vocal Boost", listOf(-2f, -2f, -1f, 1f, 3f, 4f, 4f, 3f, 1f, 0f)),
        Preset("Speech", listOf(-4f, -3f, -1f, 1f, 3f, 4f, 4f, 3f, 0f, -2f))
    )

    // ---- Equalizer state ----
    var eqEnabled by mutableStateOf(false)
    var eqPreset by mutableStateOf("Flat")
    val gains = mutableStateListOf<Float>().apply { repeat(10) { add(0f) } }
    var preamp by mutableFloatStateOf(0f)

    // ---- Effects state ----
    var modEffect by mutableIntStateOf(0)          // 0 off, 1 chorus, 2 flanger
    var modAmount by mutableFloatStateOf(0.5f)
    var reverb by mutableStateOf(false)
    var reverbSize by mutableFloatStateOf(0.5f)
    var widen by mutableStateOf(false)
    var compressor by mutableStateOf(false)
    var karaoke by mutableStateOf(false)
    var fxDirty by mutableStateOf(false)
    /** 0 = effects fully applied, 1 = filters on but fine-tuning ignored, 2 = effects unavailable */
    var fxStatus by mutableIntStateOf(0)

    fun choose(p: Preset) {
        eqEnabled = true
        eqPreset = p.name
        p.gains.forEachIndexed { i, g -> gains[i] = g }
        save()
    }

    private fun sp(ctx: Context) = ctx.applicationContext.getSharedPreferences("audio_fx", Context.MODE_PRIVATE)

    fun load(ctx: Context) {
        bind(ctx)
        val s = sp(ctx)
        eqEnabled = s.getBoolean("eq_on", false)
        eqPreset = s.getString("eq_preset", "Flat") ?: "Flat"
        val g = (s.getString("eq_gains", "") ?: "").split(",").mapNotNull { it.toFloatOrNull() }
        if (g.size == 10) g.forEachIndexed { i, v -> gains[i] = v }
        preamp = s.getFloat("preamp", 0f)
        modEffect = s.getInt("mod", 0)
        modAmount = s.getFloat("mod_amt", 0.5f)
        reverb = s.getBoolean("reverb", false)
        reverbSize = s.getFloat("reverb_size", 0.5f)
        widen = s.getBoolean("widen", false)
        compressor = s.getBoolean("comp", false)
        karaoke = s.getBoolean("karaoke", false)
    }

    fun save() {
        appContextSp?.edit()
            ?.putBoolean("eq_on", eqEnabled)
            ?.putString("eq_preset", eqPreset)
            ?.putString("eq_gains", gains.joinToString(","))
            ?.putFloat("preamp", preamp)
            ?.putInt("mod", modEffect)
            ?.putFloat("mod_amt", modAmount)
            ?.putBoolean("reverb", reverb)
            ?.putFloat("reverb_size", reverbSize)
            ?.putBoolean("widen", widen)
            ?.putBoolean("comp", compressor)
            ?.putBoolean("karaoke", karaoke)
            ?.apply()
    }

    private var appContextSp: android.content.SharedPreferences? = null
    fun bind(ctx: Context) { appContextSp = sp(ctx) }

    private fun f(v: Float) = String.format(Locale.US, "%.2f", v)

    val anyEffect: Boolean get() = modEffect != 0 || reverb || widen || compressor || karaoke

    /** libVLC option sets, tried in order until one initialises. */
    fun optionTiers(): List<List<String>> {
        val base = listOf("--audio-time-stretch")
        val filters = mutableListOf<String>()
        val params = mutableListOf<String>()
        when (modEffect) {
            1 -> {
                filters += "chorus_flanger"
                params += listOf(
                    "--delay-time=20", "--sweep-depth=6", "--sweep-rate=0.6", "--feedback-gain=0",
                    "--wet-mix=${f(0.3f + 0.6f * modAmount)}", "--dry-mix=${f(1f - 0.3f * modAmount)}"
                )
            }
            2 -> {
                filters += "chorus_flanger"
                params += listOf(
                    "--delay-time=3", "--sweep-depth=2", "--sweep-rate=0.35",
                    "--feedback-gain=${f(0.2f + 0.6f * modAmount)}",
                    "--wet-mix=${f(0.4f + 0.5f * modAmount)}", "--dry-mix=0.80"
                )
            }
        }
        if (reverb) {
            filters += "spatializer"
            params += listOf(
                "--spatializer-roomsize=${f(0.3f + 0.8f * reverbSize)}",
                "--spatializer-wet=${f(1f + 5f * reverbSize)}"
            )
        }
        if (widen) filters += "stereo_widen"
        if (compressor) filters += "compressor"
        if (karaoke) filters += "karaoke"
        if (filters.isEmpty()) return listOf(base)
        val chain = "--audio-filter=" + filters.joinToString(":")
        return listOf(base + chain + params, base + chain, base)
    }
}
