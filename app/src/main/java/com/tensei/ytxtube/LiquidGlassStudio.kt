package com.tensei.ytxtube

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.util.Locale
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

// ============================================================================
// CATEGORY 7: ADVANCED LIQUID GLASS DESIGN STUDIO (Features 86–100)
// Liquid Glass, Frosted Glass, Purple/Blue Neon Accents, Animated Gradients
// ============================================================================

/** Feature 86: Custom Glass Material Presets. */
enum class GlassMaterialPreset(
    val label: String,
    val blurDp: Int,
    val refractionHeightDp: Int,
    val refractionAmountDp: Int,
    val surfaceAlpha: Float,
    val borderAlpha: Float,
    val chromatic: Boolean,
) {
    LiquidDefault("Liquid Glass", 18, 14, 22, 0.38f, 0.20f, true),
    FrostedCrystal("Frosted Crystal", 28, 8, 12, 0.48f, 0.32f, false),
    NeonCyber("Neon Cyber Glass", 22, 20, 28, 0.34f, 0.55f, true),
    ObsidianStealth("Obsidian Stealth", 14, 10, 16, 0.68f, 0.14f, false),
    AuroraPrism("Aurora Prism", 24, 22, 32, 0.30f, 0.42f, true),
    MinimalMatte("Minimal Matte", 10, 4, 6, 0.78f, 0.10f, false),
}

/** Feature 92: Custom Navigation Indicator Styles. */
enum class NavIndicatorStyle(val label: String) {
    LiquidPill("Liquid Refractive Pill"),
    NeonUnderline("Neon Glow Underline"),
    GlowingDot("Floating Neon Orb"),
    FrostedSquircle("Frosted Squircle"),
    MinimalScale("Minimal Icon Scale"),
}

/** Feature 96: Custom Notification Styles where supported. */
enum class MediaNotificationStyle(val label: String) {
    ArtworkColorized("Dynamic Artwork Tint"),
    MinimalCompact("Compact Transport"),
    ExtendedNeon("Extended Studio Controls"),
}

/** Feature 97: Advanced Animation Timeline Presets. */
enum class AnimationTimelinePreset(val label: String, val durationMultiplier: Float, val springStiffness: Float) {
    SilkyLiquid("Silky Liquid (Default)", 1.0f, 380f),
    SnappyPro("Snappy 120Hz Pro", 0.68f, 620f),
    RelaxedCinema("Relaxed Cinema", 1.35f, 240f),
    InstantReduced("Instant / Zero Lag", 0.15f, 1200f),
}

/** Feature 98: Custom Loading Animation Styles. */
enum class LoadingAnimationStyle(val label: String) {
    LiquidPulse("Liquid Neon Pulse"),
    NeonDualOrbit("Dual Neon Orbit"),
    ShimmerWaveBars("Equalizer Wave Bars"),
    ClassicSpinner("Classic Material Ring"),
}

/** Feature 99, 100 & 145: Validated Theme Package model & validator. */
@Immutable
data class GlassThemePackage(
    val name: String,
    val author: String,
    val mode: String,
    val seedHex: String,
    val primaryGlowHex: String,
    val secondaryGlowHex: String,
    val materialPreset: GlassMaterialPreset,
    val blurDp: Int,
    val refractionHeightDp: Int,
    val refractionAmountDp: Int,
    val borderWidthDp: Float,
    val sheetCornerRadiusDp: Int,
    val animatedGradient: Boolean,
    val navIndicator: NavIndicatorStyle,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("schema", "ytx_theme_v292")
        .put("name", name)
        .put("author", author)
        .put("mode", mode)
        .put("seedHex", seedHex)
        .put("primaryGlowHex", primaryGlowHex)
        .put("secondaryGlowHex", secondaryGlowHex)
        .put("materialPreset", materialPreset.name)
        .put("blurDp", blurDp)
        .put("refractionHeightDp", refractionHeightDp)
        .put("refractionAmountDp", refractionAmountDp)
        .put("borderWidthDp", borderWidthDp.toDouble())
        .put("sheetCornerRadiusDp", sheetCornerRadiusDp)
        .put("animatedGradient", animatedGradient)
        .put("navIndicator", navIndicator.name)
}

@Immutable
data class ThemeValidationResult(
    val valid: Boolean,
    val errors: List<String>,
    val contrastRatio: Double,
    val parsedPackage: GlassThemePackage?,
)

/**
 * Feature 99: Strict schema, hex color, bounds, and WCAG contrast validator for theme packages.
 * Pure Kotlin object so it can be unit-tested deterministically.
 */
object ThemePackageValidator {
    private val hexPattern = Regex("^#[0-9A-Fa-f]{6}$")

    fun isValidHexColor(hex: String): Boolean = hexPattern.matches(hex.trim())

    fun parseRgb(hex: String): Triple<Int, Int, Int>? {
        val h = hex.trim().removePrefix("#")
        if (h.length != 6) return null
        return runCatching {
            Triple(
                h.substring(0, 2).toInt(16),
                h.substring(2, 4).toInt(16),
                h.substring(4, 6).toInt(16),
            )
        }.getOrNull()
    }

