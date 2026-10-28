package com.hbkbeats.music

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.util.Calendar

enum class Overlay { Player, Equalizer, Effects, Skins }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        PlayerManager.init(this)
        PrivateStore.init(this)
        ThemeState.load()
        setContent { AppTheme { App() } }
    }

    override fun onStart() {
        super.onStart()
        PrivateStore.keepOpen = false
    }

    override fun onStop() {
        super.onStop()
        // Leaving the app (home, screen off, other app) locks the private space.
        if (!isChangingConfigurations && !PrivateStore.keepOpen) PrivateStore.lock()
    }
}

private fun hasMedia(ctx: Context): Boolean {
    val p = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE
    return ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
}

fun openVideo(ctx: Context, index: Int, priv: Boolean) {
    PrivateStore.keepOpen = true
    ctx.startActivity(
        Intent(ctx, VideoPlayerActivity::class.java)
            .putExtra("index", index)
            .putExtra("private", priv)
    )
}

// ============================================================ App shell

@Composable
fun App() {
    val ctx = LocalContext.current
    val perms = remember {
        if (Build.VERSION.SDK_INT >= 33) arrayOf(
            Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.POST_NOTIFICATIONS
        ) else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    var granted by remember { mutableStateOf(hasMedia(ctx)) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted = hasMedia(ctx) }

    LaunchedEffect(Unit) { if (!granted) launcher.launch(perms) }
    LaunchedEffect(granted) { if (granted) MediaRepository.load(ctx) }

    var nav by remember { mutableIntStateOf(0) }               // 0 Home, 1 Search, 2 Library, 3 Private
    var overlay by remember { mutableStateOf<Overlay?>(null) }
    var backTo by remember { mutableStateOf<Overlay?>(null) }

    fun open(o: Overlay, from: Overlay? = null) { backTo = from; overlay = o }

    // Normal screens never contain private items.
    val songs = remember(MediaRepository.songs, PrivateStore.hiddenFolders, PrivateStore.hiddenItems) {
        MediaRepository.songs.filter { !PrivateStore.isPrivate(it) }
    }
    val videos = remember(MediaRepository.videos, PrivateStore.hiddenFolders, PrivateStore.hiddenItems) {
        MediaRepository.videos.filter { !PrivateStore.isPrivate(it) }
    }

    val playingPrivate = PlayerManager.current?.let { PrivateStore.isPrivate(it) } == true
    val secure = PrivateStore.unlocked && (nav == 3 || (overlay == Overlay.Player && playingPrivate))
    LaunchedEffect(secure) {
        val w = (ctx as? ComponentActivity)?.window
        if (secure) w?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else w?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
    // If the private space locks while its song is on the player screen, close it.
    LaunchedEffect(PrivateStore.unlocked, overlay) {
        if (!PrivateStore.unlocked && overlay == Overlay.Player && playingPrivate) overlay = null
    }

    BackHandler(overlay != null) {
        overlay = if (overlay == Overlay.Player) null else backTo
        backTo = null
    }
    val goBack = { overlay = if (overlay == Overlay.Player) null else backTo; backTo = null }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                Column {
                    MiniPlayer(allowPrivate = nav == 3) { overlay = Overlay.Player }
                    NavBar(nav) { nav = it }
                }
            }
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                if (!granted) {
                    Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Allow access to your music and videos to see them here.")
                        Button(onClick = { launcher.launch(perms) }, modifier = Modifier.padding(top = 12.dp)) {
                            Text("Grant access")
                        }
                    }
                } else when (nav) {
                    0 -> HomeScreen(songs, videos, onOverlay = { open(it) }, onOpenPlayer = { overlay = Overlay.Player })
                    1 -> SearchScreen(songs, videos)
                    2 -> LibraryScreen(songs, videos)
                    else -> if (PrivateStore.unlocked) PrivateScreen() else PinGate()
                }
            }
        }
        AnimatedVisibility(
            visible = overlay != null,
            enter = slideInVertically { it },
            exit = ExitTransition.None
        ) {
            when (overlay) {
                Overlay.Player -> NowPlayingScreen(onClose = { overlay = null }, onOverlay = { open(it, Overlay.Player) })
                Overlay.Equalizer -> EqualizerScreen(goBack)
                Overlay.Effects -> EffectsScreen(goBack)
                Overlay.Skins -> SkinsScreen(goBack)
                null -> {}
            }
        }
    }
}

