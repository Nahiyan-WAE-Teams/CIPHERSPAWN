package com.cipherspawn.tracker

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cipherspawn.tracker.data.ChannelStats
import com.cipherspawn.tracker.data.ChannelSnapshot
import com.cipherspawn.tracker.data.TrackedChannel
import com.cipherspawn.tracker.data.TrackerDatabase
import com.cipherspawn.tracker.data.RefreshScheduler
import com.cipherspawn.tracker.data.SecurePrefs
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
    val prefs = remember { SecurePrefs.get(context) }
    val dao = remember { TrackerDatabase.get(context).trackerDao() }
    LaunchedEffect(Unit) {
        if (prefs.getBoolean("auto_refresh", false)) RefreshScheduler.enable(context)
    }
    val vm: TrackerViewModel = viewModel(factory = TrackerViewModelFactory(prefs, dao))
    val state by vm.state.collectAsState()
    val tracked by vm.trackedChannels.collectAsState()
    val history by vm.history.collectAsState()
    var query by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Ink, surface = Panel, primary = Cyan,
            onBackground = White, onSurface = White, secondary = Color(0xFF9A8CFF)
        )
    ) {
        AnimatedContent(
            targetState = showSettings,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "screen-transition"
        ) { settings ->
            if (settings) {
                SettingsScreen(
                    currentApiKey = vm.apiKey,
                    autoRefresh = prefs.getBoolean("auto_refresh", false),
                    onSave = { apiKey, enabled ->
                        vm.setApiKey(apiKey)
                        prefs.edit().putBoolean("auto_refresh", enabled).apply()
                        if (enabled) RefreshScheduler.enable(context) else RefreshScheduler.disable(context)
                        showSettings = false
                    },
                    onBack = { showSettings = false }
                )
            } else {
                Dashboard(
                    query = query,
                    onQuery = { query = it },
                    state = state,
                    tracked = tracked,
                    history = history,
                    onSearch = { vm.loadChannel(query) },
                    onRefresh = { vm.refresh() },
                    onSettings = { showSettings = true },
                    onOpenTracked = { channel -> query = channel.handle?.let { "@$it" } ?: channel.channelId; vm.openTracked(channel) },
                    onRemoveTracked = vm::removeTracked
                )
            }
        }
    }
}