    /** Relative luminance per WCAG 2.1 specification. */
    fun relativeLuminance(hex: String): Double {
        val (r, g, b) = parseRgb(hex) ?: return 0.0
        fun channel(v: Int): Double {
            val s = v / 255.0
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)
    }

    /** WCAG contrast ratio between two hex colors (1.0 .. 21.0). */
    fun contrastRatio(hexA: String, hexB: String): Double {
        val l1 = relativeLuminance(hexA)
        val l2 = relativeLuminance(hexB)
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    fun validate(rawJson: String): ThemeValidationResult {
        val errors = mutableListOf<String>()
        val root = try {
            JSONObject(rawJson)
        } catch (e: Exception) {
            return ThemeValidationResult(false, listOf("Invalid JSON syntax: ${e.message}"), 1.0, null)
        }

        if (root.optString("schema") != "ytx_theme_v292") {
            errors.add("Unsupported or missing schema (expected 'ytx_theme_v292')")
        }
        val name = root.optString("name").trim()
        if (name.isEmpty() || name.length > 48) {
            errors.add("Theme name must be 1–48 characters")
        }
        val mode = root.optString("mode", "dark").lowercase(Locale.US)
        if (mode !in setOf("system", "dark", "light", "amoled")) {
            errors.add("Invalid theme mode '$mode'")
        }
        val seedHex = root.optString("seedHex", "#7C3AED").trim()
        val glow1 = root.optString("primaryGlowHex", "#7C3AED").trim()
        val glow2 = root.optString("secondaryGlowHex", "#0091FF").trim()
        if (!isValidHexColor(seedHex)) errors.add("Invalid seedHex '$seedHex'")
        if (!isValidHexColor(glow1)) errors.add("Invalid primaryGlowHex '$glow1'")
        if (!isValidHexColor(glow2)) errors.add("Invalid secondaryGlowHex '$glow2'")

        val blur = root.optInt("blurDp", 18)
        if (blur !in 0..48) errors.add("blurDp ($blur) out of safe bounds 0..48")
        val refH = root.optInt("refractionHeightDp", 14)
        val refA = root.optInt("refractionAmountDp", 22)
        if (refH !in 0..40 || refA !in 0..50) errors.add("Refraction parameters out of bounds")

        val bgHex = if (mode == "light") "#FAF8FF" else if (mode == "amoled") "#000000" else "#0D0B14"
        val fgHex = if (mode == "light") "#191340" else "#F2EEFF"
        val contrast = contrastRatio(bgHex, fgHex)

        if (errors.isNotEmpty()) {
            return ThemeValidationResult(false, errors, contrast, null)
        }

        val pkg = GlassThemePackage(
            name = name,
            author = root.optString("author", "Community").take(32),
            mode = mode,
            seedHex = seedHex,
            primaryGlowHex = glow1,
            secondaryGlowHex = glow2,
            materialPreset = GlassMaterialPreset.entries.firstOrNull { it.name == root.optString("materialPreset") }
                ?: GlassMaterialPreset.LiquidDefault,
            blurDp = blur,
            refractionHeightDp = refH,
            refractionAmountDp = refA,
            borderWidthDp = root.optDouble("borderWidthDp", 1.0).toFloat().coerceIn(0.5f, 3.0f),
            sheetCornerRadiusDp = root.optInt("sheetCornerRadiusDp", 28).coerceIn(12, 42),
            animatedGradient = root.optBoolean("animatedGradient", true),
            navIndicator = NavIndicatorStyle.entries.firstOrNull { it.name == root.optString("navIndicator") }
                ?: NavIndicatorStyle.LiquidPill,
        )
        return ThemeValidationResult(true, emptyList(), contrast, pkg)
    }
}

/**
 * Central state manager for Category 7: Advanced Liquid Glass Design Studio (Features 86–100).
 */
object LiquidGlassStudio {
    private const val PREFS = "liquid_glass_studio_v292"
    private var prefs: SharedPreferences? = null

    var materialPreset by mutableStateOf(GlassMaterialPreset.LiquidDefault)
        private set

    // Feature 87: Individual blur settings per screen
    var playerBlurDp by mutableIntStateOf(20)
        private set
    var navBarBlurDp by mutableIntStateOf(10)
        private set
    var sheetBlurDp by mutableIntStateOf(26)
        private set
    var musicBlurDp by mutableIntStateOf(24)
        private set
    var headerBlurDp by mutableIntStateOf(16)
        private set

    // Feature 88: Glass depth controls
    var refractionHeightDp by mutableIntStateOf(14)
        private set
    var refractionAmountDp by mutableIntStateOf(22)
        private set
    var innerShadowAlpha by mutableFloatStateOf(0.5f)
        private set
    var chromaticAberration by mutableStateOf(true)
        private set

    // Feature 89 & 90: Border gradient editor & Custom Purple/Blue Neon Glow Colours
    var borderWidthDp by mutableFloatStateOf(1.0f)
        private set
    var neonBorderEnabled by mutableStateOf(true)
        private set
    var primaryGlowHex by mutableStateOf("#7C3AED") // Electric Purple Neon
        private set
    var secondaryGlowHex by mutableStateOf("#0091FF") // Cyber Blue Neon
        private set