@Composable
fun NavBar(sel: Int, onSel: (Int) -> Unit) {
    val accent = ThemeState.accent
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    NavigationBar(containerColor = ThemeState.skin.bg) {
        listOf(
            "Home" to Icons.Default.Home,
            "Search" to Icons.Default.Search,
            "Library" to Icons.Default.LibraryMusic,
            "Private" to Icons.Default.Lock
        ).forEachIndexed { i, (label, icon) ->
            NavigationBarItem(
                selected = sel == i,
                onClick = { onSel(i) },
                icon = { Icon(icon, label) },
                label = { Text(label, fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = accent, selectedTextColor = accent,
                    indicatorColor = accent.copy(alpha = 0.18f),
                    unselectedIconColor = muted, unselectedTextColor = muted
                )
            )
        }
    }
}

// ============================================================ Home

@Composable
fun HomeScreen(
    songs: List<Track>,
    videos: List<Track>,
    onOverlay: (Overlay) -> Unit,
    onOpenPlayer: () -> Unit
) {
    val ctx = LocalContext.current
    val accent = ThemeState.accent
    val bg = ThemeState.skin.bg
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = if (hour < 12) "Good morning" else if (hour < 18) "Good afternoon" else "Good evening"
    val recent = remember(songs, Prefs.recentTick) {
        Prefs.recents().mapNotNull { id -> songs.firstOrNull { it.id == id } }
    }
    val hero = PlayerManager.current?.takeIf { !it.isVideo && !PrivateStore.isPrivate(it) }

    LazyColumn(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.40f), bg), endY = 1100f)),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(greeting, Modifier.weight(1f), fontSize = 26.sp, fontWeight = FontWeight.Bold)
                IconButton({ onOverlay(Overlay.Equalizer) }) { Icon(Icons.Default.Equalizer, "Equalizer") }
                IconButton({ onOverlay(Overlay.Effects) }) { Icon(Icons.Default.Tune, "Effects") }
                IconButton({ onOverlay(Overlay.Skins) }) { Icon(Icons.Default.Palette, "Skins") }
            }
        }
        item {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(ThemeState.skin.surface.copy(alpha = 0.75f))
                    .clickable(enabled = hero != null) { onOpenPlayer() }
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                NowArt(hero, PlayerManager.isPlaying && hero != null, Modifier.size(210.dp))
                Spacer(Modifier.height(14.dp))
                Text(
                    hero?.title ?: "Pick a song to start the record",
                    fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(hero?.artist ?: "Your music, spinning", color = muted, maxLines = 1)
                Spacer(Modifier.height(8.dp))
                if (hero != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton({ PlayerManager.prev() }) { Icon(Icons.Default.SkipPrevious, "Previous", Modifier.size(32.dp)) }
                        Box(
                            Modifier.size(56.dp).clip(CircleShape).background(accent).clickable { PlayerManager.toggle() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (PlayerManager.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                "Play/Pause", Modifier.size(34.dp), tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        IconButton({ PlayerManager.next() }) { Icon(Icons.Default.SkipNext, "Next", Modifier.size(32.dp)) }
                    }
                } else {
                    Button({ PlayerManager.playShuffled(songs) }, enabled = songs.isNotEmpty()) {
                        Icon(Icons.Default.Shuffle, null)
                        Text("  Shuffle all")
                    }
                }
            }
        }
        if (recent.isNotEmpty()) {
            item { RowTitle("Recently played") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(recent) { t -> SongCard(t) { PlayerManager.play(songs, songs.indexOf(t)) } }
                }
            }
        }
        if (songs.isNotEmpty()) {
            item { RowTitle("Your songs") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(songs.take(40)) { i, t -> SongCard(t) { PlayerManager.play(songs, i) } }
                }
            }
        }
        if (videos.isNotEmpty()) {
            item { RowTitle("Videos") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(videos.take(40)) { i, t -> SongCard(t) { openVideo(ctx, i, false) } }
                }
            }
        }
    }
}

@Composable
private fun RowTitle(text: String) {
    Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 10.dp))
}

