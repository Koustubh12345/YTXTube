package com.tensei.ytxtube

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import android.os.BatteryManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/**
 * PHASE 3 — ADDITIONAL PREMIUM FEATURES
 *
 * 1. Six Selectable Loading Animations (LiquidRing, DualPulse, NeonWave, GeometricOrbit, CyberDots, ShimmerBar)
 * 2. Animated Skeleton Loading for Video Cards
 * 3. Transcript Viewer, Search, Copy & Export
 * 4. Subscription Folders / Groups
 * 5. Performance Monitor (Auto blur reduction, Battery-aware animation, Memory & Frame drop monitor)
 */

// --- 1. Six Selectable Loading Animations ---
enum class PremiumLoadingStyle(val label: String) {
    LiquidRing("Liquid Glass Ring"),
    DualPulse("Dual Neon Pulse"),
    NeonWave("Neon Oscillating Wave"),
    GeometricOrbit("Geometric Orbit"),
    CyberDots("Cyber Bouncing Dots"),
    ShimmerBar("Liquid Shimmer Bar")
}

// --- 4. Subscription Folder Model ---
data class SubscriptionFolder(
    val id: String,
    val name: String,
    val channelIds: MutableList<String> = mutableListOf(),
    val iconName: String = "Folder"
)

// --- 3. Transcript Line Model ---
data class TranscriptLine(
    val startMs: Long,
    val durationMs: Long,
    val text: String
)

object PremiumFeatures {
    private const val PREFS = "premium_features_prefs"
    private var prefs: SharedPreferences? = null

    // 1. Loading Animation Style
    var loadingStyle by mutableStateOf(PremiumLoadingStyle.LiquidRing)
        private set

    // 5. Performance Settings
    var autoBlurReduction by mutableStateOf(true)
        private set
    var batteryAwareAnimations by mutableStateOf(true)
        private set
    var frameDropMonitoring by mutableStateOf(false)
        private set

    // Subscription Folders
    val subscriptionFolders = mutableStateListOf<SubscriptionFolder>()

    // Memory Usage Stats
    var usedMemoryMb by mutableLongStateOf(0L)
    var maxMemoryMb by mutableLongStateOf(0L)
    var droppedFramesCount by mutableLongStateOf(0L)

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).also { prefs = it }

        val animName = p.getString("loading_style", PremiumLoadingStyle.LiquidRing.name)
        loadingStyle = runCatching { PremiumLoadingStyle.valueOf(animName ?: "") }.getOrDefault(PremiumLoadingStyle.LiquidRing)

        autoBlurReduction = p.getBoolean("auto_blur_red", true)
        batteryAwareAnimations = p.getBoolean("battery_aware", true)
        frameDropMonitoring = p.getBoolean("frame_drop_mon", false)

        updateMemoryStats(context)
    }

    fun updateLoadingStyle(style: PremiumLoadingStyle) {
        loadingStyle = style
        prefs?.edit()?.putString("loading_style", style.name)?.apply()
    }

    fun updatePerformanceOptions(autoBlur: Boolean, batteryAware: Boolean, frameDrop: Boolean) {
        autoBlurReduction = autoBlur
        batteryAwareAnimations = batteryAware
        frameDropMonitoring = frameDrop
        prefs?.edit()
            ?.putBoolean("auto_blur_red", autoBlur)
            ?.putBoolean("battery_aware", batteryAware)
            ?.putBoolean("frame_drop_mon", frameDrop)
            ?.apply()
    }

    fun createSubscriptionFolder(name: String) {
        if (name.isBlank()) return
        subscriptionFolders.add(
            SubscriptionFolder(
                id = "folder_${System.currentTimeMillis()}",
                name = name.trim()
            )
        )
    }

    fun updateMemoryStats(context: Context) {
        val rt = Runtime.getRuntime()
        usedMemoryMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
        maxMemoryMb = rt.maxMemory() / (1024 * 1024)
    }

    fun isLowPowerState(context: Context): Boolean {
        if (!batteryAwareAnimations) return false
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val level = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
        return level <= 15
    }
}

/**
 * Universal App Loading Indicator dispatching across the 6 selectable styles.
 */
@Composable
fun AppLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    color: Color = MaterialTheme.colorScheme.primary
) {
    when (PremiumFeatures.loadingStyle) {
        PremiumLoadingStyle.LiquidRing -> LiquidRingLoader(modifier.size(size), color)
        PremiumLoadingStyle.DualPulse -> DualPulseLoader(modifier.size(size), color)
        PremiumLoadingStyle.NeonWave -> NeonWaveLoader(modifier.size(size), color)
        PremiumLoadingStyle.GeometricOrbit -> GeometricOrbitLoader(modifier.size(size), color)
        PremiumLoadingStyle.CyberDots -> CyberDotsLoader(modifier.size(size), color)
        PremiumLoadingStyle.ShimmerBar -> ShimmerBarLoader(modifier.size(size), color)
    }
}

