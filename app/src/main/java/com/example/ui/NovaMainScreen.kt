package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.remote.AiModelCatalog
import com.example.service.NovaRuntimeState
import com.example.ui.components.AtomicParticleOrb
import com.example.ui.components.BottomVoiceBar
import com.example.ui.components.ChatMessageList
import com.example.ui.components.NovaNavigationDrawerSheet
import com.example.ui.components.OrbVisualMode
import com.example.ui.components.PermissionSummary
import com.example.ui.theme.NovaBorder
import com.example.ui.theme.NovaCyanNeon
import com.example.ui.theme.NovaDeepSpace
import com.example.ui.theme.NovaEmerald
import com.example.ui.theme.NovaObsidian
import com.example.ui.theme.NovaRoseAlert
import com.example.ui.theme.NovaSkyPlasma
import com.example.ui.theme.NovaSurface
import com.example.ui.theme.NovaSurfaceElevated
import com.example.ui.theme.NovaTealGlow
import com.example.ui.theme.NovaTextPrimary
import com.example.ui.theme.NovaTextSecondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NovaMainScreen(
    viewModel: NovaViewModel,
    permissions: PermissionSummary,
    onRequestMic: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestUsageAccess: () -> Unit,
    onRequestNotifications: () -> Unit,
    onMinimizeToHome: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val serviceSnapshot by NovaRuntimeState.snapshot.collectAsStateWithLifecycle()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val activeModel = AiModelCatalog.findById(settings.selectedModelId)
    var overlayPromptDismissed by rememberSaveable { mutableStateOf(false) }

    if (permissions.hasMic && !permissions.hasOverlay && settings.floatingPopupEnabled && !overlayPromptDismissed) {
        AlertDialog(
            onDismissRequest = { overlayPromptDismissed = true },
            containerColor = NovaSurface,
            titleContentColor = NovaTextPrimary,
            textContentColor = NovaTextSecondary,
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Layers,
                    contentDescription = null,
                    tint = NovaCyanNeon
                )
            },
            title = {
                Text(
                    text = "Aktifkan Popup Dynamic Island",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = "Agar kapsul mengambang Nova muncul di area kamera depan saat Anda berada di Home Screen, aktifkan izin \"Tampilkan di atas aplikasi lain\".",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        overlayPromptDismissed = true
                        onRequestOverlay()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NovaCyanNeon,
                        contentColor = NovaObsidian
                    )
                ) {
                    Text("Izinkan Sekarang")
                }
            },
            dismissButton = {
                TextButton(onClick = { overlayPromptDismissed = true }) {
                    Text("Nanti", color = NovaTextSecondary)
                }
            }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        drawerContent = {
            NovaNavigationDrawerSheet(
                sessions = sessions,
                activeSessionId = uiState.activeSessionId,
                settings = settings,
                permissions = permissions,
                isServiceRunning = serviceSnapshot.isServiceRunning,
                apiDiagnostics = uiState.apiDiagnostics,
                isTestingApi = uiState.isTestingApi,
                onTestApiConnections = { viewModel.testApiConnections() },
                onNewChat = {
                    viewModel.startNewChat()
                    scope.launch { drawerState.close() }
                },
                onSelectSession = { sessionId ->
                    viewModel.setActiveSession(sessionId)
                    scope.launch { drawerState.close() }
                },
                onDeleteSession = { sessionId ->
                    viewModel.deleteSession(sessionId)
                },
                onSelectModel = { model ->
                    viewModel.selectModel(model)
                },
                onToggleFloatingPopup = { enabled ->
                    if (enabled && !permissions.hasOverlay) {
                        onRequestOverlay()
                    }
                    viewModel.setFloatingPopupEnabled(enabled)
                },
                onToggleHomeOnly = { enabled ->
                    if (enabled && !permissions.hasUsageAccess) {
                        onRequestUsageAccess()
                    }
                    viewModel.setHomeScreenOnlyMode(enabled)
                },
                onToggleWakeWord = { enabled ->
                    if (enabled && !permissions.hasMic) {
                        onRequestMic()
                    }
                    viewModel.setWakeWordEnabled(enabled)
                },
                onToggleAutoSpeak = { enabled ->
                    viewModel.setAutoSpeakEnabled(enabled)
                },
                onRequestMicPermission = onRequestMic,
                onRequestOverlayPermission = onRequestOverlay,
                onRequestUsageAccess = onRequestUsageAccess,
                onRequestNotificationPermission = onRequestNotifications
            )
        }
    ) {
        Scaffold(
            containerColor = NovaObsidian,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                NovaTopBar(
                    modelBadge = activeModel.shortBadge,
                    isServiceRunning = serviceSnapshot.isServiceRunning,
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onNewChatClick = { viewModel.startNewChat() },
                    onModelBadgeClick = { scope.launch { drawerState.open() } }
                )
            },
            bottomBar = {
                BottomVoiceBar(
                    inputText = uiState.inputText,
                    isListeningVoice = uiState.isListeningVoice,
                    isThinking = uiState.isThinking,
                    liveVoiceTranscript = uiState.liveVoiceTranscript,
                    onInputChange = { viewModel.updateInputText(it) },
                    onSendClick = { viewModel.sendMessage() },
                    onRecordVoiceClick = {
                        if (!permissions.hasMic) {
                            onRequestMic()
                        } else {
                            viewModel.toggleVoiceRecording()
                        }
                    }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(NovaObsidian, NovaDeepSpace, NovaObsidian)
                        )
                    )
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 680.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AnimatedVisibility(visible = uiState.statusBanner != null) {
                        uiState.statusBanner?.let { banner ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(NovaRoseAlert.copy(alpha = 0.14f))
                                    .border(1.dp, NovaRoseAlert.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                                    .clickable { viewModel.clearStatusBanner() }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = banner,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = NovaTextPrimary
                                )
                            }
                        }
                    }

                    if (messages.isEmpty()) {
                        // Hero screen with interactive atomic particle sphere in the center
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            AtomicParticleOrb(
                                mode = uiState.orbMode,
                                rmsLevel = uiState.rmsLevel,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(270.dp)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = when (uiState.orbMode) {
                                    OrbVisualMode.LISTENING -> "Mendengarkan suara Anda…"
                                    OrbVisualMode.THINKING -> "Nova sedang berpikir…"
                                    OrbVisualMode.SPEAKING -> "Nova berbicara…"
                                    OrbVisualMode.IDLE -> "Sentuh bola atom atau mulai obrolan"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = NovaTextPrimary,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "Model aktif: ${activeModel.displayName} · \"Hi Nova\" ${if (settings.wakeWordEnabled) "siap" else "nonaktif"}",
                                style = MaterialTheme.typography.labelMedium,
                                color = NovaTextSecondary,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(14.dp))
                            if (!permissions.hasOverlay || !permissions.hasMic) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(NovaSurfaceElevated.copy(alpha = 0.85f))
                                        .border(1.dp, NovaCyanNeon.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                                        .clickable {
                                            if (!permissions.hasMic) onRequestMic()
                                            else onRequestOverlay()
                                        }
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                        .testTag("setup_popup_permissions_chip")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Layers,
                                        contentDescription = null,
                                        tint = NovaCyanNeon,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (!permissions.hasMic) {
                                            "Aktifkan izin Mikrofon & Hi Nova"
                                        } else {
                                            "Aktifkan Izin Popup Mengambang (Overlay)"
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = NovaCyanNeon
                                    )
                                }
                            } else {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(NovaCyanNeon.copy(alpha = 0.14f))
                                            .border(1.dp, NovaCyanNeon.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                                            .clickable(onClick = onMinimizeToHome)
                                            .padding(horizontal = 14.dp, vertical = 9.dp)
                                            .testTag("minimize_to_home_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.OpenInNew,
                                            contentDescription = null,
                                            tint = NovaCyanNeon,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Minimize ke Home (Tes Popup)",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = NovaCyanNeon
                                        )
                                    }

                                    if (!permissions.hasUsageAccess && settings.homeScreenOnlyMode) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(NovaSurfaceElevated.copy(alpha = 0.85f))
                                                .border(1.dp, NovaTealGlow.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                                                .clickable(onClick = onRequestUsageAccess)
                                                .padding(horizontal = 14.dp, vertical = 9.dp)
                                                .testTag("grant_usage_access_chip")
                                        ) {
                                            Text(
                                                text = "Izinkan Deteksi Home Screen",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = NovaTealGlow
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                StarterPromptChip("Jam berapa sekarang?") {
                                    viewModel.sendMessage("Jam berapa sekarang?")
                                }
                                StarterPromptChip("Jelaskan cara kerja LLM open-source") {
                                    viewModel.sendMessage("Jelaskan secara singkat cara kerja LLM open-source")
                                }
                                StarterPromptChip("Beri 3 ide proyek seru") {
                                    viewModel.sendMessage("Beri 3 ide proyek teknologi seru untuk pemula")
                                }
                            }
                        }
                    } else {
                        ChatMessageList(
                            messages = messages,
                            isThinking = uiState.isThinking,
                            onSpeakMessage = { viewModel.speakMessage(it) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NovaTopBar(
    modelBadge: String,
    isServiceRunning: Boolean,
    onMenuClick: () -> Unit,
    onNewChatClick: () -> Unit,
    onModelBadgeClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        IconButton(
            onClick = onMenuClick,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("menu_drawer_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.Menu,
                contentDescription = stringResource(R.string.menu_description),
                tint = NovaTextPrimary,
                modifier = Modifier.size(26.dp)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .clickable(onClick = onModelBadgeClick)
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .testTag("nova_header_title")
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = NovaCyanNeon,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.nova_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = NovaTextPrimary
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .background(NovaSurfaceElevated, RoundedCornerShape(10.dp))
                    .border(1.dp, NovaBorder, RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (isServiceRunning) NovaEmerald else NovaSkyPlasma)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = modelBadge,
                    style = MaterialTheme.typography.labelSmall,
                    color = NovaCyanNeon
                )
            }
        }

        IconButton(
            onClick = onNewChatClick,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("top_new_chat_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = stringResource(R.string.new_chat_desc),
                tint = NovaCyanNeon,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun StarterPromptChip(
    text: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(NovaSurface.copy(alpha = 0.88f))
            .border(1.dp, NovaBorder, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = NovaTextSecondary
        )
    }
}
