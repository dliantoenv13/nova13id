package com.example.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.NovaContainer
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ChatSessionEntity
import com.example.data.local.NovaSettings
import com.example.data.remote.AiModelCatalog
import com.example.data.remote.AiModelOption
import com.example.data.remote.ApiDiagnosticStatus
import com.example.service.NovaBackgroundService
import com.example.ui.components.OrbVisualMode
import com.example.voice.NovaVoiceManager
import com.example.voice.VoiceListeningMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NovaUiState(
    val inputText: String = "",
    val activeSessionId: Long? = null,
    val isThinking: Boolean = false,
    val isListeningVoice: Boolean = false,
    val isSpeaking: Boolean = false,
    val rmsLevel: Float = 0f,
    val liveVoiceTranscript: String = "",
    val statusBanner: String? = null,
    val apiDiagnostics: ApiDiagnosticStatus = ApiDiagnosticStatus(),
    val isTestingApi: Boolean = false
) {
    val orbMode: OrbVisualMode
        get() = when {
            isListeningVoice -> OrbVisualMode.LISTENING
            isThinking -> OrbVisualMode.THINKING
            isSpeaking -> OrbVisualMode.SPEAKING
            else -> OrbVisualMode.IDLE
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
class NovaViewModel(application: Application) : AndroidViewModel(application) {

    private val container = NovaContainer.get(application)
    private val chatRepo = container.chatRepository
    private val prefsRepo = container.preferencesRepository
    private val aiRepo = container.aiRepository

    private val _uiState = MutableStateFlow(NovaUiState())
    val uiState: StateFlow<NovaUiState> = _uiState.asStateFlow()

    val settings: StateFlow<NovaSettings> = prefsRepo.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = NovaSettings()
    )

    val sessions: StateFlow<List<ChatSessionEntity>> = chatRepo.allSessions.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val activeSessionIdFlow = MutableStateFlow<Long?>(null)

    val messages: StateFlow<List<ChatMessageEntity>> = activeSessionIdFlow
        .flatMapLatest { sessionId ->
            if (sessionId == null) flowOf(emptyList()) else chatRepo.observeMessages(sessionId)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val voiceManager: NovaVoiceManager = NovaVoiceManager(
        context = application,
        onModeChanged = { mode ->
            _uiState.value = _uiState.value.copy(
                isListeningVoice = mode == VoiceListeningMode.DIRECT_COMMAND,
                liveVoiceTranscript = if (mode == VoiceListeningMode.IDLE) "" else _uiState.value.liveVoiceTranscript
            )
        },
        onRmsChanged = { rms ->
            _uiState.value = _uiState.value.copy(rmsLevel = rms)
        },
        onPartialTranscript = { partial ->
            _uiState.value = _uiState.value.copy(liveVoiceTranscript = partial)
        },
        onCommandRecognized = { spokenText ->
            sendMessage(textOverride = spokenText, fromVoice = true)
        },
        onSpeakingStateChanged = { speaking ->
            _uiState.value = _uiState.value.copy(isSpeaking = speaking)
        },
        onError = { err ->
            _uiState.value = _uiState.value.copy(
                isListeningVoice = false,
                statusBanner = err
            )
        }
    )

    init {
        _uiState.value = _uiState.value.copy(apiDiagnostics = aiRepo.currentKeyPresence())
        viewModelScope.launch {
            val latest = chatRepo.getOrCreateLatestSession(settings.value.selectedModelId)
            setActiveSession(latest.id)
        }
    }

    fun testApiConnections() {
        if (_uiState.value.isTestingApi) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTestingApi = true)
            try {
                val report = aiRepo.testApiConnections()
                _uiState.value = _uiState.value.copy(apiDiagnostics = report)
            } finally {
                _uiState.value = _uiState.value.copy(isTestingApi = false)
            }
        }
    }

    fun setActiveSession(sessionId: Long) {
        activeSessionIdFlow.value = sessionId
        _uiState.value = _uiState.value.copy(activeSessionId = sessionId, statusBanner = null)
    }

    fun startNewChat() {
        viewModelScope.launch {
            val session = chatRepo.createNewSession(settings.value.selectedModelId)
            setActiveSession(session.id)
        }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            chatRepo.deleteSession(sessionId)
            if (activeSessionIdFlow.value == sessionId) {
                val next = chatRepo.getOrCreateLatestSession(settings.value.selectedModelId)
                setActiveSession(next.id)
            }
        }
    }

    fun updateInputText(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text, statusBanner = null)
    }

    fun clearStatusBanner() {
        _uiState.value = _uiState.value.copy(statusBanner = null)
    }

    fun sendMessage(textOverride: String? = null, fromVoice: Boolean = false) {
        val raw = (textOverride ?: _uiState.value.inputText).trim()
        if (raw.isEmpty() || _uiState.value.isThinking) return

        if (textOverride == null) {
            _uiState.value = _uiState.value.copy(inputText = "", statusBanner = null)
        } else {
            _uiState.value = _uiState.value.copy(statusBanner = null)
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isThinking = true)
            try {
                val model = AiModelCatalog.findById(settings.value.selectedModelId)
                val sessionId = activeSessionIdFlow.value
                    ?: chatRepo.createNewSession(model.id).id.also { setActiveSession(it) }

                val history = chatRepo.getRecentContext(sessionId, limit = 8)
                chatRepo.appendMessage(
                    sessionId = sessionId,
                    role = "user",
                    content = raw,
                    modelUsed = model.shortBadge,
                    source = if (fromVoice) "floating_voice" else "main_ui"
                )

                val result = aiRepo.generateReply(
                    userPrompt = raw,
                    history = history,
                    modelOption = model,
                    isVoiceMode = fromVoice
                )

                chatRepo.appendMessage(
                    sessionId = sessionId,
                    role = "assistant",
                    content = result.content,
                    modelUsed = result.usedModelLabel,
                    source = if (fromVoice) "floating_voice" else "main_ui"
                )

                if (fromVoice && settings.value.autoSpeakEnabled) {
                    voiceManager.speak(result.content)
                }
            } finally {
                _uiState.value = _uiState.value.copy(isThinking = false)
            }
        }
    }

    fun toggleVoiceRecording() {
        if (_uiState.value.isListeningVoice) {
            voiceManager.stopListening()
        } else {
            voiceManager.startDirectListening(keepWakeLoopAfter = false)
        }
    }

    fun speakMessage(content: String) {
        if (_uiState.value.isSpeaking) {
            voiceManager.stopSpeaking()
        } else {
            voiceManager.speak(content)
        }
    }

    fun selectModel(option: AiModelOption) {
        viewModelScope.launch {
            prefsRepo.setSelectedModel(option.id)
        }
    }

    fun setFloatingPopupEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setFloatingPopupEnabled(enabled)
            syncBackgroundServiceState(popup = enabled, wake = settings.value.wakeWordEnabled)
        }
    }

    fun setHomeScreenOnlyMode(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setHomeScreenOnlyMode(enabled)
        }
    }

    fun setWakeWordEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setWakeWordEnabled(enabled)
            syncBackgroundServiceState(popup = settings.value.floatingPopupEnabled, wake = enabled)
        }
    }

    fun setAutoSpeakEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setAutoSpeakEnabled(enabled)
        }
    }

    fun syncBackgroundServiceState(popup: Boolean = settings.value.floatingPopupEnabled, wake: Boolean = settings.value.wakeWordEnabled) {
        val ctx = getApplication<Application>()
        val hasMic = ContextCompat.checkSelfPermission(
            ctx,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        val canStartMicFgs = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || hasMic
        if ((popup || wake) && canStartMicFgs) {
            runCatching { NovaBackgroundService.start(ctx) }
        } else {
            runCatching { NovaBackgroundService.stop(ctx) }
        }
    }

    override fun onCleared() {
        voiceManager.release()
        super.onCleared()
    }
}
