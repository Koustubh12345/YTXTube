package com.tensei.ytxtube

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.ColorDrawable
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.abs

val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)





val AppRoundedHeavy = FontFamily(
    Font(DeviceFontFamilyName("sans-serif-rounded"), weight = FontWeight.ExtraBold),
    Font(DeviceFontFamilyName("sans-serif-rounded"), weight = FontWeight.Bold),
    Font(DeviceFontFamilyName("sans-serif"), weight = FontWeight.ExtraBold),
    Font(DeviceFontFamilyName("sans-serif"), weight = FontWeight.Bold),
)

val RoundedHeavy: FontFamily
    get() = if (ThemeSettings.useSystemFont) FontFamily.Default else AppRoundedHeavy






private fun hexToHsl(hex: String): Triple<Float, Float, Float> {
    var h2 = hex.removePrefix("#")
    if (h2.length == 3) h2 = h2.map { "$it$it" }.joinToString("")
    val r = h2.substring(0, 2).toInt(16) / 255f
    val g = h2.substring(2, 4).toInt(16) / 255f
    val b = h2.substring(4, 6).toInt(16) / 255f
    val mx = maxOf(r, g, b); val mn = minOf(r, g, b); val l = (mx + mn) / 2f
    var h = 0f; var s = 0f; val d = mx - mn
    if (d != 0f) {
        s = d / (1 - abs(2 * l - 1))
        h = when (mx) {
            r -> (((g - b) / d) % 6)
            g -> ((b - r) / d + 2)
            else -> ((r - g) / d + 4)
        } * 60f
        if (h < 0) h += 360f
    }
    return Triple(h, s * 100f, l * 100f)
}

