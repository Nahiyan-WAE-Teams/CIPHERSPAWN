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
                val stats = TrackerRepository(baseUrl).fetchChannel(query)
                dao.saveTracked(
                    TrackedChannel(
                        channelId = stats.channelId,
                        title = stats.title,
                        handle = stats.handle,
                        subscriberCount = stats.subscriberCount,
                        videoCount = stats.videoCount,
                        viewCount = stats.viewCount,
                        likesTotal = stats.likesTotal,
                        likesStatus = stats.likesStatus,
                        updatedAt = stats.updatedAt
                    )
                )
                dao.addSnapshot(
                    ChannelSnapshot(
                        channelId = stats.channelId,
                        recordedAt = System.currentTimeMillis(),
                        subscriberCount = stats.subscriberCount,
                        viewCount = stats.viewCount,
                        videoCount = stats.videoCount
                    )
                )
                _state.value = TrackerUiState.Ready(stats)
            } catch (e: Exception) {
                _state.value = TrackerUiState.Error(
                    e.message ?: "Could not load this channel. Check your connection and try again."
                )
            }
        }
    }

    fun openTracked(channel: TrackedChannel) {
        lastQuery = channel.channelId
        _state.value = TrackerUiState.Ready(
            ChannelStats(
                channelId = channel.channelId,
                title = channel.title,
                handle = channel.handle,
                subscriberCount = channel.subscriberCount,
                videoCount = channel.videoCount,
                viewCount = channel.viewCount,
                likesTotal = channel.likesTotal,
                likesStatus = channel.likesStatus,
                updatedAt = channel.updatedAt
            )
        )
    }

    fun removeTracked(channelId: String) {
        viewModelScope.launch { dao.removeTracked(channelId) }
    }

    fun refresh() {
        if (lastQuery.isNotBlank()) loadChannel(lastQuery)
    }
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
