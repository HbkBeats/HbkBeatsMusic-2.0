package com.hbkbeats.music

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.videolan.libvlc.util.VLCVideoLayout

class VideoPlayerActivity : ComponentActivity() {
    private var layout: VLCVideoLayout? = null
    private var priv = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        PlayerManager.init(this)
        PrivateStore.init(this)
        ThemeState.load()
        priv = intent.getBooleanExtra("private", false)
        if (priv && !PrivateStore.unlocked) { finish(); return }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (priv) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val idx = intent.getIntExtra("index", 0)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = ThemeState.accent)) {
                VideoScreen(idx, priv, onLayout = { layout = it }, onBack = { finish() })
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (priv && !PrivateStore.unlocked) { finish(); return }
        layout?.let { PlayerManager.attachVideo(it) }
    }

    override fun onStop() {
        super.onStop()
        PlayerManager.detachVideo()
        if (PlayerManager.current?.isVideo == true) PlayerManager.pause()
        // Leaving the app while watching locks the private space.
        if (!isFinishing) PrivateStore.lock()
    }
}

@Composable
fun VideoScreen(startIndex: Int, priv: Boolean, onLayout: (VLCVideoLayout) -> Unit, onBack: () -> Unit) {
    var show by remember { mutableStateOf(true) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(0f) }

    Box(
        Modifier.fillMaxSize().background(Color.Black).clickable(
            indication = null, interactionSource = remember { MutableInteractionSource() }
        ) { show = !show }
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                VLCVideoLayout(ctx).also {
                    onLayout(it)
                    PlayerManager.attachVideo(it)
                }
            }
        )
        LaunchedEffect(Unit) {
            val list = MediaRepository.videos.filter { PrivateStore.isPrivate(it) == priv }
            if (list.isNotEmpty() && PlayerManager.current?.id != list.getOrNull(startIndex)?.id) {
                PlayerManager.play(list, startIndex)
            }
        }
        if (show) {
            Row(
                Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
                Text(
                    PlayerManager.current?.title ?: "", color = Color.White,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                )
                TextButton({ PlayerManager.cycleSpeed() }) {
                    Text("${PlayerManager.speed}x", color = ThemeState.accent)
                }
            }
            Column(Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(fmt(PlayerManager.position), color = Color.White, style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = if (dragging) dragValue else PlayerManager.position.toFloat(),
                        onValueChange = { dragging = true; dragValue = it },
                        onValueChangeFinished = { PlayerManager.seekTo(dragValue.toLong()); dragging = false },
                        valueRange = 0f..PlayerManager.duration.coerceAtLeast(1).toFloat(),
                        colors = SliderDefaults.colors(thumbColor = ThemeState.accent, activeTrackColor = ThemeState.accent),
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )
                    Text(fmt(PlayerManager.duration), color = Color.White, style = MaterialTheme.typography.bodySmall)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                    IconButton({ PlayerManager.prev() }) { Icon(Icons.Default.SkipPrevious, "Previous", tint = Color.White) }
                    IconButton({ PlayerManager.toggle() }) {
                        Icon(
                            if (PlayerManager.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            "Play/Pause", tint = Color.White
                        )
                    }
                    IconButton({ PlayerManager.next() }) { Icon(Icons.Default.SkipNext, "Next", tint = Color.White) }
                }
            }
        }
    }
}