private fun hsl(h: Float, s: Float, l: Float): Color {
    val sN = s.coerceIn(0f, 100f) / 100f
    val lN = l.coerceIn(0f, 100f) / 100f
    val c = (1 - abs(2 * lN - 1)) * sN
    val x = c * (1 - abs(((h / 60f) % 2) - 1))
    val m = lN - c / 2
    val (r, g, b) = when {
        h < 60 -> Triple(c, x, 0f)
        h < 120 -> Triple(x, c, 0f)
        h < 180 -> Triple(0f, c, x)
        h < 240 -> Triple(0f, x, c)
        h < 300 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color((r + m).coerceIn(0f, 1f), (g + m).coerceIn(0f, 1f), (b + m).coerceIn(0f, 1f))
}


data class ThemeChoice(val name: String, val hex: String, val isNew: Boolean = false)











const val SEED_CREAM_ROSE = "#833761"
fun normalizedThemeSeed(seed: String): String =
    if (seed.lowercase() in setOf("#1f8a70", "#2e7d32", "#b5651d")) SEED_CREAM_ROSE else seed

const val SEED_AURORA = "#6C2BD9"
const val SEED_LILAC_AIR = "#7454CE"
const val SEED_JAI       = "#D6005C"
const val SEED_PUMPKIN   = "#B0714A"
const val SEED_MOONSTONE = "#8A8F98"

val THEME_CHOICES = listOf(
    ThemeChoice("Cream Rose", SEED_CREAM_ROSE, isNew = true),
    ThemeChoice("Erica", SEED_AURORA, isNew = true),
    ThemeChoice("Lilac Air", SEED_LILAC_AIR, isNew = true),
    ThemeChoice("Jai",       SEED_JAI,       isNew = true),
    ThemeChoice("Pumpkin",   SEED_PUMPKIN,   isNew = true),
    ThemeChoice("Moonstone", SEED_MOONSTONE, isNew = true),
    ThemeChoice("Iris",   "#6750A4"),
    ThemeChoice("Harbor", "#3B5BDB"),
    



)

fun seedScheme(seedHex: String, dark: Boolean): ColorScheme {
    if (normalizedThemeSeed(seedHex).equals(SEED_CREAM_ROSE, ignoreCase = true)) {
        return if (!dark) lightColorScheme(
            primary = Color(0xFF833761),
            onPrimary = Color(0xFFFFF3FC),
            primaryContainer = Color(0xFFEED6DF),
            onPrimaryContainer = Color(0xFF54233F),
            secondaryContainer = Color(0xFFE8DBEF),
            onSecondaryContainer = Color(0xFF513568),
            surface = Color(0xFFFCF4EE),
            surfaceContainer = Color(0xFFFFF6F7),
            surfaceContainerHigh = Color(0xFFEED6DF),
            surfaceContainerHighest = Color(0xFFE8DBEF),
            onSurface = Color(0xFF422334),
            onSurfaceVariant = Color(0xFF735165),
            outline = Color(0xFFA88098),
            outlineVariant = Color.Transparent,
            background = Color(0xFFFCF4EE),
            onBackground = Color(0xFF422334)
        ) else darkColorScheme(

            primary = Color(0xFFF0B4CC),
            onPrimary = Color(0xFF3A1628),
            primaryContainer = Color(0xFF4A2A3C),
            onPrimaryContainer = Color(0xFFFBE3EE),
            secondaryContainer = Color(0xFF30242B),
            onSecondaryContainer = Color(0xFFEDDEE6),
            surface = Color(0xFF0E0A0D),
            surfaceContainer = Color(0xFF1F181C),
            surfaceContainerHigh = Color(0xFF2A2126),
            surfaceContainerHighest = Color(0xFF3A2E35),
            onSurface = Color(0xFFFBEFF2),
            onSurfaceVariant = Color(0xFFCDB8C3),
            outline = Color(0xFF9A858F),
            outlineVariant = Color.Transparent,
            background = Color(0xFF0E0A0D),
            onBackground = Color(0xFFFBEFF2)
        )
    }

    if (seedHex.equals(SEED_AURORA, ignoreCase = true)) {
        return if (!dark) lightColorScheme(
            primary = Color(0xFF6C2BD9),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFE7DCFF),
            onPrimaryContainer = Color(0xFF2A1064),
            secondaryContainer = Color(0xFFD5F0F6),
            onSecondaryContainer = Color(0xFF0A3A45),
            surface = Color(0xFFF6F4FE),
            surfaceContainer = Color(0xFFFCFBFF),
            surfaceContainerHigh = Color(0xFFEDE9FA),
            surfaceContainerHighest = Color(0xFFE1DBF3),
            onSurface = Color(0xFF191340),
            onSurfaceVariant = Color(0xFF544D80),
            outline = Color(0xFF8B84B0),
            outlineVariant = Color(0xFFDCD5F1),
            background = Color(0xFFF6F4FE),
            onBackground = Color(0xFF191340)
        ) else darkColorScheme(
            primary = Color(0xFFD4C0FF),
            onPrimary = Color(0xFF23104F),
            primaryContainer = Color(0xFF33206B),
            onPrimaryContainer = Color(0xFFE6DBFF),
            secondaryContainer = Color(0xFF12303A),
            onSecondaryContainer = Color(0xFFC9EDF5),
            surface = Color(0xFF0A0817),
            surfaceContainer = Color(0xFF1D1B29),
            surfaceContainerHigh = Color(0xFF1F1A3B),
            surfaceContainerHighest = Color(0xFF2A2350),
            onSurface = Color(0xFFF1EDFF),
            onSurfaceVariant = Color(0xFFC9C2E0),
            outline = Color(0xFF7E76A8),
            outlineVariant = Color(0xFF2D2B38),
            background = Color(0xFF0A0817),
            onBackground = Color(0xFFF1EDFF)
        )
    }

    if (seedHex.equals(SEED_LILAC_AIR, ignoreCase = true)) {
        return if (!dark) lightColorScheme(
            primary = Color(0xFF7454CE),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFEFE7FB),
            onPrimaryContainer = Color(0xFF59408C),
            secondaryContainer = Color(0xFFF7EFF9),
            onSecondaryContainer = Color(0xFF49345E),
            surface = Color(0xFFF6F2FF),
            surfaceContainer = Color(0xFFFAF7FF),
            surfaceContainerHigh = Color(0xFFEEE5F8),
            surfaceContainerHighest = Color(0xFFE6DAF4),
            onSurface = Color(0xFF30224E),
            onSurfaceVariant = Color(0xFF78648F),
            outline = Color(0xFF9580AC),
            outlineVariant = Color(0xFFE2D5EE),
            background = Color(0xFFF6F2FF),
            onBackground = Color(0xFF30224E)
        ) else darkColorScheme(
            primary = Color(0xFFC5B1F0),
            onPrimary = Color(0xFF291744),
            primaryContainer = Color(0xFF392954),
            onPrimaryContainer = Color(0xFFEADDFB),
            secondaryContainer = Color(0xFF292131),
            onSecondaryContainer = Color(0xFFDFD0EA),
            surface = Color(0xFF17121F),
            surfaceContainer = Color(0xFF261E33),
            surfaceContainerHigh = Color(0xFF322641),
            surfaceContainerHighest = Color(0xFF40304F),
            onSurface = Color(0xFFF1E9FA),
            onSurfaceVariant = Color(0xFFC7B6D7),
            outline = Color(0xFF9A85AE),
            outlineVariant = Color(0xFF514060),
            background = Color(0xFF17121F),
            onBackground = Color(0xFFF1E9FA)
        )
    }
    









    if (seedHex.equals(SEED_JAI, ignoreCase = true)) {
        







        return if (!dark) lightColorScheme(
            primary = Color(0xFFD6005C), onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFFFD3E4), onPrimaryContainer = Color(0xFF4D0020),
            secondary = Color(0xFF8C5069), onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFFBE4EC), onSecondaryContainer = Color(0xFF452029),
            tertiary = Color(0xFF94304F),
            background = Color(0xFFFFF7FA), onBackground = Color(0xFF2B0F1A),
            surface = Color(0xFFFFF7FA), onSurface = Color(0xFF2B0F1A),
            surfaceVariant = Color(0xFFF7D6E4), onSurfaceVariant = Color(0xFF77455A),
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = Color(0xFFFFFAFC),
            surfaceContainer = Color(0xFFFFEDF4),
            surfaceContainerHigh = Color(0xFFFCE3ED),
            surfaceContainerHighest = Color(0xFFF7D6E4),
            outline = Color(0xFFAC7189), outlineVariant = Color(0xFFEFCFDE),
            error = Color(0xFFB3261E), onError = Color.White,
            errorContainer = Color(0xFFFFDAD5), onErrorContainer = Color(0xFF410E0B)
        ) else darkColorScheme(
            primary = Color(0xFFFF6BA0), onPrimary = Color(0xFF3D0016),
            primaryContainer = Color(0xFF8F0F45), onPrimaryContainer = Color(0xFFFFD8E7),
            secondary = Color(0xFFE2BCC9), onSecondary = Color(0xFF332028),
            secondaryContainer = Color(0xFF332028), onSecondaryContainer = Color(0xFFF2DCE4),
            tertiary = Color(0xFFE7A0B8),
            background = Color(0xFF150A10), onBackground = Color(0xFFF8EBF0),
            surface = Color(0xFF150A10), onSurface = Color(0xFFF8EBF0),
            surfaceVariant = Color(0xFF4A2E3B), onSurfaceVariant = Color(0xFFD5B3C1),
            surfaceContainerLowest = Color(0xFF0D0509),
            surfaceContainerLow = Color(0xFF1B0F15),
            surfaceContainer = Color(0xFF22131A),
            surfaceContainerHigh = Color(0xFF2F1B25),
            surfaceContainerHighest = Color(0xFF3D2430),
            outline = Color(0xFF9C7382), outlineVariant = Color(0xFF4C333D),
            error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
            errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6)
        )
    }
    if (seedHex.equals(SEED_PUMPKIN, ignoreCase = true)) {
        return if (!dark) lightColorScheme(
            primary = Color(0xFFBC5628), onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFFBE2CE), onPrimaryContainer = Color(0xFF4A2110),
            secondary = Color(0xFF7A6248), onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFF2E7D6), onSecondaryContainer = Color(0xFF4A3A28),
            tertiary = Color(0xFF9A6B2F),
            background = Color(0xFFFDFAF6), onBackground = Color(0xFF2A211A),
            surface = Color(0xFFFDFAF6), onSurface = Color(0xFF2A211A),
            surfaceVariant = Color(0xFFEADCC9), onSurfaceVariant = Color(0xFF6F5F4F),
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = Color(0xFFFBF6EF),
            surfaceContainer = Color(0xFFF8F1E7),
            surfaceContainerHigh = Color(0xFFF3E9DB),
            surfaceContainerHighest = Color(0xFFEADCC9),
            outline = Color(0xFF97866F), outlineVariant = Color(0xFFE2D6C4),
            error = Color(0xFFB3261E), onError = Color.White,
            errorContainer = Color(0xFFFFDAD5), onErrorContainer = Color(0xFF410E0B)
        ) else darkColorScheme(
            








            primary = Color(0xFFE97A3A), onPrimary = Color(0xFF2A1200),
            primaryContainer = Color(0xFF63300F), onPrimaryContainer = Color(0xFFFFD9BE),
            secondary = Color(0xFFCFC4BB), onSecondary = Color(0xFF2E2A27),
            secondaryContainer = Color(0xFF2E2A27), onSecondaryContainer = Color(0xFFE3DBD4),
            tertiary = Color(0xFFE0A472),
            background = Color(0xFF121110), onBackground = Color(0xFFF4F1EE),
            surface = Color(0xFF121110), onSurface = Color(0xFFF4F1EE),
            surfaceVariant = Color(0xFF3F3A36), onSurfaceVariant = Color(0xFFB4ABA4),
            surfaceContainerLowest = Color(0xFF0B0A0A),
            surfaceContainerLow = Color(0xFF171615),
            surfaceContainer = Color(0xFF1C1A19),
            surfaceContainerHigh = Color(0xFF262322),
            surfaceContainerHighest = Color(0xFF322E2C),
            outline = Color(0xFF7C736C), outlineVariant = Color(0xFF3A3532),
            error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
            errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6)
        )
    }
    val (h, s, _) = hexToHsl(seedHex)
    








    val isMoonstone = seedHex.equals(SEED_MOONSTONE, ignoreCase = true)
    val isPumpkin = seedHex.equals(SEED_PUMPKIN, ignoreCase = true)
    val accent = when {
        isMoonstone -> 7f
        isPumpkin -> 37f
        else -> maxOf(s, 45f)
    }

    val surf = when {
        isMoonstone -> 3f
        isPumpkin -> 24f
        else -> if (dark) 12f else 14f
    }
    return if (!dark) lightColorScheme(
        primary = hsl(h, accent, 42f), onPrimary = Color.White,
        primaryContainer = hsl(h, minOf(accent, 68f), 90f), onPrimaryContainer = hsl(h, accent, 16f),
        secondary = hsl(h, 26f, 40f), onSecondary = Color.White,
        secondaryContainer = hsl(h, 22f, 88f), onSecondaryContainer = hsl(h, 26f, 20f),
        tertiary = hsl((h + 40f) % 360f, 40f, 45f),
        background = hsl(h, surf, 98f), onBackground = hsl(h, 10f, 12f),
        surface = hsl(h, surf, 98f), onSurface = hsl(h, 10f, 12f),
        surfaceVariant = hsl(h, surf, 90f), onSurfaceVariant = hsl(h, minOf(12f, surf), 36f),
        surfaceContainerLowest = hsl(h, surf, 100f),
        surfaceContainerLow = hsl(h, surf, 96f),
        surfaceContainer = hsl(h, surf, 95f),
        surfaceContainerHigh = hsl(h, surf, 93f),
        surfaceContainerHighest = hsl(h, surf, 90f),
        outline = hsl(h, minOf(10f, surf), 54f), outlineVariant = hsl(h, minOf(10f, surf), 84f),
        error = Color(0xFFB3261E), onError = Color.White, errorContainer = Color(0xFFFFDAD5), onErrorContainer = Color(0xFF410E0B)
    ) else darkColorScheme(
        primary = hsl(h, minOf(accent, 70f), 80f), onPrimary = hsl(h, accent, 18f),
        primaryContainer = hsl(h, minOf(accent, 58f), 32f), onPrimaryContainer = hsl(h, 60f, 90f),
        secondary = hsl(h, 18f, 80f), onSecondary = hsl(h, 20f, 20f),
        secondaryContainer = hsl(h, 16f, 30f), onSecondaryContainer = hsl(h, 18f, 88f),
        tertiary = hsl((h + 40f) % 360f, 35f, 80f),
        background = hsl(h, surf, 7f), onBackground = hsl(h, 8f, 91f),
        surface = hsl(h, surf, 7f), onSurface = hsl(h, 8f, 91f),
        surfaceVariant = hsl(h, surf, 30f), onSurfaceVariant = hsl(h, minOf(10f, surf), 80f),
        surfaceContainerLowest = hsl(h, surf, 4f),
        surfaceContainerLow = hsl(h, surf, 10f),
        surfaceContainer = hsl(h, surf, 12f),
        surfaceContainerHigh = hsl(h, surf, 17f),
        surfaceContainerHighest = hsl(h, surf, 22f),
        outline = hsl(h, minOf(8f, surf), 60f), outlineVariant = hsl(h, minOf(8f, surf), 32f),
        error = Color(0xFFFFB4AB), onError = Color(0xFF690005), errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6)
    )
}








