package com.hbkbeats.music

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun NowPlayingScreen(onClose: () -> Unit, onOverlay: (Overlay) -> Unit) {
    val ctx = LocalContext.current
    val t = PlayerManager.current
    if (t == null) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val accent = ThemeState.accent
    val bg = ThemeState.skin.bg
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val scope = rememberCoroutineScope()

    var lyrics by remember(t.id) { mutableStateOf<Lyrics?>(null) }
    var loading by remember(t.id) { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }

    suspend fun loadLyrics(force: Boolean) {
        loading = true
        lyrics = LyricsRepo.get(ctx, t, force)
        loading = false
    }
    LaunchedEffect(t.id) { loadLyrics(false) }

    Surface(Modifier.fillMaxSize(), color = bg) {
        Box(
            Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.45f), bg), endY = 1500f))
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val peek = 76.dp
                val topGap = 12.dp
                val panelH = maxHeight - topGap
                val travel = with(density) { (panelH - peek).toPx() }
                val frac = remember { Animatable(0f) }

                BackHandler(enabled = frac.value > 0.5f) { scope.launch { frac.animateTo(0f) } }

                val drag = Modifier.pointerInput(travel) {
                    detectVerticalDragGestures(
                        onDragEnd = { scope.launch { frac.animateTo(if (frac.value > 0.4f) 1f else 0f) } },
                        onDragCancel = { scope.launch { frac.animateTo(if (frac.value > 0.4f) 1f else 0f) } },
                        onVerticalDrag = { change, dy ->
                            change.consume()
                            scope.launch { frac.snapTo((frac.value - dy / travel).coerceIn(0f, 1f)) }
                        }
                    )
                }

                val artSize = minOf(maxWidth - 48.dp, maxHeight - 400.dp).coerceAtLeast(140.dp)

                // ---------- Player ----------
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClose) { Icon(Icons.Default.KeyboardArrowDown, "Close", Modifier.size(30.dp)) }
                        Spacer(Modifier.weight(1f))
                        Text("NOW PLAYING", fontSize = 12.sp, letterSpacing = 2.sp, color = muted)
                        Spacer(Modifier.weight(1f))
                        IconButton({ onOverlay(Overlay.Equalizer) }) { Icon(Icons.Default.Equalizer, "Equalizer") }
                        IconButton({ onOverlay(Overlay.Effects) }) { Icon(Icons.Default.Tune, "Effects") }
                    }
                    Column(
                        Modifier.weight(1f).fillMaxWidth().then(drag),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        NowArt(t, PlayerManager.isPlaying, Modifier.size(artSize))
                        Spacer(Modifier.height(20.dp))
                        Text(
                            t.title, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(t.artist, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    var dragging by remember { mutableStateOf(false) }
                    var dv by remember { mutableFloatStateOf(0f) }
                    val dur = PlayerManager.duration.coerceAtLeast(1).toFloat()
                    Slider(
                        value = if (dragging) dv else PlayerManager.position.toFloat().coerceIn(0f, dur),
                        onValueChange = { dragging = true; dv = it },
                        onValueChangeFinished = { PlayerManager.seekTo(dv.toLong()); dragging = false },
                        valueRange = 0f..dur
                    )
                    Row(Modifier.fillMaxWidth()) {
                        Text(fmt(PlayerManager.position), fontSize = 12.sp, color = muted)
                        Spacer(Modifier.weight(1f))
                        Text(fmt(PlayerManager.duration), fontSize = 12.sp, color = muted)
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton({ PlayerManager.toggleShuffle() }) {
                            Icon(Icons.Default.Shuffle, "Shuffle", tint = if (PlayerManager.shuffle) accent else muted)
                        }
                        IconButton({ PlayerManager.prev() }) {
                            Icon(Icons.Default.SkipPrevious, "Previous", Modifier.size(36.dp))
                        }
                        Box(
                            Modifier.size(68.dp).clip(CircleShape).background(accent)
                                .clickable { PlayerManager.toggle() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (PlayerManager.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                "Play/Pause", Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        IconButton({ PlayerManager.next() }) {
                            Icon(Icons.Default.SkipNext, "Next", Modifier.size(36.dp))
                        }
                        IconButton({ PlayerManager.cycleRepeat() }) {
                            Icon(
                                if (PlayerManager.repeatMode == 2) Icons.Default.RepeatOne else Icons.Default.Repeat,
                                "Repeat", tint = if (PlayerManager.repeatMode > 0) accent else muted
                            )
                        }
                    }
                    Spacer(Modifier.height(peek + 12.dp))
                }

                // ---------- Swipe-up lyrics panel ----------
                val preview by remember(lyrics) {
                    derivedStateOf {
                        val ly = lyrics
                        if (ly != null && ly.synced) {
                            ly.lines.lastOrNull { it.first <= PlayerManager.position }?.second
                                ?.takeIf { it.isNotBlank() } ?: "♪"
                        } else "Swipe up for lyrics"
                    }
                }
                Surface(
                    modifier = Modifier.fillMaxWidth().height(panelH).offset {
                        IntOffset(0, (with(density) { topGap.toPx() } + (1f - frac.value) * travel).roundToInt())
                    },
                    shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
                    color = lerp(ThemeState.skin.surface, accent, 0.28f),
                    shadowElevation = 16.dp
                ) {
                    Column(Modifier.fillMaxSize()) {
                        Column(
                            Modifier.fillMaxWidth().height(peek).then(drag)
                                .clickable { scope.launch { frac.animateTo(if (frac.value > 0.5f) 0f else 1f) } },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                Modifier.padding(top = 10.dp).width(40.dp).height(4.dp).clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f))
                            )
                            Text("Lyrics", Modifier.padding(top = 8.dp), fontWeight = FontWeight.Bold)
                            Text(
                                preview, Modifier.padding(horizontal = 24.dp), color = muted,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp
                            )
                        }
                        LyricsBody(lyrics, loading, Modifier.weight(1f).fillMaxWidth())
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            TextButton({ editing = true }) { Text("Edit / paste", color = accent) }
                            TextButton({ scope.launch { loadLyrics(true) } }) { Text("Search online", color = accent) }
                        }
                    }
                }
            }
        }
    }

    if (editing) {
        var text by remember { mutableStateOf(lyrics?.raw ?: "") }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Lyrics") },
            text = {
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                    placeholder = { Text("Paste lyrics, or synced .lrc text, here") }
                )
            },
            confirmButton = {
                TextButton({
                    LyricsRepo.save(ctx, t, text)
                    lyrics = if (text.isBlank()) null else LyricsRepo.parse(text)
                    editing = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton({ editing = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun LyricsBody(ly: Lyrics?, loading: Boolean, modifier: Modifier = Modifier) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    Box(modifier) {
        if (loading) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = ThemeState.accent)
        } else if (ly == null || (ly.lines.isEmpty() && ly.raw.isBlank())) {
            Text(
                "No lyrics yet.\nTap “Search online” to look them up, or “Edit / paste” to add your own.",
                Modifier.align(Alignment.Center).padding(24.dp),
                textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (ly.synced) {
            val state = rememberLazyListState()
            val idx by remember(ly) {
                derivedStateOf { ly.lines.indexOfLast { it.first <= PlayerManager.position }.coerceAtLeast(0) }
            }
            LaunchedEffect(idx) { state.animateScrollToItem((idx - 2).coerceAtLeast(0)) }
            LazyColumn(state = state, contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp)) {
                itemsIndexed(ly.lines) { i, l ->
                    Text(
                        l.second.ifBlank { "♪" },
                        fontSize = if (i == idx) 24.sp else 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (i == idx) onSurface else onSurface.copy(alpha = 0.45f),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        } else {
            Text(
                ly.raw,
                Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                fontSize = 18.sp, lineHeight = 28.sp
            )
        }
    }
}
