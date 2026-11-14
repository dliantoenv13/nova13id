package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.ChatSessionEntity
import com.example.data.local.NovaSettings
import com.example.data.remote.AiModelCatalog
import com.example.data.remote.AiModelOption
import com.example.data.remote.ApiDiagnosticStatus
import com.example.ui.theme.NovaBorder
import com.example.ui.theme.NovaCyanNeon
import com.example.ui.theme.NovaDeepSpace
import com.example.ui.theme.NovaEmerald
import com.example.ui.theme.NovaObsidian
import com.example.ui.theme.NovaRoseAlert
import com.example.ui.theme.NovaSurface
import com.example.ui.theme.NovaSurfaceElevated
import com.example.ui.theme.NovaTealGlow
import com.example.ui.theme.NovaTextPrimary
import com.example.ui.theme.NovaTextSecondary

data class PermissionSummary(
    val hasMic: Boolean,
    val hasOverlay: Boolean,
    val hasUsageAccess: Boolean,
    val hasNotifications: Boolean
)

@Composable
fun NovaNavigationDrawerSheet(
    sessions: List<ChatSessionEntity>,
    activeSessionId: Long?,
    settings: NovaSettings,
    permissions: PermissionSummary,
    isServiceRunning: Boolean,
    apiDiagnostics: ApiDiagnosticStatus,
    isTestingApi: Boolean,
    onTestApiConnections: () -> Unit,
    onNewChat: () -> Unit,
    onSelectSession: (Long) -> Unit,
    onDeleteSession: (Long) -> Unit,
    onSelectModel: (AiModelOption) -> Unit,
    onToggleFloatingPopup: (Boolean) -> Unit,
    onToggleHomeOnly: (Boolean) -> Unit,
    onToggleWakeWord: (Boolean) -> Unit,
    onToggleAutoSpeak: (Boolean) -> Unit,
    onRequestMicPermission: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onRequestUsageAccess: () -> Unit,
    onRequestNotificationPermission: () -> Unit
) {
    ModalDrawerSheet(
        drawerContainerColor = NovaDeepSpace,
        drawerContentColor = NovaTextPrimary,
        modifier = Modifier.width(320.dp)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxHeight()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = NovaCyanNeon,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "NOVA OS",
                                style = MaterialTheme.typography.titleLarge,
                                color = NovaTextPrimary
                            )
                            Text(
                                text = if (isServiceRunning) "Background Voice Aktif" else "Siap Diaktifkan",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isServiceRunning) NovaEmerald else NovaTextSecondary
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(NovaCyanNeon.copy(alpha = 0.14f))
                        .border(1.dp, NovaCyanNeon.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                        .clickable(onClick = onNewChat)
                        .testTag("new_chat_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        tint = NovaCyanNeon,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Chat Baru",
                        style = MaterialTheme.typography.titleMedium,
                        color = NovaCyanNeon
                    )
                }
            }

            item {
                DrawerSectionHeader(
                    icon = Icons.Rounded.Memory,
                    title = "Model Open-Source Gratis"
                )
            }

            items(AiModelCatalog.ALL_MODELS, key = { it.id }) { model ->
                val selected = model.id == settings.selectedModelId
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) NovaSurfaceElevated else NovaSurface.copy(alpha = 0.6f))
                        .border(
                            width = 1.dp,
                            color = if (selected) NovaCyanNeon else NovaBorder.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { onSelectModel(model) }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .testTag("model_option_${model.provider.name.lowercase()}")
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = model.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (selected) NovaCyanNeon else NovaTextPrimary
                        )
                        Text(
                            text = model.description,
                            style = MaterialTheme.typography.labelSmall,
                            color = NovaTextSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (selected) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "Model terpilih",
                            tint = NovaCyanNeon,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NovaSurface, RoundedCornerShape(14.dp))
                        .border(1.dp, NovaBorder.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Status API Cloud",
                        style = MaterialTheme.typography.labelMedium,
                        color = NovaCyanNeon
                    )
                    Text(
                        text = "OpenRouter: ${apiDiagnostics.openRouterStatus}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (apiDiagnostics.openRouterStatus.startsWith("Terhubung")) NovaEmerald else NovaTextSecondary
                    )
                    Text(
                        text = "Groq: ${apiDiagnostics.groqStatus}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (apiDiagnostics.groqStatus.startsWith("Terhubung")) NovaEmerald else NovaTextSecondary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(NovaCyanNeon.copy(alpha = 0.14f))
                            .border(1.dp, NovaCyanNeon.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                            .clickable(enabled = !isTestingApi, onClick = onTestApiConnections)
                            .testTag("test_api_connection_button")
                    ) {
                        Text(
                            text = if (isTestingApi) "Menguji Koneksi API…" else "Tes Koneksi OpenRouter & Groq",
                            style = MaterialTheme.typography.labelMedium,
                            color = NovaCyanNeon
                        )
                    }
                }
            }

            item {
                HorizontalDivider(color = NovaBorder.copy(alpha = 0.45f))
                Spacer(modifier = Modifier.height(6.dp))
                DrawerSectionHeader(
                    icon = Icons.Rounded.Layers,
                    title = "Kontrol Popup & Hi Nova"
                )
            }

            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NovaSurface, RoundedCornerShape(14.dp))
                        .border(1.dp, NovaBorder.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    DrawerToggleRow(
                        label = "Popup Dynamic Island",
                        subtitle = "Muncul di area kamera saat minimize",
                        checked = settings.floatingPopupEnabled,
                        onCheckedChange = onToggleFloatingPopup,
                        tag = "toggle_floating_popup"
                    )
                    DrawerToggleRow(
                        label = "Hanya di Layar Utama",
                        subtitle = "Sembunyi otomatis di aplikasi lain",
                        checked = settings.homeScreenOnlyMode,
                        onCheckedChange = onToggleHomeOnly,
                        tag = "toggle_home_only"
                    )
                    DrawerToggleRow(
                        label = "Deteksi \"Hi Nova\" Permanen",
                        subtitle = "Aktif mendengar di latar belakang",
                        checked = settings.wakeWordEnabled,
                        onCheckedChange = onToggleWakeWord,
                        tag = "toggle_wake_word"
                    )
                    DrawerToggleRow(
                        label = "Suara Balasan (TTS)",
                        subtitle = "Nova menjawab lewat speaker",
                        checked = settings.autoSpeakEnabled,
                        onCheckedChange = onToggleAutoSpeak,
                        tag = "toggle_auto_speak"
                    )
                }
            }

            if (!permissions.hasMic || !permissions.hasOverlay || !permissions.hasUsageAccess || !permissions.hasNotifications) {
                item {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(NovaSurfaceElevated.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                            .border(1.dp, NovaTealGlow.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Izin Diperlukan",
                            style = MaterialTheme.typography.labelMedium,
                            color = NovaTealGlow
                        )
                        if (!permissions.hasMic) {
                            PermissionActionChip("Aktifkan Izin Mikrofon", onRequestMicPermission)
                        }
                        if (!permissions.hasOverlay) {
                            PermissionActionChip("Izinkan Popup Mengambang (Overlay)", onRequestOverlayPermission)
                        }
                        if (!permissions.hasUsageAccess) {
                            PermissionActionChip("Izinkan Deteksi Layar Utama (Usage)", onRequestUsageAccess)
                        }
                        if (!permissions.hasNotifications) {
                            PermissionActionChip("Aktifkan Notifikasi Layanan", onRequestNotificationPermission)
                        }
                    }
                }
            }

            item {
                HorizontalDivider(color = NovaBorder.copy(alpha = 0.45f))
                Spacer(modifier = Modifier.height(6.dp))
                DrawerSectionHeader(
                    icon = Icons.Rounded.ChatBubbleOutline,
                    title = "Riwayat Chat Lokal"
                )
            }

            if (sessions.isEmpty()) {
                item {
                    Text(
                        text = "Belum ada riwayat percakapan.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NovaTextSecondary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            } else {
                items(sessions, key = { it.id }) { session ->
                    val isActive = session.id == activeSessionId
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isActive) NovaSurfaceElevated else NovaSurface.copy(alpha = 0.45f))
                            .border(
                                width = 1.dp,
                                color = if (isActive) NovaCyanNeon.copy(alpha = 0.65f) else NovaBorder.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onSelectSession(session.id) }
                            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
                    ) {
                        Text(
                            text = session.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isActive) NovaCyanNeon else NovaTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { onDeleteSession(session.id) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = "Hapus riwayat",
                                tint = NovaTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun DrawerSectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = NovaCyanNeon,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = NovaTextSecondary
        )
    }
}

@Composable
private fun DrawerToggleRow(
    label: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = NovaTextPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = NovaTextSecondary
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag),
            colors = SwitchDefaults.colors(
                checkedThumbColor = NovaObsidian,
                checkedTrackColor = NovaCyanNeon,
                uncheckedThumbColor = NovaTextSecondary,
                uncheckedTrackColor = NovaSurfaceElevated
            )
        )
    }
}

@Composable
private fun PermissionActionChip(
    label: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(10.dp))
            .background(NovaRoseAlert.copy(alpha = 0.14f))
            .border(1.dp, NovaRoseAlert.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.Mic,
            contentDescription = null,
            tint = NovaRoseAlert,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = NovaTextPrimary
        )
    }
}
