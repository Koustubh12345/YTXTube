package com.tensei.ytxtube

import android.content.Context
import android.content.SharedPreferences
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

/**
 * LIQUID GLASS THEME ENGINE — Features 1 to 15
 *
 * Implements a comprehensive, modular theme architecture:
 * 1. Custom neon accent colours
 * 2. Adjustable background blur
 * 3. Surface transparency controls
 * 4. Border glow intensity
 * 5. Custom corner radius
 * 6. Custom primary and secondary HEX colours
 * 7. Theme import and export using JSON
 * 8. Live theme preview
 * 9. AMOLED black theme
 * 10. Adaptive system theme
 * 11. Per-screen themes
 * 12. Custom gradient backgrounds
 * 13. Dynamic thumbnail-based backgrounds
 * 14. Separate player and music themes
 * 15. Custom typography presets
 */

// --- Feature 1: Neon Accent Colours ---
data class NeonAccent(val name: String, val hex: String, val glowHex: String)

val NEON_ACCENTS = listOf(
    NeonAccent("Cyan Neon", "#00F5FF", "#00A8FF"),
    NeonAccent("Magenta Glow", "#FF007F", "#FF4081"),
    NeonAccent("Lime Pulse", "#39FF14", "#00E676"),
    NeonAccent("Electric Violet", "#8B00FF", "#6C2BD9"),
    NeonAccent("Solar Flare", "#FF7700", "#FF9100"),
    NeonAccent("Hot Pink", "#FF1493", "#F50057"),
    NeonAccent("Deep Amber", "#FFB300", "#FFA000")
)

// --- Feature 15: Typography Presets ---
enum class TypographyPreset(val label: String, val fontFamily: FontFamily) {
    SystemSans("System Sans", FontFamily.Default),
    RoundedHeavy("Rounded Heavy", AppRoundedHeavy),
    SerifEditorial("Serif Editorial", FontFamily.Serif),
    MonospaceTech("Monospace Tech", FontFamily.Monospace),
    CursiveExpressive("Cursive Soft", FontFamily.Cursive)
}

// --- Feature 12: Gradient Background Styles ---
enum class GradientBackgroundStyle(val label: String) {
    None("Solid Black/White"),
    SubtleMesh("Subtle Neon Mesh"),
    AuroraBorealis("Aurora Borealis"),
    NeonDusk("Neon Dusk"),
    SunsetGlow("Sunset Glow")
}

fun gradientBrushFor(style: GradientBackgroundStyle, dark: Boolean, accent: Color): Brush {
    return when (style) {
        GradientBackgroundStyle.None -> Brush.verticalGradient(
            listOf(if (dark) Color(0xFF0F0F0F) else Color(0xFFFCF4EE), if (dark) Color(0xFF0A0A0A) else Color(0xFFF6F2FF))
        )
        GradientBackgroundStyle.SubtleMesh -> Brush.radialGradient(
            colors = listOf(
                accent.copy(alpha = if (dark) 0.18f else 0.12f),
                if (dark) Color(0xFF101018) else Color(0xFFF9F7FC),
                if (dark) Color(0xFF08080C) else Color(0xFFF2F0F7)
            ),
            radius = 1200f
        )
        GradientBackgroundStyle.AuroraBorealis -> Brush.verticalGradient(
            listOf(
                Color(0xFF00384D).copy(alpha = if (dark) 0.85f else 0.15f),
                accent.copy(alpha = if (dark) 0.25f else 0.10f),
                if (dark) Color(0xFF0A0A14) else Color(0xFFFAF8FF)
            )
        )
        GradientBackgroundStyle.NeonDusk -> Brush.linearGradient(
            listOf(
                Color(0xFF280B3B).copy(alpha = if (dark) 0.85f else 0.15f),
                accent.copy(alpha = if (dark) 0.20f else 0.10f),
                if (dark) Color(0xFF06060A) else Color(0xFFF8F5FB)
            )
        )
        GradientBackgroundStyle.SunsetGlow -> Brush.verticalGradient(
            listOf(
                Color(0xFF3E1404).copy(alpha = if (dark) 0.75f else 0.12f),
                accent.copy(alpha = if (dark) 0.25f else 0.10f),
                if (dark) Color(0xFF0D0A08) else Color(0xFFFCF8F5)
            )
        )
    }
}