@Composable
private fun Dashboard(
    query: String,
    onQuery: (String) -> Unit,
    state: TrackerUiState,
    tracked: List<TrackedChannel>,
    history: List<ChannelSnapshot>,
    onSearch: () -> Unit,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
    onOpenTracked: (TrackedChannel) -> Unit,
    onRemoveTracked: (String) -> Unit
) {
    val focus = LocalFocusManager.current
    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF101A2A), Ink, Ink)))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp)
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
        Text("Public YouTube analytics. One clear view.", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(20.dp))

        Row(
            Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(18.dp))
                .border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(18.dp)).padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Search, null, tint = Muted, modifier = Modifier.padding(start = 10.dp))
            TextField(
                value = query, onValueChange = onQuery,
                placeholder = { Text("Channel URL, channel ID, or @handle", color = Muted, fontSize = 12.sp) },
                modifier = Modifier.weight(1f), singleLine = true,
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
        AnimatedContent(targetState = state, label = "dashboard-state") { current ->
            when (current) {
                is TrackerUiState.Loading -> LoadingPanel()
                is TrackerUiState.Error -> ErrorPanel(current.message)
                is TrackerUiState.Ready -> StatsContent(current.stats, history, onRefresh)
                TrackerUiState.Idle -> WelcomePanel()
            }
        }

        if (tracked.isNotEmpty()) {
            Spacer(Modifier.height(28.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.TrendingUp, null, tint = Cyan, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("YOUR TRACKED CHANNELS", color = White, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                Spacer(Modifier.weight(1f))
                Text("${tracked.size}", color = Muted, fontSize = 11.sp)
            }
            Spacer(Modifier.height(10.dp))
            tracked.forEach { channel ->
                TrackedChannelRow(channel, onClick = { onOpenTracked(channel) }, onRemove = { onRemoveTracked(channel.channelId) })
                Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.height(22.dp))
        Text("PUBLIC DATA ONLY  •  NO HIDDEN METRICS", color = Muted, fontSize = 9.sp, letterSpacing = 1.3.sp)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun TrackedChannelRow(channel: TrackedChannel, onClick: () -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp))
            .border(1.dp, Color.White.copy(alpha = .05f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(36.dp).background(Cyan.copy(alpha = .12f), CircleShape), Alignment.Center) {
            Text(channel.title.take(1).uppercase(), color = Cyan, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(channel.title, color = White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
            Text("${channel.subscriberCount?.formatCount() ?: "Hidden"} subscribers · ${channel.videoCount.formatCount()} videos",
                color = Muted, fontSize = 10.sp)
        }
        TextButton(onClick = onRemove, contentPadding = PaddingValues(horizontal = 8.dp)) {
            Text("Remove", color = Muted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun WelcomePanel() {
    Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(22.dp)).padding(20.dp)) {
        Text("Your dashboard starts here", color = White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))
        Text("Search any public YouTube channel. Save it to your list and revisit its latest public stats whenever you need them.",
            color = Muted, fontSize = 13.sp, lineHeight = 20.sp)
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(Cyan, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text("Subscriber counts stay hidden when YouTube hides them.", color = Cyan, fontSize = 11.sp)
        }
    }
}

@Composable
private fun LoadingPanel() {
    Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(22.dp)).padding(22.dp)) {
        LinearProgressIndicator(Modifier.fillMaxWidth(), color = Cyan, trackColor = Panel2)
        Spacer(Modifier.height(14.dp))
        Text("Loading public channel statistics…", color = White, fontWeight = FontWeight.Medium)
        Text("Likes are checked separately so the main dashboard can load first.", color = Muted, fontSize = 12.sp)
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
private fun StatsContent(stats: ChannelStats, history: List<ChannelSnapshot>, onRefresh: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stats.title, color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(stats.handle?.let { if (it.startsWith("@")) it else "@$it" } ?: stats.channelId, color = Muted, fontSize = 12.sp)
            }
            IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Refresh", tint = Cyan) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("SUBSCRIBERS", stats.subscriberCount?.formatCount() ?: "Hidden", "Public count", Modifier.weight(1f))
            StatCard("VIDEOS", stats.videoCount.formatCount(), "Published total", Modifier.weight(1f))
        }
        StatCard("TOTAL VIEWS", stats.viewCount.formatCount(), "Public channel views", Modifier.fillMaxWidth())
        StatCard("PUBLIC VIDEO LIKES", stats.likesTotal?.formatCount() ?: "Scanning later",
            stats.likesStatus ?: "Separate scan · public counts only", Modifier.fillMaxWidth())
        HistoryCard(history)
        Text("Last updated: ${stats.updatedAt}", color = Muted, fontSize = 10.sp)
        Text("Likes are a sum of individual public video like counts, not an official channel-wide YouTube metric. Partial scans are labelled.",
            color = Muted, fontSize = 11.sp, lineHeight = 17.sp)
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
private fun HistoryCard(history: List<ChannelSnapshot>) {
    Column(
        Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(20.dp))
            .border(1.dp, Color.White.copy(alpha = .06f), RoundedCornerShape(20.dp)).padding(17.dp)
    ) {
        Text("VIEW HISTORY", color = Muted, fontSize = 9.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        if (history.size < 2) {
            Text("Your trend line will appear after another refresh.", color = Muted, fontSize = 12.sp)
        } else {
            val points = history.reversed()
            val first = points.first().viewCount
            val last = points.last().viewCount
            Canvas(Modifier.fillMaxWidth().height(92.dp)) {
                val values = points.map { it.viewCount.toFloat() }
                val low = values.minOrNull() ?: 0f
                val high = values.maxOrNull() ?: 0f
                val range = (high - low).takeIf { it > 0f } ?: 1f
                val path = Path()
                values.forEachIndexed { index, value ->
                    val x = if (values.size == 1) 0f else size.width * index / (values.size - 1)
                    val y = size.height - ((value - low) / range * (size.height - 12.dp.toPx())) - 6.dp.toPx()
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, color = Cyan, style = Stroke(width = 3.dp.toPx()))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Earlier: ${first.formatCount()}", color = Muted, fontSize = 10.sp)
                Text("Latest: ${last.formatCount()}", color = Cyan, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    currentApiKey: String,
    autoRefresh: Boolean,
    onSave: (String, Boolean) -> Unit,
    onBack: () -> Unit
) {
    var apiKey by remember(currentApiKey) { mutableStateOf(currentApiKey) }
    var refreshEnabled by remember(autoRefresh) { mutableStateOf(autoRefresh) }
    var showKey by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Ink).verticalScroll(rememberScrollState()).padding(22.dp)) {
        Text("Settings", color = White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Use your own YouTube Data API v3 key. Each installation uses its own key and its own Google Cloud quota.",
            color = Muted, fontSize = 13.sp, lineHeight = 20.sp)
        Spacer(Modifier.height(22.dp))
        Text("YOUR YOUTUBE API KEY", color = Cyan, fontSize = 10.sp, letterSpacing = 1.4.sp)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = apiKey, onValueChange = { apiKey = it },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            placeholder = { Text("Paste your API key") },
            visualTransformation = if (showKey) androidx.compose.ui.text.input.VisualTransformation.None
                else androidx.compose.ui.text.input.PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { showKey = !showKey }) {
                    Text(if (showKey) "Hide" else "Show", color = Cyan)
                }
            }
        )
        Spacer(Modifier.height(8.dp))
        Text("Create a key in Google Cloud Console and enable YouTube Data API v3. Restrict the key to that API.",
            color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Automatic refresh", color = White, fontWeight = FontWeight.SemiBold)
                Text("Refresh saved channels about every 6 hours on Wi-Fi or mobile data.",
                    color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
            }
            Switch(checked = refreshEnabled, onCheckedChange = { refreshEnabled = it })
        }
        Spacer(Modifier.height(22.dp))
        Button(onClick = { onSave(apiKey.trim(), refreshEnabled) }, modifier = Modifier.fillMaxWidth(),
            enabled = apiKey.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Ink)) {
            Text("Save settings")
        }
        TextButton(onClick = onBack) { Text("Back", color = Muted) }
    }
}

private fun Long.formatCount(): String = when {
    this >= 1_000_000_000 -> "${"%.2f".format(this / 1_000_000_000.0)}B"
    this >= 1_000_000 -> "${"%.2f".format(this / 1_000_000.0)}M"
    this >= 1_000 -> "${"%.1f".format(this / 1_000.0)}K"
    else -> toString()
}