    // Feature 91: Animated gradient backgrounds
    var animatedBackgroundsEnabled by mutableStateOf(true)
        private set

    // Feature 92: Navigation indicator style
    var navIndicatorStyle by mutableStateOf(NavIndicatorStyle.LiquidPill)
        private set

    // Feature 93: Dynamic wallpaper contrast boost
    var wallpaperContrastBoost by mutableFloatStateOf(1.0f)
        private set

    // Feature 94 & 95: Player glass & Bottom-sheet appearance
    var playerOverlayOpacity by mutableFloatStateOf(0.45f)
        private set
    var sheetCornerRadiusDp by mutableIntStateOf(28)
        private set
    var sheetSurfaceAlpha by mutableFloatStateOf(0.68f)
        private set

    // Feature 96, 97, 98: Notification style, Animation timeline, Loading animation
    var notificationStyle by mutableStateOf(MediaNotificationStyle.ArtworkColorized)
        private set
    var animationPreset by mutableStateOf(AnimationTimelinePreset.SilkyLiquid)
        private set
    var loadingStyle by mutableStateOf(LoadingAnimationStyle.LiquidPulse)
        private set

    // Feature 145: Built-in Community Theme Packages
    val communityThemePackages = listOf(
        GlassThemePackage(
            name = "Neon Cyber Pulse",
            author = "YTX Studio",
            mode = "amoled",
            seedHex = "#7C3AED",
            primaryGlowHex = "#7C3AED",
            secondaryGlowHex = "#0091FF",
            materialPreset = GlassMaterialPreset.NeonCyber,
            blurDp = 22,
            refractionHeightDp = 20,
            refractionAmountDp = 28,
            borderWidthDp = 1.5f,
            sheetCornerRadiusDp = 30,
            animatedGradient = true,
            navIndicator = NavIndicatorStyle.NeonUnderline,
        ),
        GlassThemePackage(
            name = "Arctic Frosted Crystal",
            author = "YTX Studio",
            mode = "dark",
            seedHex = "#3B5BDB",
            primaryGlowHex = "#38BDF8",
            secondaryGlowHex = "#818CF8",
            materialPreset = GlassMaterialPreset.FrostedCrystal,
            blurDp = 28,
            refractionHeightDp = 10,
            refractionAmountDp = 14,
            borderWidthDp = 1.0f,
            sheetCornerRadiusDp = 28,
            animatedGradient = true,
            navIndicator = NavIndicatorStyle.LiquidPill,
        ),
        GlassThemePackage(
            name = "Tokyo Midnight Aurora",
            author = "Community Lab",
            mode = "dark",
            seedHex = "#6C2BD9",
            primaryGlowHex = "#EC4899",
            secondaryGlowHex = "#6366F1",
            materialPreset = GlassMaterialPreset.AuroraPrism,
            blurDp = 24,
            refractionHeightDp = 22,
            refractionAmountDp = 32,
            borderWidthDp = 1.2f,
            sheetCornerRadiusDp = 32,
            animatedGradient = true,
            navIndicator = NavIndicatorStyle.GlowingDot,
        ),
        GlassThemePackage(
            name = "Obsidian Pro Minimal",
            author = "Community Lab",
            mode = "amoled",
            seedHex = "#8A8F98",
            primaryGlowHex = "#6366F1",
            secondaryGlowHex = "#14B8A6",
            materialPreset = GlassMaterialPreset.ObsidianStealth,
            blurDp = 14,
            refractionHeightDp = 8,
            refractionAmountDp = 12,
            borderWidthDp = 0.8f,
            sheetCornerRadiusDp = 24,
            animatedGradient = false,
            navIndicator = NavIndicatorStyle.MinimalScale,
        ),
    )

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).also { prefs = it }
        materialPreset = GlassMaterialPreset.entries.firstOrNull {
            it.name == p.getString("material_preset", "LiquidDefault")
        } ?: GlassMaterialPreset.LiquidDefault
        playerBlurDp = p.getInt("player_blur", 20).coerceIn(0, 40)
        navBarBlurDp = p.getInt("navbar_blur", 10).coerceIn(0, 40)
        sheetBlurDp = p.getInt("sheet_blur", 26).coerceIn(0, 40)
        musicBlurDp = p.getInt("music_blur", 24).coerceIn(0, 40)
        headerBlurDp = p.getInt("header_blur", 16).coerceIn(0, 40)
        refractionHeightDp = p.getInt("refraction_height", materialPreset.refractionHeightDp).coerceIn(0, 36)
        refractionAmountDp = p.getInt("refraction_amount", materialPreset.refractionAmountDp).coerceIn(0, 44)
        innerShadowAlpha = p.getFloat("inner_shadow_alpha", 0.5f).coerceIn(0f, 1f)
        chromaticAberration = p.getBoolean("chromatic", materialPreset.chromatic)
        borderWidthDp = p.getFloat("border_width", 1.0f).coerceIn(0.5f, 3.0f)
        neonBorderEnabled = p.getBoolean("neon_border", true)
        primaryGlowHex = p.getString("primary_glow", "#7C3AED") ?: "#7C3AED"
        secondaryGlowHex = p.getString("secondary_glow", "#0091FF") ?: "#0091FF"
        animatedBackgroundsEnabled = p.getBoolean("animated_bg", true)
        navIndicatorStyle = NavIndicatorStyle.entries.firstOrNull {
            it.name == p.getString("nav_indicator", "LiquidPill")
        } ?: NavIndicatorStyle.LiquidPill
        wallpaperContrastBoost = p.getFloat("wallpaper_contrast", 1.0f).coerceIn(0.8f, 1.5f)
        playerOverlayOpacity = p.getFloat("player_overlay_opacity", 0.45f).coerceIn(0.15f, 0.90f)
        sheetCornerRadiusDp = p.getInt("sheet_corner_radius", 28).coerceIn(12, 42)
        sheetSurfaceAlpha = p.getFloat("sheet_surface_alpha", 0.68f).coerceIn(0.25f, 1.0f)
        notificationStyle = MediaNotificationStyle.entries.firstOrNull {
            it.name == p.getString("notif_style", "ArtworkColorized")
        } ?: MediaNotificationStyle.ArtworkColorized
        animationPreset = AnimationTimelinePreset.entries.firstOrNull {
            it.name == p.getString("anim_preset", "SilkyLiquid")
        } ?: AnimationTimelinePreset.SilkyLiquid
        loadingStyle = LoadingAnimationStyle.entries.firstOrNull {
            it.name == p.getString("loading_style", "LiquidPulse")
        } ?: LoadingAnimationStyle.LiquidPulse
    }

    val primaryGlowColor: Color
        get() = parseHexColor(primaryGlowHex, Color(0xFF7C3AED))

    val secondaryGlowColor: Color
        get() = parseHexColor(secondaryGlowHex, Color(0xFF0091FF))

    fun borderGradientBrush(): Brush =
        if (neonBorderEnabled) {
            Brush.linearGradient(
                listOf(
                    primaryGlowColor.copy(alpha = 0.65f),
                    secondaryGlowColor.copy(alpha = 0.45f),
                    Color.White.copy(alpha = 0.18f),
                ),
            )
        } else {
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.22f),
                    Color.White.copy(alpha = 0.06f),
                ),
            )
        }

    fun applyMaterialPreset(preset: GlassMaterialPreset) {
        materialPreset = preset
        playerBlurDp = preset.blurDp
        sheetBlurDp = (preset.blurDp + 6).coerceAtMost(38)
        refractionHeightDp = preset.refractionHeightDp
        refractionAmountDp = preset.refractionAmountDp
        chromaticAberration = preset.chromatic
        saveAll()
    }

    fun updateScreenBlurs(
        player: Int = playerBlurDp,
        navBar: Int = navBarBlurDp,
        sheet: Int = sheetBlurDp,
        music: Int = musicBlurDp,
        header: Int = headerBlurDp,
    ) {
        playerBlurDp = player.coerceIn(0, 40)
        navBarBlurDp = navBar.coerceIn(0, 40)
        sheetBlurDp = sheet.coerceIn(0, 40)
        musicBlurDp = music.coerceIn(0, 40)
        headerBlurDp = header.coerceIn(0, 40)
        saveAll()
    }

    fun updateDepthAndBorders(
        refHeight: Int = refractionHeightDp,
        refAmount: Int = refractionAmountDp,
        shadowAlpha: Float = innerShadowAlpha,
        chromatic: Boolean = chromaticAberration,
        borderW: Float = borderWidthDp,
        neonBorder: Boolean = neonBorderEnabled,
    ) {
        refractionHeightDp = refHeight.coerceIn(0, 36)
        refractionAmountDp = refAmount.coerceIn(0, 44)
        innerShadowAlpha = shadowAlpha.coerceIn(0f, 1f)
        chromaticAberration = chromatic
        borderWidthDp = borderW.coerceIn(0.5f, 3.0f)
        neonBorderEnabled = neonBorder
        saveAll()
    }

    fun updateGlowAndStyle(
        glow1: String = primaryGlowHex,
        glow2: String = secondaryGlowHex,
        animBg: Boolean = animatedBackgroundsEnabled,
        navStyle: NavIndicatorStyle = navIndicatorStyle,
        animTimeline: AnimationTimelinePreset = animationPreset,
        loader: LoadingAnimationStyle = loadingStyle,
        sheetRadius: Int = sheetCornerRadiusDp,
        playerAlpha: Float = playerOverlayOpacity,
    ) {
        if (ThemePackageValidator.isValidHexColor(glow1)) primaryGlowHex = glow1.uppercase(Locale.US)
        if (ThemePackageValidator.isValidHexColor(glow2)) secondaryGlowHex = glow2.uppercase(Locale.US)
        animatedBackgroundsEnabled = animBg
        navIndicatorStyle = navStyle
        animationPreset = animTimeline
        loadingStyle = loader
        sheetCornerRadiusDp = sheetRadius.coerceIn(12, 42)
        playerOverlayOpacity = playerAlpha.coerceIn(0.15f, 0.90f)
        saveAll()
    }

    fun applyThemePackage(pkg: GlassThemePackage) {
        ThemeSettings.chooseMode(pkg.mode)
        ThemeSettings.choose(pkg.seedHex)
        materialPreset = pkg.materialPreset
        playerBlurDp = pkg.blurDp
        refractionHeightDp = pkg.refractionHeightDp
        refractionAmountDp = pkg.refractionAmountDp
        borderWidthDp = pkg.borderWidthDp
        sheetCornerRadiusDp = pkg.sheetCornerRadiusDp
        primaryGlowHex = pkg.primaryGlowHex
        secondaryGlowHex = pkg.secondaryGlowHex
        animatedBackgroundsEnabled = pkg.animatedGradient
        navIndicatorStyle = pkg.navIndicator
        saveAll()
    }

    fun exportCurrentThemeJson(name: String = "My YTX Liquid Glass"): String =
        GlassThemePackage(
            name = name.ifBlank { "My YTX Liquid Glass" },
            author = "Local User",
            mode = ThemeSettings.mode,
            seedHex = ThemeSettings.seed ?: SEED_AURORA,
            primaryGlowHex = primaryGlowHex,
            secondaryGlowHex = secondaryGlowHex,
            materialPreset = materialPreset,
            blurDp = playerBlurDp,
            refractionHeightDp = refractionHeightDp,
            refractionAmountDp = refractionAmountDp,
            borderWidthDp = borderWidthDp,
            sheetCornerRadiusDp = sheetCornerRadiusDp,
            animatedGradient = animatedBackgroundsEnabled,
            navIndicator = navIndicatorStyle,
        ).toJson().toString(2)

    private fun saveAll() {
        prefs?.edit()
            ?.putString("material_preset", materialPreset.name)
            ?.putInt("player_blur", playerBlurDp)
            ?.putInt("navbar_blur", navBarBlurDp)
            ?.putInt("sheet_blur", sheetBlurDp)
            ?.putInt("music_blur", musicBlurDp)
            ?.putInt("header_blur", headerBlurDp)
            ?.putInt("refraction_height", refractionHeightDp)
            ?.putInt("refraction_amount", refractionAmountDp)
            ?.putFloat("inner_shadow_alpha", innerShadowAlpha)
            ?.putBoolean("chromatic", chromaticAberration)
            ?.putFloat("border_width", borderWidthDp)
            ?.putBoolean("neon_border", neonBorderEnabled)
            ?.putString("primary_glow", primaryGlowHex)
            ?.putString("secondary_glow", secondaryGlowHex)
            ?.putBoolean("animated_bg", animatedBackgroundsEnabled)
            ?.putString("nav_indicator", navIndicatorStyle.name)
            ?.putFloat("wallpaper_contrast", wallpaperContrastBoost)
            ?.putFloat("player_overlay_opacity", playerOverlayOpacity)
            ?.putInt("sheet_corner_radius", sheetCornerRadiusDp)
            ?.putFloat("sheet_surface_alpha", sheetSurfaceAlpha)
            ?.putString("notif_style", notificationStyle.name)
            ?.putString("anim_preset", animationPreset.name)
            ?.putString("loading_style", loadingStyle.name)
            ?.apply()
    }

    fun resetAll() {
        prefs?.edit()?.clear()?.apply()
        applyMaterialPreset(GlassMaterialPreset.LiquidDefault)
        primaryGlowHex = "#7C3AED"
        secondaryGlowHex = "#0091FF"
        animatedBackgroundsEnabled = true
        navIndicatorStyle = NavIndicatorStyle.LiquidPill
        animationPreset = AnimationTimelinePreset.SilkyLiquid
        loadingStyle = LoadingAnimationStyle.LiquidPulse
        borderWidthDp = 1.0f
        neonBorderEnabled = true
    }

    private fun parseHexColor(hex: String, fallback: Color): Color {
        val rgb = ThemePackageValidator.parseRgb(hex) ?: return fallback
        return Color(rgb.first, rgb.second, rgb.third)
    }
}