private fun completeLedgiiScheme(seedHex: String, cs: ColorScheme): ColorScheme {
    val sparse = seedHex.lowercase() in setOf(SEED_CREAM_ROSE.lowercase(), SEED_AURORA.lowercase(), SEED_LILAC_AIR.lowercase())
    val filled = cs.copy(tertiaryContainer = cs.secondaryContainer, onTertiaryContainer = cs.onSecondaryContainer)
    return if (!sparse) filled else filled.copy(
        secondary = cs.primary, tertiary = cs.primary,
        surfaceVariant = cs.surfaceContainerHigh,

        surfaceContainerLow = androidx.compose.ui.graphics.lerp(cs.surface, cs.surfaceContainer, 0.5f),
        surfaceContainerLowest = cs.surface,
    )
}






object ThemeSettings {
    private var prefs: SharedPreferences? = null
    val wallpaperAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    var seed by mutableStateOf<String?>(null)
        private set

    var mode by mutableStateOf("system")
        private set
    var useSystemFont by mutableStateOf(true)
        private set

    fun init(context: Context) {
        val p = context.getSharedPreferences("appearance", Context.MODE_PRIVATE).also { prefs = it }
        seed = p.getString("seed", null) ?: SEED_YOUTUBE
        mode = p.getString("mode", "system") ?: "system"
        useSystemFont = p.getBoolean("use_system_font", true)
    }

