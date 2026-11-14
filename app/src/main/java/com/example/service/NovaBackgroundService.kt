package com.example.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.NovaContainer
import com.example.data.local.NovaSettings
import com.example.data.remote.AiModelCatalog
import com.example.voice.NovaVoiceManager
import com.example.voice.VoiceListeningMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class NovaBackgroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var container: NovaContainer
    private lateinit var homeScreenDetector: HomeScreenDetector
    private lateinit var voiceManager: NovaVoiceManager
    private var windowManager: WindowManager? = null

    private var overlayView: LinearLayout? = null
    private var orbDotView: MiniOrbPulseView? = null
    private var pillLabelView: TextView? = null
    private var pillSubLabelView: TextView? = null

    private var currentSettings = NovaSettings()
    private var monitorJob: Job? = null
    private var isProcessingAi = false

    override fun onCreate() {
        super.onCreate()
        container = NovaContainer.get(this)
        homeScreenDetector = HomeScreenDetector(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        voiceManager = NovaVoiceManager(
            context = this,
            onModeChanged = { mode ->
                val islandMode = when (mode) {
                    VoiceListeningMode.DIRECT_COMMAND -> IslandMode.LISTENING
                    VoiceListeningMode.WAKE_WORD_LOOP, VoiceListeningMode.IDLE ->
                        if (isProcessingAi) IslandMode.THINKING else IslandMode.IDLE
                }
                NovaRuntimeState.updateSnapshot { it.copy(islandMode = islandMode) }
                refreshPillVisuals()
            },
            onRmsChanged = { rms ->
                NovaRuntimeState.updateSnapshot { it.copy(rmsLevel = rms) }
                orbDotView?.updateRms(rms)
            },
            onPartialTranscript = { partial ->
                NovaRuntimeState.updateSnapshot { it.copy(liveTranscript = partial) }
                refreshPillVisuals()
            },
            onCommandRecognized = { command ->
                handleBackgroundCommand(command)
            },
            onSpeakingStateChanged = { speaking ->
                val nextMode = when {
                    speaking -> IslandMode.SPEAKING
                    isProcessingAi -> IslandMode.THINKING
                    else -> IslandMode.IDLE
                }
                NovaRuntimeState.updateSnapshot { it.copy(islandMode = nextMode) }
                refreshPillVisuals()
            },
            onError = { err ->
                NovaRuntimeState.updateSnapshot {
                    it.copy(islandMode = IslandMode.IDLE, liveTranscript = err)
                }
                refreshPillVisuals()
                serviceScope.launch {
                    delay(2400L)
                    if (!isProcessingAi) {
                        NovaRuntimeState.updateSnapshot { it.copy(liveTranscript = "") }
                        refreshPillVisuals()
                    }
                }
            }
        )

        createNotificationChannelIfNeeded()
        startForegroundCompat()

        NovaRuntimeState.updateSnapshot { it.copy(isServiceRunning = true) }

        serviceScope.launch {
            container.preferencesRepository.settingsFlow.collectLatest { settings ->
                currentSettings = settings
                syncWakeWordLoop()
                evaluateOverlayVisibility()
                updateNotification()
            }
        }

        serviceScope.launch {
            NovaRuntimeState.isMainActivityForeground.collectLatest {
                syncWakeWordLoop()
                evaluateOverlayVisibility()
            }
        }

        startHomeVisibilityMonitor()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TRIGGER_VOICE -> {
                if (NovaRuntimeState.snapshot.value.islandMode == IslandMode.SPEAKING) {
                    voiceManager.stopSpeaking()
                } else {
                    voiceManager.startDirectListening(keepWakeLoopAfter = currentSettings.wakeWordEnabled)
                }
            }
            ACTION_STOP_SERVICE -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    private fun startForegroundCompat() {
        val notification = buildNotification()
        val hasMic = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && hasMic) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun syncWakeWordLoop() {
        val inMainApp = NovaRuntimeState.isMainActivityForeground.value
        if (!inMainApp && currentSettings.wakeWordEnabled && voiceManager.hasRecordPermission()) {
            voiceManager.startWakeWordLoop()
        } else {
            voiceManager.stopWakeWordLoop()
        }
    }

    private fun startHomeVisibilityMonitor() {
        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            while (isActive) {
                evaluateOverlayVisibility()
                delay(750L)
            }
        }
    }

    private fun canDrawOverlay(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun evaluateOverlayVisibility() {
        val inMainApp = NovaRuntimeState.isMainActivityForeground.value
        val onHome = homeScreenDetector.isCurrentlyOnHomeScreen()
        val hasUsagePerm = homeScreenDetector.hasUsageStatsPermission()
        val snap = NovaRuntimeState.snapshot.value
        val activeVoiceSession = snap.islandMode != IslandMode.IDLE

        val shouldShow = currentSettings.floatingPopupEnabled &&
            canDrawOverlay() &&
            !inMainApp &&
            (activeVoiceSession || !currentSettings.homeScreenOnlyMode || !hasUsagePerm || onHome)

        NovaRuntimeState.updateSnapshot {
            it.copy(isOnHomeScreen = onHome, isOverlayShowing = shouldShow)
        }

        if (shouldShow) {
            showOverlayPill()
            refreshPillVisuals()
        } else {
            hideOverlayPill()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showOverlayPill() {
        if (overlayView != null) return

        val density = resources.displayMetrics.density
        fun dp(value: Float): Int = (value * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10f), dp(6f), dp(12f), dp(6f))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(24f).toFloat()
                setColor(Color.parseColor("#EE070D19"))
                setStroke(dp(1f), Color.parseColor("#8800F0FF"))
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                elevation = dp(10f).toFloat()
            }
        }

        val orbView = MiniOrbPulseView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(18f), dp(18f)).apply {
                marginEnd = dp(8f)
            }
        }

        val textColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleLabel = TextView(this).apply {
            text = getString(R.string.tap_to_talk)
            setTextColor(Color.parseColor("#F1F5F9"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        val subLabel = TextView(this).apply {
            setTextColor(Color.parseColor("#00F0FF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            visibility = View.GONE
        }

        textColumn.addView(titleLabel)
        textColumn.addView(subLabel)
        root.addView(orbView)
        root.addView(textColumn)

        var downTime = 0L
        var startedOnDown = false
        root.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downTime = System.currentTimeMillis()
                    root.animate().scaleX(0.95f).scaleY(0.95f).setDuration(90).start()
                    val mode = NovaRuntimeState.snapshot.value.islandMode
                    if (mode == IslandMode.IDLE) {
                        startedOnDown = true
                        voiceManager.startDirectListening(keepWakeLoopAfter = currentSettings.wakeWordEnabled)
                    } else {
                        startedOnDown = false
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    root.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    val held = System.currentTimeMillis() - downTime
                    if (event.action == MotionEvent.ACTION_UP) {
                        onDynamicIslandReleased(startedOnDown = startedOnDown, heldDurationMs = held)
                    } else if (startedOnDown) {
                        voiceManager.stopListening()
                    }
                    startedOnDown = false
                    true
                }
                else -> false
            }
        }

        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(6f)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        runCatching {
            windowManager?.addView(root, params)
            overlayView = root
            orbDotView = orbView
            pillLabelView = titleLabel
            pillSubLabelView = subLabel
        }
    }

    private fun hideOverlayPill() {
        val view = overlayView ?: return
        runCatching {
            windowManager?.removeView(view)
        }
        overlayView = null
        orbDotView = null
        pillLabelView = null
        pillSubLabelView = null
    }

    private fun onDynamicIslandReleased(startedOnDown: Boolean, heldDurationMs: Long) {
        val snap = NovaRuntimeState.snapshot.value
        if (startedOnDown) {
            if (heldDurationMs > 480L) {
                // Push-and-hold released: stop recording and immediately transcribe
                voiceManager.stopListening()
            }
            return
        }
        if (snap.islandMode == IslandMode.SPEAKING) {
            voiceManager.stopSpeaking()
            return
        }
        if (snap.islandMode == IslandMode.LISTENING) {
            voiceManager.stopListening()
            return
        }
        voiceManager.startDirectListening(keepWakeLoopAfter = currentSettings.wakeWordEnabled)
    }

    private fun refreshPillVisuals() {
        val snap = NovaRuntimeState.snapshot.value
        val title = pillLabelView ?: return
        val sub = pillSubLabelView ?: return
        val orb = orbDotView

        orb?.setMode(snap.islandMode)

        when (snap.islandMode) {
            IslandMode.IDLE -> {
                title.text = if (snap.liveTranscript.isNotBlank()) {
                    snap.liveTranscript
                } else {
                    getString(R.string.tap_to_talk)
                }
                sub.visibility = View.GONE
            }
            IslandMode.LISTENING -> {
                title.text = getString(R.string.listening_status)
                if (snap.liveTranscript.isNotBlank()) {
                    sub.text = snap.liveTranscript
                    sub.visibility = View.VISIBLE
                } else {
                    sub.visibility = View.GONE
                }
            }
            IslandMode.THINKING -> {
                title.text = getString(R.string.thinking_status)
                if (snap.liveTranscript.isNotBlank()) {
                    sub.text = snap.liveTranscript
                    sub.visibility = View.VISIBLE
                } else {
                    sub.visibility = View.GONE
                }
            }
            IslandMode.SPEAKING -> {
                title.text = getString(R.string.speaking_status)
                if (snap.lastReplySnippet.isNotBlank()) {
                    sub.text = snap.lastReplySnippet
                    sub.visibility = View.VISIBLE
                } else {
                    sub.visibility = View.GONE
                }
            }
        }
    }

    private fun handleBackgroundCommand(command: String) {
        if (command.isBlank() || isProcessingAi) return
        isProcessingAi = true
        NovaRuntimeState.updateSnapshot {
            it.copy(
                islandMode = IslandMode.THINKING,
                liveTranscript = command.take(36)
            )
        }
        evaluateOverlayVisibility()
        refreshPillVisuals()

        serviceScope.launch {
            try {
                val model = AiModelCatalog.findById(currentSettings.selectedModelId)
                val session = container.chatRepository.getOrCreateLatestSession(model.id)
                val history = container.chatRepository.getRecentContext(session.id, limit = 8)

                container.chatRepository.appendMessage(
                    sessionId = session.id,
                    role = "user",
                    content = command,
                    modelUsed = model.shortBadge,
                    source = "floating_voice"
                )

                val result = container.aiRepository.generateReply(
                    userPrompt = command,
                    history = history,
                    modelOption = model,
                    isVoiceMode = true
                )

                container.chatRepository.appendMessage(
                    sessionId = session.id,
                    role = "assistant",
                    content = result.content,
                    modelUsed = result.usedModelLabel,
                    source = "floating_voice"
                )

                val snippet = result.content.replace('\n', ' ').take(44)
                NovaRuntimeState.updateSnapshot {
                    it.copy(lastReplySnippet = snippet, liveTranscript = "")
                }

                if (currentSettings.autoSpeakEnabled) {
                    voiceManager.speak(result.content)
                } else {
                    NovaRuntimeState.updateSnapshot { it.copy(islandMode = IslandMode.IDLE) }
                    refreshPillVisuals()
                }
            } finally {
                isProcessingAi = false
                if (NovaRuntimeState.snapshot.value.islandMode == IslandMode.THINKING) {
                    NovaRuntimeState.updateSnapshot { it.copy(islandMode = IslandMode.IDLE) }
                    refreshPillVisuals()
                }
            }
        }
    }

    private fun createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val talkIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, NovaBackgroundService::class.java).apply { action = ACTION_TRIGGER_VOICE },
            PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val bodyText = if (currentSettings.wakeWordEnabled) {
            getString(R.string.notification_text_wake)
        } else {
            getString(R.string.notification_text_idle)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(bodyText)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppIntent)
            .addAction(0, getString(R.string.tap_to_talk), talkIntent)
            .build()
    }

    private fun updateNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, buildNotification())
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        hideOverlayPill()
        voiceManager.release()
        NovaRuntimeState.updateSnapshot {
            it.copy(isServiceRunning = false, isOverlayShowing = false, islandMode = IslandMode.IDLE)
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private class MiniOrbPulseView(context: Context) : View(context) {
        private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00F0FF")
            style = Paint.Style.FILL
        }
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#8038BDF8")
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
        }
        private var rms = 0f
        private var mode = IslandMode.IDLE

        fun updateRms(value: Float) {
            rms = value
            invalidate()
        }

        fun setMode(islandMode: IslandMode) {
            mode = islandMode
            corePaint.color = when (islandMode) {
                IslandMode.IDLE -> Color.parseColor("#00F0FF")
                IslandMode.LISTENING -> Color.parseColor("#2DD4BF")
                IslandMode.THINKING -> Color.parseColor("#38BDF8")
                IslandMode.SPEAKING -> Color.parseColor("#00F0FF")
            }
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val maxR = (minOf(width, height) / 2f) - 2f
            val coreScale = when (mode) {
                IslandMode.LISTENING -> 0.52f + rms * 0.25f
                IslandMode.SPEAKING -> 0.65f
                IslandMode.THINKING -> 0.58f
                IslandMode.IDLE -> 0.52f
            }
            canvas.drawCircle(cx, cy, maxR * 0.92f, ringPaint)
            canvas.drawCircle(cx, cy, maxR * coreScale, corePaint)
        }
    }

    companion object {
        private const val CHANNEL_ID = "nova_voice_channel"
        private const val NOTIFICATION_ID = 7071
        const val ACTION_TRIGGER_VOICE = "com.example.action.TRIGGER_VOICE"
        const val ACTION_STOP_SERVICE = "com.example.action.STOP_SERVICE"

        fun start(context: Context) {
            val intent = Intent(context, NovaBackgroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, NovaBackgroundService::class.java))
        }
    }
}
