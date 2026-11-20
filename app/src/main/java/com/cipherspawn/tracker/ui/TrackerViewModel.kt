package com.cipherspawn.tracker.ui

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cipherspawn.tracker.data.ChannelStats
import com.cipherspawn.tracker.data.TrackerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface TrackerUiState {
    data object Idle : TrackerUiState
    data object Loading : TrackerUiState
    data class Ready(val stats: ChannelStats) : TrackerUiState
    data class Error(val message: String) : TrackerUiState
}

class TrackerViewModel(private val prefs: SharedPreferences) : ViewModel() {
    private val _state = MutableStateFlow<TrackerUiState>(TrackerUiState.Idle)
    val state: StateFlow<TrackerUiState> = _state
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
        if (baseUrl.isBlank()) {
            _state.value = TrackerUiState.Error("Connect the API gateway first: open Settings and add your deployed Worker URL.")
            return
        }
        lastQuery = query
        viewModelScope.launch {
            _state.value = TrackerUiState.Loading
            try {
                _state.value = TrackerUiState.Ready(TrackerRepository(baseUrl).fetchChannel(query))
            } catch (e: Exception) {
                _state.value = TrackerUiState.Error(e.message ?: "Something went wrong. Check your connection and try again.")
            }
        }
    }

    fun refresh() {
        if (lastQuery.isNotBlank()) loadChannel(lastQuery)
    }
}

class TrackerViewModelFactory(private val prefs: SharedPreferences) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TrackerViewModel::class.java)) return TrackerViewModel(prefs) as T
        throw IllegalArgumentException("Unknown ViewModel")
    }
}
