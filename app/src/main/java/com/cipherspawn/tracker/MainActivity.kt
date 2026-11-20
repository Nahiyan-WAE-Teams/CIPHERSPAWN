package com.cipherspawn.tracker

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cipherspawn.tracker.data.ChannelStats
import com.cipherspawn.tracker.ui.TrackerViewModel
import com.cipherspawn.tracker.ui.TrackerViewModelFactory
import com.cipherspawn.tracker.ui.TrackerUiState

private val Ink = Color(0xFF080B12)
private val Panel = Color(0xFF111827)
private val Panel2 = Color(0xFF172235)
private val Cyan = Color(0xFF56E0D2)
private val Muted = Color(0xFF93A4B8)
private val White = Color(0xFFF4F7FB)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CipherSpawnApp() }
    }
}

@Composable
private fun CipherSpawnApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("cipher_prefs", Context.MODE_PRIVATE) }
    val vm: TrackerViewModel = viewModel(factory = TrackerViewModelFactory(prefs))
    val state by vm.state.collectAsState()
    var query by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Ink, surface = Panel, primary = Cyan,
            onBackground = White, onSurface = White, secondary = Color(0xFF9A8CFF)
        )
    ) {
        if (showSettings) {
            SettingsScreen(
                currentUrl = vm.baseUrl,
                onSave = { vm.setBaseUrl(it); showSettings = false },
                onBack = { showSettings = false }
            )
        } else {
            Dashboard(
                query = query,
                onQuery = { query = it },
                state = state,
                onSearch = { vm.loadChannel(query) },
                onRefresh = { vm.refresh() },
                onSettings = { showSettings = true }
            )
        }
    }
}

@Composable
private fun Dashboard(
    query: String,
    onQuery: (String) -> Unit,
    state: TrackerUiState,
    onSearch: () -> Unit,
    onRefresh: () -> Unit,
    onSettings: () -> Unit
) {
    val focus = LocalFocusManager.current
    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF101A2A), Ink, Ink)))
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).background(Cyan.copy(alpha = .12f), RoundedCornerShape(14.dp)), Alignment.Center) {
                Icon(Icons.Default.Shield, null, tint = Cyan, modifier = Modifier.size(23.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("CIPHERSPAWN", color = White, fontSize = 17.sp, fontWeight = FontWeight.Black, letterSpacing = 1.8.sp)
                Text("CHANNEL INTELLIGENCE", color = Muted, fontSize = 9.sp, letterSpacing = 2.sp)
            }
            IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings", tint = Muted) }
        }
        Spacer(Modifier.height(30.dp))
        Text("Know the numbers.", color = White, fontSize = 29.sp, fontWeight = FontWeight.Bold)
        Text("Track public YouTube channel performance.", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(20.dp))
        Row(
            Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(18.dp))
                .border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(18.dp)).padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Search, null, tint = Muted, modifier = Modifier.padding(start = 10.dp))
            TextField(
                value = query, onValueChange = onQuery,
                placeholder = { Text("Paste channel URL or @handle", color = Muted, fontSize = 13.sp) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = Cyan
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); onSearch() })
            )
            IconButton(onClick = { focus.clearFocus(); onSearch() }) {
                Icon(Icons.Default.ArrowUpward, "Search channel", tint = Ink,
                    modifier = Modifier.background(Cyan, CircleShape).padding(7.dp))
            }
        }
        Spacer(Modifier.height(18.dp))
        when (state) {
            is TrackerUiState.Loading -> LoadingPanel()
            is TrackerUiState.Error -> ErrorPanel(state.message)
            is TrackerUiState.Ready -> StatsContent(state.stats, onRefresh)
            TrackerUiState.Idle -> WelcomePanel()
        }
        Spacer(Modifier.height(22.dp))
        Text("PUBLIC DATA ONLY  •  BUILT FOR CLARITY", color = Muted, fontSize = 9.sp, letterSpacing = 1.5.sp)
    }
}