/**
 * State holder for all Liquid Glass Theme Engine configurations.
 */
object LiquidGlassThemeEngine {
    private const val PREFS = "liquid_glass_theme_engine"
    private var prefs: SharedPreferences? = null

    // 1. Neon Accent
    var activeNeonHex by mutableStateOf<String?>(null)
        private set

    // 2. Adjustable Blur (0dp to 32dp)
    var blurRadiusDp by mutableFloatStateOf(16f)
        private set

    // 3. Surface Transparency (0.1f to 0.9f)
    var surfaceAlpha by mutableFloatStateOf(0.40f)
        private set

    // 4. Border Glow Intensity (0.0f to 2.0f)
    var borderGlowIntensity by mutableFloatStateOf(1.0f)
        private set

    // 5. Custom Corner Radius (8dp to 36dp)
    var cornerRadiusDp by mutableIntStateOf(24)
        private set

    // 6. Custom Primary & Secondary HEX
    var customPrimaryHex by mutableStateOf<String?>(null)
        private set
    var customSecondaryHex by mutableStateOf<String?>(null)
        private set

    // 11. Per-screen theme overrides
    var homeThemeSeed by mutableStateOf<String?>(null)
        private set
    var playerThemeSeed by mutableStateOf<String?>(null)
        private set
    var musicThemeSeed by mutableStateOf<String?>(null)
        private set
    var libraryThemeSeed by mutableStateOf<String?>(null)
        private set

    // 12. Gradient Backgrounds
    var gradientBackground by mutableStateOf(GradientBackgroundStyle.None)
        private set

    // 13. Dynamic Thumbnail-based Backgrounds
    var dynamicThumbnailBackground by mutableStateOf(false)
        private set
    var currentExtractedColor by mutableStateOf<Color?>(null)

    // 14. Separate Player & Music Themes
    var separatePlayerTheme by mutableStateOf(false)
        private set
    var separateMusicTheme by mutableStateOf(false)
        private set

