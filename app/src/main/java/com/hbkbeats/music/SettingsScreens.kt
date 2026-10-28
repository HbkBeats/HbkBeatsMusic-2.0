package com.hbkbeats.music

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ScreenFrame(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
                content()
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun Section(text: String) {
    Text(
        text, fontWeight = FontWeight.Bold, fontSize = 16.sp,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) ThemeState.accent else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick)
    ) {
        Text(
            text, Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onChange)
    }
}

// ------------------------------------------------------------------ Equalizer

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EqualizerScreen(onBack: () -> Unit) {
    ScreenFrame("Equalizer", onBack) {
        SwitchRow("Enable equalizer", AudioFx.eqEnabled) {
            AudioFx.eqEnabled = it
            AudioFx.save()
            PlayerManager.applyEq()
        }
        Section("Genre presets")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AudioFx.presets.forEach { p ->
                Chip(p.name, AudioFx.eqEnabled && AudioFx.eqPreset == p.name) {
                    AudioFx.choose(p)
                    PlayerManager.applyEq()
                }
            }
        }
        if (AudioFx.eqPreset == "Custom") {
            Text("Custom curve", Modifier.padding(top = 8.dp), color = ThemeState.accent)
        }
        Section("Preamp")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = AudioFx.preamp,
                onValueChange = { AudioFx.preamp = it; PlayerManager.applyEq() },
                onValueChangeFinished = { AudioFx.save() },
                valueRange = -12f..12f, modifier = Modifier.weight(1f)
            )
            Text("%+.0f dB".format(AudioFx.preamp), Modifier.width(60.dp))
        }
        Section("Bands")
        AudioFx.bandLabels.forEachIndexed { i, label ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.width(64.dp), fontSize = 13.sp)
                Slider(
                    value = AudioFx.gains[i],
                    onValueChange = {
                        AudioFx.gains[i] = it
                        AudioFx.eqPreset = "Custom"
                        AudioFx.eqEnabled = true
                        PlayerManager.applyEq()
                    },
                    onValueChangeFinished = { AudioFx.save() },
                    valueRange = -12f..12f, modifier = Modifier.weight(1f)
                )
                Text("%+.0f".format(AudioFx.gains[i]), Modifier.width(36.dp), fontSize = 13.sp)
            }
        }
    }
}

// ------------------------------------------------------------------ Effects

@Composable
fun EffectsScreen(onBack: () -> Unit) {
    fun changed() { AudioFx.fxDirty = true; AudioFx.save() }
    ScreenFrame("Effects", onBack) {
        Section("Modulation")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Off", AudioFx.modEffect == 0) { AudioFx.modEffect = 0; changed() }
            Chip("Chorus", AudioFx.modEffect == 1) { AudioFx.modEffect = 1; changed() }
            Chip("Flanger", AudioFx.modEffect == 2) { AudioFx.modEffect = 2; changed() }
        }
        if (AudioFx.modEffect != 0) {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Intensity", Modifier.width(80.dp))
                Slider(AudioFx.modAmount, { AudioFx.modAmount = it; changed() }, Modifier.weight(1f))
            }
        }
        Section("Space & character")
        SwitchRow("Reverb", AudioFx.reverb) { AudioFx.reverb = it; changed() }
        if (AudioFx.reverb) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Room size", Modifier.width(80.dp))
                Slider(AudioFx.reverbSize, { AudioFx.reverbSize = it; changed() }, Modifier.weight(1f))
            }
        }
        SwitchRow("Stereo widen", AudioFx.widen) { AudioFx.widen = it; changed() }
        SwitchRow("Compressor (louder, punchier)", AudioFx.compressor) { AudioFx.compressor = it; changed() }
        SwitchRow("Vocal remover (karaoke)", AudioFx.karaoke) { AudioFx.karaoke = it; changed() }

        Button(
            onClick = {
                AudioFx.save()
                PlayerManager.rebuildAudioEngine()
                AudioFx.fxDirty = false
            },
            enabled = AudioFx.fxDirty,
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
        ) { Text("Apply effects") }

        Text(
            "Applying restarts the audio engine for a second and resumes from the same spot.",
            Modifier.padding(top = 8.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (AudioFx.anyEffect && AudioFx.fxStatus == 1) {
            Text(
                "Your phone's audio engine ignored the fine-tuning values, so effects run with default settings.",
                Modifier.padding(top = 8.dp), fontSize = 12.sp, color = ThemeState.accent
            )
        } else if (AudioFx.anyEffect && AudioFx.fxStatus == 2) {
            Text(
                "These effects aren't available in this build's audio engine, so playback runs without them.",
                Modifier.padding(top = 8.dp), fontSize = 12.sp, color = ThemeState.accent
            )
        }
    }
}

// ------------------------------------------------------------------ Skins

@Composable
fun SkinsScreen(onBack: () -> Unit) {
    ScreenFrame("Skins & colours", onBack) {
        Section("Skins")
        Skins.chunked(2).forEachIndexed { row, pair ->
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEachIndexed { col, sk ->
                    val index = row * 2 + col
                    val sel = ThemeState.skinIndex == index
                    Box(
                        Modifier.weight(1f).height(84.dp).clip(RoundedCornerShape(14.dp))
                            .background(sk.bg)
                            .border(if (sel) 3.dp else 1.dp, if (sel) ThemeState.accent else Color.Gray.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                            .clickable { ThemeState.setSkin(index) }
                            .padding(12.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(18.dp).clip(CircleShape).background(sk.primary))
                                Box(Modifier.padding(start = 6.dp).size(width = 36.dp, height = 8.dp).clip(CircleShape).background(sk.surface))
                            }
                            Spacer(Modifier.weight(1f))
                            Text(sk.name, color = if (sk.light) Color(0xFF121212) else Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Section("Accent colour")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Chip("Skin default", ThemeState.customAccent == null) { ThemeState.setAccent(null) }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AccentChoices.forEach { c ->
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(c)
                        .border(if (ThemeState.customAccent == c) BorderStroke(3.dp, Color.White) else BorderStroke(0.dp, Color.Transparent), CircleShape)
                        .clickable { ThemeState.setAccent(c) }
                )
            }
        }
    }
}