    fun choose(seedHex: String?) {
        seed = seedHex
        prefs?.edit()?.putString("seed", seedHex)?.apply()
    }

    fun chooseMode(value: String) {
        mode = value
        prefs?.edit()?.putString("mode", value)?.apply()
    }

    fun updateUseSystemFont(enabled: Boolean) {
        useSystemFont = enabled
        prefs?.edit()?.putBoolean("use_system_font", enabled)?.apply()
    }
}


const val SEED_YOUTUBE = "#FF0033"

private fun youTubeScheme(dark: Boolean): ColorScheme = if (!dark) lightColorScheme(
    primary = Color(0xFFE6002E), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE3E8), onPrimaryContainer = Color(0xFF5C0012),
    secondary = Color(0xFF606060), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E0E0), onSecondaryContainer = Color(0xFF0F0F0F),
    tertiary = Color(0xFFE6002E), tertiaryContainer = Color(0xFFF2F2F2), onTertiaryContainer = Color(0xFF0F0F0F),
    background = Color.White, onBackground = Color(0xFF0F0F0F),
    surface = Color.White, onSurface = Color(0xFF0F0F0F),
    surfaceVariant = Color(0xFFE5E5E5), onSurfaceVariant = Color(0xFF606060),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF9F9F9),
    surfaceContainer = Color(0xFFF2F2F2), surfaceContainerHigh = Color(0xFFEAEAEA),
    surfaceContainerHighest = Color(0xFFE0E0E0),
    outline = Color(0xFF909090), outlineVariant = Color(0xFFE5E5E5),
) else darkColorScheme(
    primary = Color(0xFFFF0033), onPrimary = Color.White,
    primaryContainer = Color(0xFF4A0010), onPrimaryContainer = Color(0xFFFFD9DF),
    secondary = Color(0xFFAAAAAA), onSecondary = Color(0xFF0F0F0F),
    secondaryContainer = Color(0xFF3F3F3F), onSecondaryContainer = Color(0xFFF1F1F1),
    tertiary = Color(0xFFFF4E45), tertiaryContainer = Color(0xFF272727), onTertiaryContainer = Color(0xFFF1F1F1),
    background = Color(0xFF0F0F0F), onBackground = Color(0xFFF1F1F1),
    surface = Color(0xFF0F0F0F), onSurface = Color(0xFFF1F1F1),
    surfaceVariant = Color(0xFF272727), onSurfaceVariant = Color(0xFFAAAAAA),
    surfaceContainerLowest = Color(0xFF0A0A0A), surfaceContainerLow = Color(0xFF181818),
    surfaceContainer = Color(0xFF212121), surfaceContainerHigh = Color(0xFF2C2C2C),
    surfaceContainerHighest = Color(0xFF3A3A3A),
    outline = Color(0xFF717171), outlineVariant = Color(0xFF303030),
)


