package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.service.HomeScreenDetector
import com.example.service.NovaRuntimeState
import com.example.ui.NovaMainScreen
import com.example.ui.NovaViewModel
import com.example.ui.components.PermissionSummary
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NovaObsidian

class MainActivity : ComponentActivity() {

    private val viewModel: NovaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(NovaObsidian.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(NovaObsidian.toArgb())
        )

        setContent {
            val homeDetector = remember { HomeScreenDetector(this) }
            var permissions by remember { mutableStateOf(readPermissions(homeDetector)) }

            val micLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { granted ->
                permissions = readPermissions(homeDetector)
                if (granted) {
                    viewModel.syncBackgroundServiceState()
                }
            }

            val notificationLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) {
                permissions = readPermissions(homeDetector)
                viewModel.syncBackgroundServiceState()
            }

            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                permissions = readPermissions(homeDetector)
                if (permissions.hasMic || permissions.hasOverlay) {
                    viewModel.syncBackgroundServiceState()
                }
            }

            LaunchedEffect(Unit) {
                if (!permissions.hasMic) {
                    micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                } else {
                    viewModel.syncBackgroundServiceState()
                }
            }

            MyApplicationTheme {
                NovaMainScreen(
                    viewModel = viewModel,
                    permissions = permissions,
                    onRequestMic = {
                        micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onRequestOverlay = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName")
                            )
                            startActivity(intent)
                        }
                    },
                    onRequestUsageAccess = {
                        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    },
                    onRequestNotifications = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onMinimizeToHome = {
                        viewModel.syncBackgroundServiceState()
                        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_HOME)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        startActivity(homeIntent)
                    }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        NovaRuntimeState.setMainActivityForeground(true)
    }

    override fun onStop() {
        NovaRuntimeState.setMainActivityForeground(false)
        super.onStop()
    }

    private fun readPermissions(homeDetector: HomeScreenDetector): PermissionSummary {
        val hasMic = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }

        val hasNotifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        return PermissionSummary(
            hasMic = hasMic,
            hasOverlay = hasOverlay,
            hasUsageAccess = homeDetector.hasUsageStatsPermission(),
            hasNotifications = hasNotifications
        )
    }
}