/**
 * Feature 91: Subtle Animated Purple/Blue Neon Gradient Mesh Background Layer.
 */
@Composable
fun AnimatedLiquidGradientBackground(modifier: Modifier = Modifier) {
    if (!LiquidGlassStudio.animatedBackgroundsEnabled || !GlassQuality.effectsSupported) return
    val isAmoled = ThemeSettings.mode == "amoled"
    val isDark = isDarkTheme()
    val alphaScale = when {
        isAmoled -> 0.14f
        isDark -> 0.20f
        else -> 0.10f
    }
    val infinite = rememberInfiniteTransition(label = "liquidGradientBg")
    val drift by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bgDrift",
    )
    val c1 = LiquidGlassStudio.primaryGlowColor.copy(alpha = alphaScale)
    val c2 = LiquidGlassStudio.secondaryGlowColor.copy(alpha = alphaScale * 0.85f)

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (w < 10f || h < 10f) return@Canvas
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(c1, Color.Transparent),
                center = Offset(w * (0.15f + 0.65f * drift), h * 0.12f),
                radius = w * 0.85f,
            ),
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(c2, Color.Transparent),
                center = Offset(w * (0.85f - 0.60f * drift), h * 0.78f),
                radius = w * 0.90f,
            ),
        )
    }
}