val YTX_THEMES = listOf(ThemeChoice("YouTube", SEED_YOUTUBE)) + THEME_CHOICES


fun schemeFor(context: Context, seedHex: String?, dark: Boolean): ColorScheme =
    if (seedHex == null && ThemeSettings.wallpaperAvailable) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (seedHex.equals(SEED_YOUTUBE, ignoreCase = true)) {
        youTubeScheme(dark)
    } else {
        val seedValue = seedHex ?: SEED_CREAM_ROSE
        completeLedgiiScheme(seedValue, seedScheme(seedValue, dark))
    }

@Composable
fun isDarkTheme(): Boolean = when (ThemeSettings.mode) {
    "dark", "amoled" -> true
    "light" -> false
    else -> isSystemInDarkTheme()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YtxTubeTheme(content: @Composable () -> Unit) {
    val dark = isDarkTheme()
    val isAmoled = ThemeSettings.mode == "amoled"
    val context = LocalContext.current
    val baseScheme = schemeFor(context, ThemeSettings.seed, dark)
    val scheme = if (isAmoled) {
        baseScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceVariant = Color(0xFF141414),
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF050505),
            surfaceContainer = Color(0xFF0C0C0C),
            surfaceContainerHigh = Color(0xFF141414),
            surfaceContainerHighest = Color(0xFF1E1E1E),
            onBackground = Color(0xFFF5F5F5),
            onSurface = Color(0xFFF5F5F5),
            outlineVariant = Color(0xFF1F1F1F),
        )
    } else baseScheme

    SystemBars(dark, scheme.surface)
    MaterialTheme(colorScheme = scheme, shapes = ExpressiveShapes, typography = Typography()) {
        CompositionLocalProvider(

            LocalRippleConfiguration provides null,


            LocalContentColor provides scheme.onSurface,
            content = content,
        )
    }
}






@Composable
private fun SystemBars(dark: Boolean, windowBg: Color) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !dark
        controller.isAppearanceLightNavigationBars = !dark
    }
    DisposableEffect(windowBg) {
        (view.context as? Activity)?.window?.setBackgroundDrawable(ColorDrawable(windowBg.toArgb()))
        onDispose { }
    }
}