    // 15. Custom Typography Preset
    var typographyPreset by mutableStateOf(TypographyPreset.RoundedHeavy)
        private set

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).also { prefs = it }
        activeNeonHex = p.getString("neon_hex", null)
        blurRadiusDp = p.getFloat("blur_radius", 16f)
        surfaceAlpha = p.getFloat("surface_alpha", 0.40f)
        borderGlowIntensity = p.getFloat("border_glow", 1.0f)
        cornerRadiusDp = p.getInt("corner_radius", 24)
        customPrimaryHex = p.getString("custom_primary_hex", null)
        customSecondaryHex = p.getString("custom_secondary_hex", null)
        homeThemeSeed = p.getString("home_theme_seed", null)
        playerThemeSeed = p.getString("player_theme_seed", null)
        musicThemeSeed = p.getString("music_theme_seed", null)
        libraryThemeSeed = p.getString("library_theme_seed", null)
        val gradName = p.getString("gradient_bg", GradientBackgroundStyle.None.name) ?: GradientBackgroundStyle.None.name
        gradientBackground = runCatching { GradientBackgroundStyle.valueOf(gradName) }.getOrDefault(GradientBackgroundStyle.None)
        dynamicThumbnailBackground = p.getBoolean("dynamic_thumb_bg", false)
        separatePlayerTheme = p.getBoolean("sep_player_theme", false)
        separateMusicTheme = p.getBoolean("sep_music_theme", false)
        val typoName = p.getString("typography_preset", TypographyPreset.RoundedHeavy.name) ?: TypographyPreset.RoundedHeavy.name
        typographyPreset = runCatching { TypographyPreset.valueOf(typoName) }.getOrDefault(TypographyPreset.RoundedHeavy)
    }

    fun setNeonAccent(hex: String?) {
        activeNeonHex = hex
        prefs?.edit()?.putString("neon_hex", hex)?.apply()
    }

    fun updateBlurRadius(radius: Float) {
        blurRadiusDp = radius.coerceIn(0f, 32f)
        prefs?.edit()?.putFloat("blur_radius", blurRadiusDp)?.apply()
    }

    fun updateSurfaceAlpha(alpha: Float) {
        surfaceAlpha = alpha.coerceIn(0.1f, 0.95f)
        prefs?.edit()?.putFloat("surface_alpha", surfaceAlpha)?.apply()
    }

    fun updateBorderGlow(intensity: Float) {
        borderGlowIntensity = intensity.coerceIn(0f, 2.0f)
        prefs?.edit()?.putFloat("border_glow", borderGlowIntensity)?.apply()
    }

    fun updateCornerRadius(radiusDp: Int) {
        cornerRadiusDp = radiusDp.coerceIn(8, 36)
        prefs?.edit()?.putInt("corner_radius", cornerRadiusDp)?.apply()
    }

    fun setCustomColors(primaryHex: String?, secondaryHex: String?) {
        customPrimaryHex = primaryHex
        customSecondaryHex = secondaryHex
        prefs?.edit()
            ?.putString("custom_primary_hex", primaryHex)
            ?.putString("custom_secondary_hex", secondaryHex)
            ?.apply()
    }

    fun setScreenThemes(home: String?, player: String?, music: String?, library: String?) {
        homeThemeSeed = home
        playerThemeSeed = player
        musicThemeSeed = music
        libraryThemeSeed = library
        prefs?.edit()
            ?.putString("home_theme_seed", home)
            ?.putString("player_theme_seed", player)
            ?.putString("music_theme_seed", music)
            ?.putString("library_theme_seed", library)
            ?.apply()
    }

    fun updateGradientBackground(style: GradientBackgroundStyle) {
        gradientBackground = style
        prefs?.edit()?.putString("gradient_bg", style.name)?.apply()
    }

    fun updateDynamicThumbnailBackground(enabled: Boolean) {
        dynamicThumbnailBackground = enabled
        prefs?.edit()?.putBoolean("dynamic_thumb_bg", enabled)?.apply()
    }

    fun updateSeparatePlayerTheme(enabled: Boolean) {
        separatePlayerTheme = enabled
        prefs?.edit()?.putBoolean("sep_player_theme", enabled)?.apply()
    }

    fun updateSeparateMusicTheme(enabled: Boolean) {
        separateMusicTheme = enabled
        prefs?.edit()?.putBoolean("sep_music_theme", enabled)?.apply()
    }

    fun updateTypographyPreset(preset: TypographyPreset) {
        typographyPreset = preset
        prefs?.edit()?.putString("typography_preset", preset.name)?.apply()
    }

    // --- Feature 7: JSON Theme Import and Export ---
    fun exportThemeJson(): String {
        val json = JSONObject()
        json.put("version", 1)
        json.put("neon_hex", activeNeonHex ?: "")
        json.put("blur_radius", blurRadiusDp.toDouble())
        json.put("surface_alpha", surfaceAlpha.toDouble())
        json.put("border_glow", borderGlowIntensity.toDouble())
        json.put("corner_radius", cornerRadiusDp)
        json.put("custom_primary_hex", customPrimaryHex ?: "")
        json.put("custom_secondary_hex", customSecondaryHex ?: "")
        json.put("gradient_bg", gradientBackground.name)
        json.put("dynamic_thumb_bg", dynamicThumbnailBackground)
        json.put("sep_player_theme", separatePlayerTheme)
        json.put("sep_music_theme", separateMusicTheme)
        json.put("typography_preset", typographyPreset.name)
        json.put("home_theme_seed", homeThemeSeed ?: "")
        json.put("player_theme_seed", playerThemeSeed ?: "")
        json.put("music_theme_seed", musicThemeSeed ?: "")
        json.put("library_theme_seed", libraryThemeSeed ?: "")
        return json.toString(2)
    }

    fun importThemeJson(jsonString: String): Boolean {
        return runCatching {
            val json = JSONObject(jsonString)
            val neon = json.optString("neon_hex", "").takeIf { it.isNotBlank() }
            val blur = json.optDouble("blur_radius", 16.0).toFloat()
            val alpha = json.optDouble("surface_alpha", 0.40).toFloat()
            val glow = json.optDouble("border_glow", 1.0).toFloat()
            val radius = json.optInt("corner_radius", 24)
            val prim = json.optString("custom_primary_hex", "").takeIf { it.isNotBlank() }
            val sec = json.optString("custom_secondary_hex", "").takeIf { it.isNotBlank() }
            val grad = runCatching {
                GradientBackgroundStyle.valueOf(json.optString("gradient_bg", GradientBackgroundStyle.None.name))
            }.getOrDefault(GradientBackgroundStyle.None)
            val dynThumb = json.optBoolean("dynamic_thumb_bg", false)
            val sepPlay = json.optBoolean("sep_player_theme", false)
            val sepMusic = json.optBoolean("sep_music_theme", false)
            val typo = runCatching {
                TypographyPreset.valueOf(json.optString("typography_preset", TypographyPreset.RoundedHeavy.name))
            }.getOrDefault(TypographyPreset.RoundedHeavy)

            setNeonAccent(neon)
            updateBlurRadius(blur)
            updateSurfaceAlpha(alpha)
            updateBorderGlow(glow)
            updateCornerRadius(radius)
            setCustomColors(prim, sec)
            updateGradientBackground(grad)
            updateDynamicThumbnailBackground(dynThumb)
            updateSeparatePlayerTheme(sepPlay)
            updateSeparateMusicTheme(sepMusic)
            updateTypographyPreset(typo)

            val h = json.optString("home_theme_seed", "").takeIf { it.isNotBlank() }
            val p = json.optString("player_theme_seed", "").takeIf { it.isNotBlank() }
            val m = json.optString("music_theme_seed", "").takeIf { it.isNotBlank() }
            val l = json.optString("library_theme_seed", "").takeIf { it.isNotBlank() }
            setScreenThemes(h, p, m, l)
            true
        }.getOrDefault(false)
    }
}

