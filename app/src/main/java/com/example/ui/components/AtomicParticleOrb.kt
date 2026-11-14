package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.R
import com.example.ui.theme.NovaCyanNeon
import com.example.ui.theme.NovaSkyPlasma
import com.example.ui.theme.NovaTealGlow
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

enum class OrbVisualMode {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING
}

private class AtomParticle(
    val theta: Float,
    val phi: Float,
    val baseLayer: Float,
    val radiusPx: Float,
    val colorIndex: Int,
    var offsetX: Float = 0f,
    var offsetY: Float = 0f,
    var velX: Float = 0f,
    var velY: Float = 0f
)

@Composable
fun AtomicParticleOrb(
    mode: OrbVisualMode,
    rmsLevel: Float,
    modifier: Modifier = Modifier,
    onOrbTapped: () -> Unit = {}
) {
    val orbDesc = stringResource(R.string.orb_content_desc)
    val particleCount = 210

    val particles = remember {
        val rng = Random(42)
        val goldenAngle = PI * (3.0 - sqrt(5.0))
        List(particleCount) { i ->
            val y = 1.0 - (i / (particleCount - 1).toDouble()) * 2.0
            val phi = acos(y.coerceIn(-1.0, 1.0)).toFloat()
            val theta = (goldenAngle * i).toFloat()
            AtomParticle(
                theta = theta,
                phi = phi,
                baseLayer = 0.78f + rng.nextFloat() * 0.28f,
                radiusPx = 2.0f + rng.nextFloat() * 3.1f,
                colorIndex = i % 4
            )
        }
    }

    var rotAngle by remember { mutableFloatStateOf(0f) }
    var pulsePhase by remember { mutableFloatStateOf(0f) }
    var shockwaveProgress by remember { mutableFloatStateOf(1f) }
    var shockwaveOrigin by remember { mutableStateOf(Offset.Zero) }
    var frameTick by remember { mutableLongStateOf(0L) }
    var canvasCenter by remember { mutableStateOf(Offset.Zero) }
    var baseOrbRadius by remember { mutableFloatStateOf(140f) }

    fun triggerAtomShatter(impactPoint: Offset) {
        val rng = Random(System.nanoTime())
        shockwaveOrigin = impactPoint
        shockwaveProgress = 0f
        val r = baseOrbRadius.coerceAtLeast(90f)
        val sinR = sin(rotAngle)
        val cosR = cos(rotAngle)

        for (p in particles) {
            val sinPhi = sin(p.phi)
            val bx = r * p.baseLayer * sinPhi * cos(p.theta)
            val by = r * p.baseLayer * cos(p.phi)
            val bz = r * p.baseLayer * sinPhi * sin(p.theta)
            val px = canvasCenter.x + (bx * cosR - bz * sinR)
            val py = canvasCenter.y + by

            val dx = px - impactPoint.x
            val dy = py - impactPoint.y
            val dist = hypot(dx, dy).coerceAtLeast(12f)
            val dirX = dx / dist
            val dirY = dy / dist

            val burstSpeed = 420f + rng.nextFloat() * 780f
            val tangentX = -dirY
            val tangentY = dirX
            val swirl = (rng.nextFloat() - 0.5f) * 340f

            p.velX += dirX * burstSpeed + tangentX * swirl + (rng.nextFloat() - 0.5f) * 180f
            p.velY += dirY * burstSpeed + tangentY * swirl + (rng.nextFloat() - 0.5f) * 180f
        }
    }

    LaunchedEffect(mode) {
        var lastNanos = 0L
        while (true) {
            withFrameNanos { nanos ->
                if (lastNanos == 0L) {
                    lastNanos = nanos
                    return@withFrameNanos
                }
                val dt = ((nanos - lastNanos) / 1_000_000_000f).coerceIn(0.001f, 0.033f)
                lastNanos = nanos

                val speedMultiplier = when (mode) {
                    OrbVisualMode.IDLE -> 0.55f
                    OrbVisualMode.LISTENING -> 1.15f + rmsLevel * 1.4f
                    OrbVisualMode.THINKING -> 2.4f
                    OrbVisualMode.SPEAKING -> 1.5f
                }
                rotAngle += dt * speedMultiplier
                pulsePhase += dt * (if (mode == OrbVisualMode.THINKING) 5.5f else 2.6f)

                if (shockwaveProgress < 1f) {
                    shockwaveProgress = (shockwaveProgress + dt * 2.2f).coerceAtMost(1f)
                }

                val springK = 28f
                val damping = 4.6f
                for (p in particles) {
                    val ax = -springK * p.offsetX - damping * p.velX
                    val ay = -springK * p.offsetY - damping * p.velY
                    p.velX += ax * dt
                    p.velY += ay * dt
                    p.offsetX += p.velX * dt
                    p.offsetY += p.velY * dt
                }
                frameTick++
            }
        }
    }

    val palette = remember {
        listOf(
            NovaCyanNeon,
            NovaSkyPlasma,
            NovaTealGlow,
            Color(0xFFE0FFFF)
        )
    }

    Box(
        modifier = modifier
            .testTag("atomic_particle_orb")
            .semantics { contentDescription = orbDesc }
            .pointerInput(Unit) {
                detectTapGestures { tapOffset ->
                    triggerAtomShatter(tapOffset)
                    onOrbTapped()
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val pos = change.position
                    for (p in particles) {
                        p.velX += dragAmount.x * 16f
                        p.velY += dragAmount.y * 16f
                    }
                    if (hypot(dragAmount.x, dragAmount.y) > 10f) {
                        shockwaveOrigin = pos
                        shockwaveProgress = 0.15f
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Read frameTick so Canvas invalidates smoothly at display refresh rate
            val tick = frameTick
            if (tick < 0L) return@Canvas

            val cx = size.width / 2f
            val cy = size.height / 2f
            canvasCenter = Offset(cx, cy)

            val minDim = minOf(size.width, size.height)
            val breath = 1f + sin(pulsePhase) * 0.035f
            val voiceExpand = when (mode) {
                OrbVisualMode.LISTENING -> 1f + rmsLevel * 0.22f
                OrbVisualMode.SPEAKING -> 1f + (sin(pulsePhase * 2f) * 0.06f + 0.05f)
                OrbVisualMode.THINKING -> 0.94f
                OrbVisualMode.IDLE -> 1f
            }
            val orbRadius = minDim * 0.30f * breath * voiceExpand
            baseOrbRadius = orbRadius

            // Ambient bioluminescent halo
            val glowColor = when (mode) {
                OrbVisualMode.LISTENING -> NovaTealGlow
                OrbVisualMode.THINKING -> NovaSkyPlasma
                OrbVisualMode.SPEAKING -> NovaCyanNeon
                OrbVisualMode.IDLE -> NovaCyanNeon
            }
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        glowColor.copy(alpha = 0.22f),
                        NovaSkyPlasma.copy(alpha = 0.07f),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = orbRadius * 1.95f
                ),
                radius = orbRadius * 1.95f,
                center = Offset(cx, cy)
            )

            // Outer orbital energy ring
            drawCircle(
                color = glowColor.copy(alpha = 0.22f),
                radius = orbRadius * 1.18f,
                center = Offset(cx, cy),
                style = Stroke(width = 1.6f)
            )

            // Shockwave ring on touch shatter
            if (shockwaveProgress < 1f) {
                val ringRadius = orbRadius * (0.35f + shockwaveProgress * 2.1f)
                val ringAlpha = (1f - shockwaveProgress) * 0.65f
                drawCircle(
                    color = NovaCyanNeon.copy(alpha = ringAlpha),
                    radius = ringRadius,
                    center = shockwaveOrigin,
                    style = Stroke(width = (1f - shockwaveProgress) * 5f + 1f)
                )
            }

            // Project 3D Fibonacci sphere atoms + spring offsets
            val sinR = sin(rotAngle)
            val cosR = cos(rotAngle)

            for (p in particles) {
                val sinPhi = sin(p.phi)
                val bx = orbRadius * p.baseLayer * sinPhi * cos(p.theta)
                val by = orbRadius * p.baseLayer * cos(p.phi)
                val bz = orbRadius * p.baseLayer * sinPhi * sin(p.theta)

                val rx = bx * cosR - bz * sinR
                val rz = bx * sinR + bz * cosR

                val depthNorm = ((rz / (orbRadius * 1.1f)) + 1f) * 0.5f
                val alpha = (0.28f + depthNorm * 0.72f).coerceIn(0.2f, 1f)
                val scale = 0.65f + depthNorm * 0.75f

                val drawX = cx + rx + p.offsetX
                val drawY = cy + by + p.offsetY
                val dotColor = palette[p.colorIndex]

                // Micro glow for foreground atoms
                if (depthNorm > 0.6f || hypot(p.offsetX, p.offsetY) > 18f) {
                    drawCircle(
                        color = dotColor.copy(alpha = alpha * 0.24f),
                        radius = p.radiusPx * scale * 2.3f,
                        center = Offset(drawX, drawY)
                    )
                }

                drawCircle(
                    color = dotColor.copy(alpha = alpha),
                    radius = p.radiusPx * scale,
                    center = Offset(drawX, drawY)
                )
            }
        }
    }
}