/**
 * Feature 98: Custom Loading Animation Component supporting 4 selectable studio styles.
 */
@Composable
fun YtxStudioLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
) {
    val style = LiquidGlassStudio.loadingStyle
    val c1 = LiquidGlassStudio.primaryGlowColor
    val c2 = LiquidGlassStudio.secondaryGlowColor

    if (style == LoadingAnimationStyle.ClassicSpinner) {
        CircularProgressIndicator(color = c1, strokeWidth = 3.dp, modifier = modifier.size(size))
        return
    }

    val infinite = rememberInfiniteTransition(label = "ytxLoader")
    val t by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "loaderPhase",
    )

    Canvas(modifier = modifier.size(size)) {
        val r = this.size.minDimension / 2f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        when (style) {
            LoadingAnimationStyle.LiquidPulse -> {
                val pulse = (sin(t) * 0.5f + 0.5f)
                drawCircle(c1.copy(alpha = 0.35f * (1f - pulse)), radius = r * (0.5f + 0.5f * pulse), center = center)
                drawCircle(c2, radius = r * (0.28f + 0.22f * pulse), center = center)
            }
            LoadingAnimationStyle.NeonDualOrbit -> {
                drawArc(
                    brush = Brush.sweepGradient(listOf(Color.Transparent, c1, c2)),
                    startAngle = (t * 180f / PI).toFloat(),
                    sweepAngle = 240f,
                    useCenter = false,
                    style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round),
                )
            }
            LoadingAnimationStyle.ShimmerWaveBars -> {
                val bars = 4
                val gap = 3.dp.toPx()
                val bw = (this.size.width - gap * (bars - 1)) / bars
                for (i in 0 until bars) {
                    val hFrac = 0.3f + 0.7f * (sin(t + i * 0.9f) * 0.5f + 0.5f)
                    val bh = this.size.height * hFrac
                    drawRoundRect(
                        color = if (i % 2 == 0) c1 else c2,
                        topLeft = Offset(i * (bw + gap), (this.size.height - bh) / 2f),
                        size = Size(bw, bh),
                        cornerRadius = CornerRadius(bw / 2f, bw / 2f),
                    )
                }
            }
            LoadingAnimationStyle.ClassicSpinner -> Unit
        }
    }
}