@Composable
private fun SongCard(t: Track, onClick: () -> Unit) {
    Column(Modifier.width(140.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick)) {
        Thumb(t, 140.dp, corner = 10.dp)
        Text(t.title, Modifier.padding(top = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
        Text(
            if (t.isVideo) t.folderName else t.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ============================================================ Search

@Composable
fun SearchScreen(songs: List<Track>, videos: List<Track>) {
    val ctx = LocalContext.current
    var q by remember { mutableStateOf("") }
    val rs = remember(q, songs) {
        if (q.isBlank()) emptyList() else songs.filter {
            it.title.contains(q, true) || it.artist.contains(q, true) || it.folderName.contains(q, true)
        }
    }
    val rv = remember(q, videos) {
        if (q.isBlank()) emptyList() else videos.filter {
            it.title.contains(q, true) || it.folderName.contains(q, true)
        }
    }
    Column(Modifier.fillMaxSize()) {
        Text("Search", Modifier.padding(start = 16.dp, top = 16.dp), fontSize = 26.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = q, onValueChange = { q = it },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            singleLine = true, shape = RoundedCornerShape(50),
            placeholder = { Text("Songs, artists, videos…") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (q.isNotEmpty()) IconButton({ q = "" }) { Icon(Icons.Default.Close, "Clear") }
            }
        )
        if (q.isBlank()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("Find anything in your library.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (rs.isEmpty() && rv.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("No results for “$q”", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                if (rs.isNotEmpty()) {
                    item { RowTitle("Songs") }
                    itemsIndexed(rs) { i, t -> TrackRow(t, null) { PlayerManager.play(rs, i) } }
                }
                if (rv.isNotEmpty()) {
                    item { RowTitle("Videos") }
                    itemsIndexed(rv) { i, t -> TrackRow(t, null) { openVideo(ctx, videos.indexOf(t), false) } }
                }
            }
        }
    }
}

// ============================================================ Library

@Composable
fun LibraryScreen(songs: List<Track>, videos: List<Track>) {
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Your Library", Modifier.weight(1f), fontSize = 26.sp, fontWeight = FontWeight.Bold)
            if (tab == 0) TextButton({ PlayerManager.playShuffled(songs) }, enabled = songs.isNotEmpty()) {
                Icon(Icons.Default.Shuffle, null, tint = ThemeState.accent)
                Text("  Shuffle", color = ThemeState.accent)
            }
        }
        TabRow(selectedTabIndex = tab, containerColor = Color.Transparent, contentColor = ThemeState.accent) {
            Tab(tab == 0, { tab = 0 }, text = { Text("Music (${songs.size})") })
            Tab(tab == 1, { tab = 1 }, text = { Text("Videos (${videos.size})") })
        }
        if (tab == 0) TrackList(songs, privateView = false) { i -> PlayerManager.play(songs, i) }
        else TrackList(videos, privateView = false) { i -> openVideo(ctx, i, false) }
    }
}

// ============================================================ Private space

@Composable
fun PinGate() {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val creating = !PrivateStore.hasPin
    val keys = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Lock, null, tint = ThemeState.accent, modifier = Modifier.size(40.dp))
        Text(
            if (creating) "Create a PIN for your private space (4-8 digits)" else "Enter your PIN",
            modifier = Modifier.padding(vertical = 12.dp)
        )
        OutlinedTextField(
            value = pin, onValueChange = { pin = it.filter(Char::isDigit).take(8); error = "" },
            label = { Text("PIN") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), keyboardOptions = keys
        )
        if (creating) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = confirm, onValueChange = { confirm = it.filter(Char::isDigit).take(8); error = "" },
                label = { Text("Confirm PIN") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = keys
            )
        }
        if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        Button(
            onClick = {
                if (creating) {
                    when {
                        pin.length < 4 -> error = "Use at least 4 digits"
                        pin != confirm -> error = "PINs don't match"
                        else -> { PrivateStore.setPin(pin); PrivateStore.unlock(pin) }
                    }
                } else if (!PrivateStore.unlock(pin)) {
                    error = "Wrong PIN"; pin = ""
                }
            },
            modifier = Modifier.padding(top = 16.dp)
        ) { Text(if (creating) "Create & unlock" else "Unlock") }
    }
}