@Composable
private fun WelcomePanel() {
    Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(22.dp)).padding(20.dp)) {
        Text("Your channel dashboard starts here", color = White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))
        Text("Search with a YouTube channel link, channel ID, or @handle. CIPHERSPAWN will fetch public stats and explain what each number means.", color = Muted, fontSize = 13.sp, lineHeight = 20.sp)
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(Cyan, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text("No hidden subscriber data. No guesswork.", color = Cyan, fontSize = 11.sp)
        }
    }
}

@Composable
private fun LoadingPanel() {
    Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(22.dp)).padding(22.dp)) {
        LinearProgressIndicator(Modifier.fillMaxWidth(), color = Cyan, trackColor = Panel2)
        Spacer(Modifier.height(14.dp))
        Text("Reading public channel data…", color = White, fontWeight = FontWeight.Medium)
        Text("The engine retries temporary failures and respects API limits.", color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun ErrorPanel(message: String) {
    Column(Modifier.fillMaxWidth().background(Color(0xFF2A1820), RoundedCornerShape(20.dp)).padding(18.dp)) {
        Text("Could not load channel", color = Color(0xFFFF9B9B), fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(message, color = White, fontSize = 13.sp, lineHeight = 19.sp)
    }
}

@Composable
private fun StatsContent(stats: ChannelStats, onRefresh: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stats.title, color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("@${stats.handle ?: stats.channelId}", color = Muted, fontSize = 12.sp)
            }
            IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Refresh", tint = Cyan) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("SUBSCRIBERS", stats.subscriberCount?.formatCount() ?: "Hidden", "Public count", Modifier.weight(1f))
            StatCard("VIDEOS", stats.videoCount.formatCount(), "Published total", Modifier.weight(1f))
        }
        StatCard("TOTAL VIEWS", stats.viewCount.formatCount(), "Public channel views", Modifier.fillMaxWidth())
        StatCard("LIKES SCANNED", stats.likesTotal?.formatCount() ?: "Not scanned",
            stats.likesStatus ?: "Public video likes", Modifier.fillMaxWidth())
        Text("Last updated: ${stats.updatedAt}", color = Muted, fontSize = 10.sp)
        Text("Counts can change. Hidden subscriber counts stay hidden. Likes are summed only from videos the API allowed us to scan.", color = Muted, fontSize = 11.sp, lineHeight = 17.sp)
    }
}

@Composable
private fun StatCard(label: String, value: String, note: String, modifier: Modifier = Modifier) {
    Column(modifier.background(Brush.linearGradient(listOf(Panel2, Panel)), RoundedCornerShape(20.dp))
        .border(1.dp, Color.White.copy(alpha = .06f), RoundedCornerShape(20.dp)).padding(17.dp)) {
        Text(label, color = Muted, fontSize = 9.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(value, color = White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(note, color = Muted, fontSize = 10.sp)
    }
}

@Composable
private fun SettingsScreen(currentUrl: String, onSave: (String) -> Unit, onBack: () -> Unit) {
    var url by remember(currentUrl) { mutableStateOf(currentUrl) }
    Column(Modifier.fillMaxSize().background(Ink).padding(22.dp)) {
        Text("Settings", color = White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Connect your private API gateway. Your YouTube API key belongs on the server, never inside the app.", color = Muted, fontSize = 13.sp, lineHeight = 20.sp)
        Spacer(Modifier.height(22.dp))
        Text("CLOUDFLARE WORKER URL", color = Cyan, fontSize = 10.sp, letterSpacing = 1.4.sp)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = url, onValueChange = { url = it }, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("https://your-worker.workers.dev") }, singleLine = true)
        Spacer(Modifier.height(18.dp))
        Button(onClick = { onSave(url.trim()) }, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Ink)) { Text("Save connection") }
        TextButton(onClick = onBack) { Text("Back", color = Muted) }
    }
}

private fun Long.formatCount(): String = when {
    this >= 1_000_000_000 -> "${"%.2f".format(this / 1_000_000_000.0)}B"
    this >= 1_000_000 -> "${"%.2f".format(this / 1_000_000.0)}M"
    this >= 1_000 -> "${"%.1f".format(this / 1_000.0)}K"
    else -> toString()
}