@Composable
fun LiquidRingLoader(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    val rotation = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        rotation.animateTo(
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart)
        )
    }

    Canvas(modifier = modifier) {
        val stroke = 3.5f.dp.toPx()
        drawArc(
            brush = Brush.sweepGradient(listOf(color.copy(alpha = 0.05f), color, color)),
            startAngle = rotation.value,
            sweepAngle = 280f,
            useCenter = false,
            style = Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        )
    }
}

@Composable
fun DualPulseLoader(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    val pulse = remember { Animatable(0.2f) }

    LaunchedEffect(Unit) {
        pulse.animateTo(
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse)
        )
    }

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxR = size.minDimension / 2f

        drawCircle(
            color = color.copy(alpha = (1f - pulse.value) * 0.45f),
            radius = maxR * pulse.value,
            center = center
        )
        drawCircle(
            color = color.copy(alpha = 0.85f),
            radius = maxR * 0.35f,
            center = center
        )
    }
}

@Composable
fun NeonWaveLoader(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    val phase = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        phase.animateTo(
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Restart)
        )
    }

    Canvas(modifier = modifier) {
        val barCount = 4
        val barWidth = size.width / (barCount * 1.8f)
        val gap = barWidth * 0.8f

        for (i in 0 until barCount) {
            val progress = (phase.value + i * 0.25f) % 1f
            val h = (sin(progress * 3.14159f) * size.height).coerceAtLeast(4.dp.toPx())
            val x = i * (barWidth + gap)
            val y = (size.height - h) / 2f

            drawRoundRect(
                color = color.copy(alpha = 0.9f),
                topLeft = Offset(x.toFloat(), y.toFloat()),
                size = Size(barWidth, h.toFloat()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

@Composable
fun GeometricOrbitLoader(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    val angle = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        angle.animateTo(
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart)
        )
    }

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2.6f
        val rad = Math.toRadians(angle.value.toDouble())

        drawCircle(
            color = color.copy(alpha = 0.25f),
            radius = radius,
            center = center,
            style = Stroke(width = 1.5.dp.toPx())
        )

        val dotX = center.x + (radius * cos(rad)).toFloat()
        val dotY = center.y + (radius * sin(rad)).toFloat()

        drawCircle(
            color = color,
            radius = 5.dp.toPx(),
            center = Offset(dotX, dotY)
        )
    }
}

@Composable
fun CyberDotsLoader(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val transition = rememberInfiniteTransition(label = "dots")
        val alpha1 by transition.animateFloat(0.3f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "d1")
        val alpha2 by transition.animateFloat(0.3f, 1f, infiniteRepeatable(tween(600, delayMillis = 150), RepeatMode.Reverse), label = "d2")
        val alpha3 by transition.animateFloat(0.3f, 1f, infiniteRepeatable(tween(600, delayMillis = 300), RepeatMode.Reverse), label = "d3")

        Box(Modifier.size(10.dp).clip(CircleShape).background(color.copy(alpha = alpha1)))
        Box(Modifier.size(10.dp).clip(CircleShape).background(color.copy(alpha = alpha2)))
        Box(Modifier.size(10.dp).clip(CircleShape).background(color.copy(alpha = alpha3)))
    }
}

@Composable
fun ShimmerBarLoader(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    val shimmerPhase = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        shimmerPhase.animateTo(
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart)
        )
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(6.dp)) {
            val grad = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, color, Color.Transparent),
                startX = (shimmerPhase.value * size.width * 1.5f) - size.width * 0.5f,
                endX = (shimmerPhase.value * size.width * 1.5f) + size.width * 0.5f
            )
            drawRect(brush = grad)
        }
    }
}

/**
 * Animated Skeleton Loading Placeholder for Video Cards
 */
@Composable
fun VideoCardSkeleton(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(18.dp)
    val shimmer = remember { Animatable(0.2f) }

    LaunchedEffect(Unit) {
        shimmer.animateTo(
            targetValue = 0.5f,
            animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse)
        )
    }

    val alpha = shimmer.value

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = alpha * 0.4f))
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = alpha * 0.3f))
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = alpha * 0.4f))
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.45f)
                        .height(11.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = alpha * 0.25f))
                )
            }
        }
    }
}