@Composable
fun PrivateScreen() {
    val ctx = LocalContext.current
    var sub by remember { mutableIntStateOf(0) }
    val pv = remember(MediaRepository.videos, PrivateStore.hiddenFolders, PrivateStore.hiddenItems) {
        MediaRepository.videos.filter { PrivateStore.isPrivate(it) }
    }
    val ps = remember(MediaRepository.songs, PrivateStore.hiddenFolders, PrivateStore.hiddenItems) {
        MediaRepository.songs.filter { PrivateStore.isPrivate(it) }
    }
    val accent = ThemeState.accent
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton({ sub = 0 }) { Text("Videos (${pv.size})", color = if (sub == 0) accent else Color.Gray) }
            TextButton({ sub = 1 }) { Text("Music (${ps.size})", color = if (sub == 1) accent else Color.Gray) }
            Spacer(Modifier.weight(1f))
            TextButton({ PrivateStore.lock() }) {
                Icon(Icons.Default.Lock, null, tint = accent)
                Text("  Lock", color = accent)
            }
        }
        val empty = "Nothing private yet. Tap ⋮ on any file in Library or Search to hide it here."
        if (sub == 0) TrackList(pv, privateView = true, emptyText = empty) { i -> openVideo(ctx, i, true) }
        else TrackList(ps, privateView = true, emptyText = empty) { i -> PlayerManager.play(ps, i) }
    }
}

// ============================================================ Lists

@Composable
fun TrackList(
    tracks: List<Track>,
    privateView: Boolean,
    emptyText: String = "Nothing found yet",
    onClick: (Int) -> Unit
) {
    if (tracks.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { Text(emptyText) }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(tracks, key = { _, t -> (if (t.isVideo) "v" else "a") + t.id }) { i, t ->
            TrackRow(t, privateView) { onClick(i) }
        }
    }
}

/** privateView: null = no menu, false = "make private" menu, true = "unhide" menu. */
@Composable
fun TrackRow(t: Track, privateView: Boolean?, onClick: () -> Unit) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val playing = PlayerManager.current?.let { it.id == t.id && it.isVideo == t.isVideo } == true
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Thumb(t, 50.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                t.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (playing) ThemeState.accent else MaterialTheme.colorScheme.onSurface
            )
            Text(
                if (t.isVideo) "${t.folderName} • ${fmt(t.durationMs)}" else "${t.artist} • ${fmt(t.durationMs)}",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        if (privateView == null) {
            Spacer(Modifier.width(12.dp))
        } else Box {
            IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, "More") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (!privateView) {
                    DropdownMenuItem(
                        text = { Text("Make private") },
                        onClick = {
                            menu = false
                            if (!PrivateStore.hasPin)
                                Toast.makeText(ctx, "Create a PIN in the Private tab first", Toast.LENGTH_LONG).show()
                            else PrivateStore.hideItem(t)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Make folder “${t.folderName}” private") },
                        onClick = {
                            menu = false
                            if (!PrivateStore.hasPin)
                                Toast.makeText(ctx, "Create a PIN in the Private tab first", Toast.LENGTH_LONG).show()
                            else PrivateStore.hideFolder(t)
                        }
                    )
                } else {
                    if (PrivateStore.isItemHidden(t)) DropdownMenuItem(
                        text = { Text("Remove from private") },
                        onClick = { menu = false; PrivateStore.unhideItem(t) }
                    )
                    if (PrivateStore.isFolderHidden(t)) DropdownMenuItem(
                        text = { Text("Unhide folder “${t.folderName}”") },
                        onClick = { menu = false; PrivateStore.unhideFolder(t) }
                    )
                }
            }
        }
    }
}

// ============================================================ Mini player

@Composable
fun MiniPlayer(allowPrivate: Boolean, onOpen: () -> Unit) {
    val t = PlayerManager.current
    if (t == null || t.isVideo) return
    // Private songs never show here outside the unlocked Private tab.
    if (PrivateStore.isPrivate(t) && !(allowPrivate && PrivateStore.unlocked)) return
    val accent = ThemeState.accent
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp)).clickable(onClick = onOpen),
        color = lerp(ThemeState.skin.surface, accent, 0.20f)
    ) {
        Column {
            Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Thumb(t, 44.dp)
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(
                        t.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton({ PlayerManager.toggle() }) {
                    Icon(if (PlayerManager.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/Pause")
                }
                IconButton({ PlayerManager.next() }) { Icon(Icons.Default.SkipNext, "Next") }
            }
            val progress = if (PlayerManager.duration > 0)
                (PlayerManager.position.toFloat() / PlayerManager.duration).coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(
                progress = { progress }, modifier = Modifier.fillMaxWidth().height(2.dp),
                color = accent, trackColor = Color.Transparent
            )
        }
    }
}