/**
 * Feature 8: Live Theme Preview Composable
 * Renders an interactive live visual preview of the liquid glass styling,
 * neon accents, typography, and controls.
 */
@Composable
fun LiveThemePreviewCard(
    modifier: Modifier = Modifier
) {
    val dark = isDarkTheme()
    val isAmoled = ThemeSettings.mode == "amoled"
    val accentHex = LiquidGlassThemeEngine.activeNeonHex ?: ThemeSettings.seed ?: SEED_CREAM_ROSE
    val accentColor = runCatching {
        Color(android.graphics.Color.parseColor(accentHex))
    }.getOrDefault(MaterialTheme.colorScheme.primary)

    val cornerRadius = LiquidGlassThemeEngine.cornerRadiusDp.dp
    val shape = RoundedCornerShape(cornerRadius)
    val glowIntensity = LiquidGlassThemeEngine.borderGlowIntensity
    val alpha = LiquidGlassThemeEngine.surfaceAlpha

    val bgTint = if (isAmoled) {
        Color.Black.copy(alpha = alpha.coerceAtLeast(0.6f))
    } else if (dark) {
        Color(0xFF14141E).copy(alpha = alpha)
    } else {
        Color(0xFFFFFFFF).copy(alpha = alpha)
    }

    val borderColor = accentColor.copy(alpha = (0.35f * glowIntensity).coerceIn(0.1f, 1f))

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bgTint)
            .border(
                width = (1.5f * glowIntensity).coerceIn(1f, 3f).dp,
                color = borderColor,
                shape = shape
            )
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(accentColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Palette,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Liquid Glass Live",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = LiquidGlassThemeEngine.typographyPreset.fontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Blur: ${LiquidGlassThemeEngine.blurRadiusDp.toInt()}dp · Radius: ${LiquidGlassThemeEngine.cornerRadiusDp}dp",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(accentColor.copy(alpha = 0.2f))
                        .border(1.dp, accentColor, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isAmoled) "AMOLED" else if (dark) "DARK" else "LIGHT",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                }
            }

            // Preview seekbar & interactive elements
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(accentColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(accentColor.copy(alpha = 0.25f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.65f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(accentColor)
                    )
                }

                Text(
                    text = "03:42",
                    fontSize = 11.sp,
                    fontFamily = LiquidGlassThemeEngine.typographyPreset.fontFamily,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
