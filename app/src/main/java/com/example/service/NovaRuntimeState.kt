package com.example.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class IslandMode {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING
}

data class NovaServiceSnapshot(
    val isServiceRunning: Boolean = false,
    val isOverlayShowing: Boolean = false,
    val isOnHomeScreen: Boolean = false,
    val islandMode: IslandMode = IslandMode.IDLE,
    val liveTranscript: String = "",
    val lastReplySnippet: String = "",
    val rmsLevel: Float = 0f
)

object NovaRuntimeState {
    private val _isMainActivityForeground = MutableStateFlow(false)
    val isMainActivityForeground: StateFlow<Boolean> = _isMainActivityForeground.asStateFlow()

    private val _snapshot = MutableStateFlow(NovaServiceSnapshot())
    val snapshot: StateFlow<NovaServiceSnapshot> = _snapshot.asStateFlow()

    fun setMainActivityForeground(foreground: Boolean) {
        _isMainActivityForeground.value = foreground
    }

    fun updateSnapshot(transform: (NovaServiceSnapshot) -> NovaServiceSnapshot) {
        _snapshot.value = transform(_snapshot.value)
    }
}
