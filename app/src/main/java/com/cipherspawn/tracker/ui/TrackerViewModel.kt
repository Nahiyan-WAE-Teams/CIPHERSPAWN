package com.cipherspawn.tracker.ui

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cipherspawn.tracker.data.ChannelSnapshot
import com.cipherspawn.tracker.data.ChannelStats
import com.cipherspawn.tracker.data.TrackerDao
import com.cipherspawn.tracker.data.TrackerRepository
import com.cipherspawn.tracker.data.TrackedChannel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface TrackerUiState {
    data object Idle : TrackerUiState
    data object Loading : TrackerUiState
    data class Ready(val stats: ChannelStats) : TrackerUiState
    data class Error(val message: String) : TrackerUiState
}

class TrackerViewModel(
    private val prefs: SharedPreferences,
    private val dao: TrackerDao
) : ViewModel() {
    private val _state = MutableStateFlow<TrackerUiState>(TrackerUiState.Idle)
    val state: StateFlow<TrackerUiState> = _state
    val trackedChannels = dao.observeTracked().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )
    private val selectedChannelId = MutableStateFlow<String?>(null)
    val history = selectedChannelId.filterNotNull().flatMapLatest { dao.observeSnapshots(it) }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )

    var baseUrl: String = prefs.getString("worker_url", "") ?: ""
        private set
    private var lastQuery: String = ""

    fun setBaseUrl(value: String) {
        baseUrl = value.trim().trimEnd('/')
        prefs.edit().putString("worker_url", baseUrl).apply()
    }

    fun loadChannel(input: String) {
        val query = input.trim()
        if (query.isBlank()) {
            _state.value = TrackerUiState.Error("Paste a channel URL, @handle, or channel ID.")
            return
        }
        if (!baseUrl.startsWith("https://")) {
            _state.value = TrackerUiState.Error("Connect the secure API gateway in Settings first.")
            return
        }
        lastQuery = query
        viewModelScope.launch {
            _state.value = TrackerUiState.Loading
            try {
                val repository = TrackerRepository(baseUrl)
                val stats = repository.fetchChannel(query)
                selectedChannelId.value = stats.channelId
                dao.saveTracked(stats.toEntity())
                dao.addSnapshot(
                    ChannelSnapshot(
                        channelId = stats.channelId,
                        recordedAt = System.currentTimeMillis(),
                        subscriberCount = stats.subscriberCount,
                        viewCount = stats.viewCount,
                        videoCount = stats.videoCount
                    )
                )
                if (lastQuery == query) _state.value = TrackerUiState.Ready(stats)

                // Likes are a separate, quota-expensive scan. Do not block the core channel dashboard.
                runCatching { repository.fetchLikes(stats.channelId) }
                    .onSuccess { likes ->
                        val enriched = stats.copy(likesTotal = likes.total, likesStatus = likes.status)
                        dao.saveTracked(enriched.toEntity())
                        if (lastQuery == query) _state.value = TrackerUiState.Ready(enriched)
                    }
                    .onFailure {
                        val unavailable = stats.copy(likesStatus = "Likes scan unavailable · channel stats are unaffected")
                        dao.saveTracked(unavailable.toEntity())
                        if (lastQuery == query) _state.value = TrackerUiState.Ready(unavailable)
                    }
            } catch (e: Exception) {
                if (lastQuery == query) {
                    _state.value = TrackerUiState.Error(
                        e.message ?: "Could not load this channel. Check your connection and try again."
                    )
                }
            }
        }
    }

    fun openTracked(channel: TrackedChannel) {
        lastQuery = channel.channelId
        selectedChannelId.value = channel.channelId
        _state.value = TrackerUiState.Ready(channel.toStats())
    }

    fun removeTracked(channelId: String) {
        viewModelScope.launch { dao.removeTracked(channelId) }
    }

    fun refresh() {
        if (lastQuery.isNotBlank()) loadChannel(lastQuery)
    }

    private fun ChannelStats.toEntity() = TrackedChannel(
        channelId, title, handle, subscriberCount, videoCount, viewCount,
        likesTotal, likesStatus, updatedAt
    )

    private fun TrackedChannel.toStats() = ChannelStats(
        channelId, title, handle, subscriberCount, videoCount, viewCount,
        likesTotal, likesStatus, updatedAt
    )
}

class TrackerViewModelFactory(
    private val prefs: SharedPreferences,
    private val dao: TrackerDao
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TrackerViewModel::class.java)) {
            return TrackerViewModel(prefs, dao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel")
    }
}