/**
 * Interactive Advanced Liquid Glass Design Studio Sheet (Features 86–100).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiquidGlassDesignStudioSheet(onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Glass Presets & Depth", "Per-Screen Blur", "Neon Glow & Borders", "Motion & Nav Style", "Theme Packages")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 650.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                "Liquid Glass Design Studio",
                fontFamily = RoundedHeavy,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
            )
            Text(
                "Refractive shaders, per-screen blur, purple/blue neon accents & validated theme packages",
                color = c.onSurfaceVariant,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tabs.forEachIndexed { idx, label ->
                    FilterChip(selected = tab == idx, onClick = { tab = idx }, label = { Text(label) })
                }
            }
            Spacer(Modifier.height(12.dp))

            when (tab) {
                0 -> GlassPresetsAndDepthTab()
                1 -> PerScreenBlurTab()
                2 -> NeonGlowAndBordersTab()
                3 -> MotionAndNavigationStyleTab()
                4 -> ThemePackageManagerTab(context)
            }
        }
    }
}

@Composable
private fun GlassPresetsAndDepthTab() {
    val c = MaterialTheme.colorScheme

    // Live interactive Liquid Glass preview card
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(86.dp)
            .clip(RoundedCornerShape(LiquidGlassStudio.sheetCornerRadiusDp.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        LiquidGlassStudio.primaryGlowColor.copy(alpha = 0.28f),
                        LiquidGlassStudio.secondaryGlowColor.copy(alpha = 0.24f),
                    ),
                ),
            )
            .border(
                width = LiquidGlassStudio.borderWidthDp.dp,
                brush = LiquidGlassStudio.borderGradientBrush(),
                shape = RoundedCornerShape(LiquidGlassStudio.sheetCornerRadiusDp.dp),
            )
            .padding(14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            YtxStudioLoadingIndicator(size = 34.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "Live Surface: ${LiquidGlassStudio.materialPreset.label}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                )
                Text(
                    "Blur ${LiquidGlassStudio.playerBlurDp}dp · Refraction ${LiquidGlassStudio.refractionHeightDp}/${LiquidGlassStudio.refractionAmountDp}dp · Glow ${LiquidGlassStudio.primaryGlowHex}",
                    fontSize = 11.5.sp,
                    color = c.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }

    Spacer(Modifier.height(12.dp))
    Text("Glass Material Presets (Feature 86)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.primary)
    Spacer(Modifier.height(6.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GlassMaterialPreset.entries.forEach { preset ->
            FilterChip(
                selected = LiquidGlassStudio.materialPreset == preset,
                onClick = { LiquidGlassStudio.applyMaterialPreset(preset) },
                label = { Text(preset.label) },
            )
        }
    }

    Spacer(Modifier.height(10.dp))
    Text("Glass Depth & Lens Controls (Feature 88)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.primary)
    StudioSlider("Lens Refraction Height", LiquidGlassStudio.refractionHeightDp.toFloat(), 0f..36f, "${LiquidGlassStudio.refractionHeightDp} dp") {
        LiquidGlassStudio.updateDepthAndBorders(refHeight = it.roundToInt())
    }
    StudioSlider("Lens Refraction Amount", LiquidGlassStudio.refractionAmountDp.toFloat(), 0f..44f, "${LiquidGlassStudio.refractionAmountDp} dp") {
        LiquidGlassStudio.updateDepthAndBorders(refAmount = it.roundToInt())
    }
    StudioSlider("Inner Shadow Depth", LiquidGlassStudio.innerShadowAlpha, 0f..1f, "${(LiquidGlassStudio.innerShadowAlpha * 100).roundToInt()}%") {
        LiquidGlassStudio.updateDepthAndBorders(shadowAlpha = it)
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Chromatic Aberration Prism Edge", Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Switch(
            checked = LiquidGlassStudio.chromaticAberration,
            onCheckedChange = { LiquidGlassStudio.updateDepthAndBorders(chromatic = it) },
        )
    }
}

@Composable
private fun PerScreenBlurTab() {
    val c = MaterialTheme.colorScheme
    Text("Individual Blur Radius Per Screen (Feature 87)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.primary)
    Spacer(Modifier.height(6.dp))
    StudioSlider("Video Player Overlay Blur (Feature 94)", LiquidGlassStudio.playerBlurDp.toFloat(), 0f..40f, "${LiquidGlassStudio.playerBlurDp} dp") {
        LiquidGlassStudio.updateScreenBlurs(player = it.roundToInt())
    }
    StudioSlider("Bottom Navigation Bar Blur", LiquidGlassStudio.navBarBlurDp.toFloat(), 0f..40f, "${LiquidGlassStudio.navBarBlurDp} dp") {
        LiquidGlassStudio.updateScreenBlurs(navBar = it.roundToInt())
    }
    StudioSlider("Bottom Sheets & Dialogs Blur (Feature 95)", LiquidGlassStudio.sheetBlurDp.toFloat(), 0f..40f, "${LiquidGlassStudio.sheetBlurDp} dp") {
        LiquidGlassStudio.updateScreenBlurs(sheet = it.roundToInt())
    }
    StudioSlider("Music Player Backdrop Blur", LiquidGlassStudio.musicBlurDp.toFloat(), 0f..40f, "${LiquidGlassStudio.musicBlurDp} dp") {
        LiquidGlassStudio.updateScreenBlurs(music = it.roundToInt())
    }
    StudioSlider("Collapsing Header Blur", LiquidGlassStudio.headerBlurDp.toFloat(), 0f..40f, "${LiquidGlassStudio.headerBlurDp} dp") {
        LiquidGlassStudio.updateScreenBlurs(header = it.roundToInt())
    }
    StudioSlider("Bottom Sheet Corner Radius", LiquidGlassStudio.sheetCornerRadiusDp.toFloat(), 12f..42f, "${LiquidGlassStudio.sheetCornerRadiusDp} dp") {
        LiquidGlassStudio.updateGlowAndStyle(sheetRadius = it.roundToInt())
    }
}

@Composable
private fun NeonGlowAndBordersTab() {
    val c = MaterialTheme.colorScheme
    var glow1 by remember { mutableStateOf(LiquidGlassStudio.primaryGlowHex) }
    var glow2 by remember { mutableStateOf(LiquidGlassStudio.secondaryGlowHex) }

    Text("Neon Glow & Border Gradient Editor (Features 89–91)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.primary)
    Spacer(Modifier.height(8.dp))
    val glowPairs = listOf(
        "Purple/Blue Neon" to ("#7C3AED" to "#0091FF"),
        "Cyber Magenta/Cyan" to ("#EC4899" to "#06B6D4"),
        "Emerald/Blue" to ("#10B981" to "#3B82F6"),
        "Crimson/Gold" to ("#FF0033" to "#F59E0B"),
    )
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        glowPairs.forEach { (label, pair) ->
            FilterChip(
                selected = LiquidGlassStudio.primaryGlowHex.equals(pair.first, ignoreCase = true),
                onClick = {
                    glow1 = pair.first
                    glow2 = pair.second
                    LiquidGlassStudio.updateGlowAndStyle(glow1 = pair.first, glow2 = pair.second)
                },
                label = { Text(label) },
            )
        }
    }

    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = glow1,
            onValueChange = { glow1 = it; LiquidGlassStudio.updateGlowAndStyle(glow1 = it) },
            label = { Text("Primary Glow (#RRGGBB)") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = glow2,
            onValueChange = { glow2 = it; LiquidGlassStudio.updateGlowAndStyle(glow2 = it) },
            label = { Text("Secondary Glow (#RRGGBB)") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }

    StudioSlider("Glass Border Width", LiquidGlassStudio.borderWidthDp, 0.5f..3.0f, "${"%.1f".format(Locale.US, LiquidGlassStudio.borderWidthDp)} dp") {
        LiquidGlassStudio.updateDepthAndBorders(borderW = it)
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Dual-Stop Neon Border Gradient", Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Switch(
            checked = LiquidGlassStudio.neonBorderEnabled,
            onCheckedChange = { LiquidGlassStudio.updateDepthAndBorders(neonBorder = it) },
        )
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Animated Aurora Gradient Backgrounds (Feature 91)", Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Switch(
            checked = LiquidGlassStudio.animatedBackgroundsEnabled,
            onCheckedChange = { LiquidGlassStudio.updateGlowAndStyle(animBg = it) },
        )
    }
}

@Composable
private fun MotionAndNavigationStyleTab() {
    val c = MaterialTheme.colorScheme

    Text("Navigation Indicator Style (Feature 92)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.primary)
    Spacer(Modifier.height(6.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NavIndicatorStyle.entries.forEach { style ->
            FilterChip(
                selected = LiquidGlassStudio.navIndicatorStyle == style,
                onClick = { LiquidGlassStudio.updateGlowAndStyle(navStyle = style) },
                label = { Text(style.label) },
            )
        }
    }

    Spacer(Modifier.height(10.dp))
    Text("Animation Timeline Physics (Feature 97)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.primary)
    Spacer(Modifier.height(6.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AnimationTimelinePreset.entries.forEach { anim ->
            FilterChip(
                selected = LiquidGlassStudio.animationPreset == anim,
                onClick = { LiquidGlassStudio.updateGlowAndStyle(animTimeline = anim) },
                label = { Text(anim.label) },
            )
        }
    }

    Spacer(Modifier.height(10.dp))
    Text("Custom Loading Animation Editor (Feature 98)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.primary)
    Spacer(Modifier.height(6.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LoadingAnimationStyle.entries.forEach { loader ->
            FilterChip(
                selected = LiquidGlassStudio.loadingStyle == loader,
                onClick = { LiquidGlassStudio.updateGlowAndStyle(loader = loader) },
                label = { Text(loader.label) },
            )
        }
    }
}

@Composable
private fun ThemePackageManagerTab(context: Context) {
    val c = MaterialTheme.colorScheme
    var jsonInput by remember { mutableStateOf("") }
    var validationMessage by remember { mutableStateOf<String?>(null) }

    Text("Community Theme Packages (Feature 145)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.primary)
    Spacer(Modifier.height(6.dp))
    LiquidGlassStudio.communityThemePackages.forEach { pkg ->
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(c.surfaceContainerHigh)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(pkg.name, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                Text("${pkg.mode.uppercase()} · ${pkg.materialPreset.label} · Glow ${pkg.primaryGlowHex}/${pkg.secondaryGlowHex}", fontSize = 11.5.sp, color = c.onSurfaceVariant)
            }
            Button(
                onClick = {
                    LiquidGlassStudio.applyThemePackage(pkg)
                    validationMessage = "Applied validated theme package '${pkg.name}'."
                },
            ) {
                Text("Apply", fontSize = 12.sp)
            }
        }
    }

    Spacer(Modifier.height(10.dp))
    HorizontalDivider(color = c.onSurface.copy(alpha = 0.08f))
    Spacer(Modifier.height(10.dp))

    Text("Theme Package Validation & Import/Export (Features 99 & 100)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.primary)
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = {
                val exported = LiquidGlassStudio.exportCurrentThemeJson()
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("YTX Theme Package", exported))
                validationMessage = "Exported current theme JSON to clipboard."
            },
        ) {
            Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(4.dp))
            Text("Export JSON")
        }
        OutlinedButton(onClick = { LiquidGlassStudio.resetAll(); validationMessage = "Reset Liquid Glass Studio to defaults." }) {
            Text("Reset Studio")
        }
    }

    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = jsonInput,
        onValueChange = { jsonInput = it },
        label = { Text("Paste Theme Package JSON to Validate & Import") },
        minLines = 3,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(6.dp))
    Button(
        enabled = jsonInput.isNotBlank(),
        onClick = {
            val result = ThemePackageValidator.validate(jsonInput)
            if (result.valid && result.parsedPackage != null) {
                LiquidGlassStudio.applyThemePackage(result.parsedPackage)
                validationMessage = "Valid package! Contrast ratio ${"%.1f".format(Locale.US, result.contrastRatio)}:1 (WCAG AA Pass). Applied '${result.parsedPackage.name}'."
                jsonInput = ""
            } else {
                validationMessage = "Validation failed: ${result.errors.joinToString("; ")}"
            }
        },
    ) {
        Text("Validate & Apply Package")
    }

    validationMessage?.let {
        Spacer(Modifier.height(6.dp))
        Text(it, fontSize = 12.sp, color = c.primary, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StudioSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    readout: String,
    onChange: (Float) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            Text(readout, fontSize = 12.sp, color = c.primary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}
