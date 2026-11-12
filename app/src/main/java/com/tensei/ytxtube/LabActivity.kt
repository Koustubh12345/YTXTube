package com.tensei.ytxtube

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

private object LabGlassTokens {
    val cardShape = RoundedCornerShape(26.dp)
    val controlShape = RoundedCornerShape(18.dp)
    val pagePadding = 18.dp
    val sectionSpacing = 15.dp
}

/**
 * YTX Lab: settings that do not have a home in the main app yet.
 * Uses the same Kyant Backdrop + refractive glass material as YTXTube.
 */
class LabActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val scheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            MaterialTheme(colorScheme = scheme) { LabScreen() }
        }
    }
}

@Composable
private fun LabScreen() {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val backdrop = rememberLayerBackdrop()
    var status by remember { mutableStateOf<String?>(null) }
    var mode by remember { mutableStateOf(Backup.ConflictMode.Replace) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            status = runCatching { Backup.exportTo(context, uri); "Backup saved." }
                .getOrElse { "Backup failed: ${it.message}" }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            status = runCatching {
                val r = Backup.importFrom(context, uri, mode)
                buildString {
                    append("Restored ${r.applied} settings, skipped ${r.skipped}.")
                    if (r.ignoredFiles.isNotEmpty()) append(" Ignored: ${r.ignoredFiles.joinToString()}.")
                    if (r.needsRestart) append(" Restart the app to apply them.")
                }
            }.getOrElse { "Restore failed: ${it.message}" }
        }
    }

    val pageBrush = if (dark) {
        Brush.linearGradient(listOf(Color(0xFF080B13), Color(0xFF111A2B), Color(0xFF090D17)))
    } else {
        Brush.linearGradient(listOf(Color(0xFFEAF2FF), Color(0xFFF7F8FC), Color(0xFFE9F0FA)))
    }

    Box(Modifier.fillMaxSize().background(pageBrush)) {
        // Capture the ambient page artwork. Glass cards refract this layer rather
        // than simulating glass with a plain translucent Material surface.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = if (dark) listOf(Color(0x553B82F6), Color.Transparent)
                        else listOf(Color(0x334F8FE8), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(0.82f, 0.08f),
                        radius = 900f,
                    )
                )
                .layerBackdrop(backdrop)
        )

        Column(
            modifier = Modifier
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LabGlassTokens.pagePadding, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(LabGlassTokens.sectionSpacing),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(bottom = 3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Box(
                        Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF48A7FF))
                    )
                    Text(
                        "FEATURE WORKSPACE",
                        color = if (dark) Color(0xFF9CBCEB) else Color(0xFF52749F),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.7.sp,
                    )
                }
                Text(
                    "YTX Lab",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 34.sp,
                    lineHeight = 38.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    "Extra controls, playback tweaks and portable settings.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
                Text(
                    "Version ${BuildConfig.VERSION_NAME}  •  Based on YTXTube by Koustubh12345 (GPLv3)",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    fontSize = 10.sp,
                )
            }

            GlassSection("Dislike counts", backdrop) {
                ReturnDislikeSetting()
            }

            GlassSection("Playback", backdrop) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Skip silence", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Automatically skip silent portions during playback.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    }
                    Switch(checked = SkipSilence.enabled, onCheckedChange = { SkipSilence.toggle() })
                }
                GlassAction(
                    text = if (SleepTimer.minutes == 0) "Sleep timer  ·  Off" else "Sleep timer  ·  ${SleepTimer.minutes} min",
                    backdrop = backdrop,
                    onClick = { SleepTimer.cycle() },
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            }

            GlassSection("Backup & restore", backdrop) {
                Text(
                    "Save your settings to a file you choose. Sign-in data is never included.",
                    Modifier.padding(horizontal = 18.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
                Text(
                    "When a setting already exists",
                    Modifier.padding(horizontal = 18.dp, vertical = 2.dp),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                )
                ChipRow {
                    Backup.ConflictMode.entries.forEach { m ->
                        LabChip(m.label, mode == m, backdrop) { mode = m }
                    }
                }
                Row(
                    Modifier.padding(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    GlassAction(
                        text = "Back up",
                        backdrop = backdrop,
                        emphasized = true,
                        modifier = Modifier.weight(1f),
                        onClick = { exportLauncher.launch("ytx-backup.json") },
                    )
                    GlassAction(
                        text = "Restore",
                        backdrop = backdrop,
                        modifier = Modifier.weight(1f),
                        onClick = { importLauncher.launch(arrayOf("*/*")) },
                    )
                }
                status?.let {
                    Text(
                        it,
                        Modifier.padding(horizontal = 18.dp, vertical = 5.dp),
                        color = if (it.contains("failed", true)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun GlassSection(
    title: String,
    backdrop: com.kyant.backdrop.Backdrop,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(
                backdrop,
                glassSpec(
                    shape = LabGlassTokens.cardShape,
                    blur = 20.dp,
                    refractionHeight = 15.dp,
                    refractionAmount = 22.dp,
                ),
            )
            .padding(vertical = 17.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            title,
            Modifier.padding(horizontal = 18.dp),
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            letterSpacing = 0.2.sp,
        )
        content()
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun LabChip(
    text: String,
    selected: Boolean,
    backdrop: com.kyant.backdrop.Backdrop,
    onClick: () -> Unit,
) {
    val tint by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.23f)
        else MaterialTheme.colorScheme.surface.copy(alpha = 0.12f),
        animationSpec = spring(stiffness = 500f),
        label = "chipTint",
    )
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .liquidGlass(
                backdrop,
                glassSpec(
                    shape = CircleShape,
                    tint = tint,
                    blur = 13.dp,
                    refractionHeight = 8.dp,
                    refractionAmount = 12.dp,
                    chromaticAberration = false,
                ),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun GlassAction(
    text: String,
    backdrop: com.kyant.backdrop.Backdrop,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = LabGlassTokens.controlShape
    val tint = if (emphasized) MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
    else MaterialTheme.colorScheme.surface.copy(alpha = 0.12f)
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(shape)
            .liquidGlass(
                backdrop,
                glassSpec(
                    shape = shape,
                    tint = tint,
                    blur = 16.dp,
                    refractionHeight = 10.dp,
                    refractionAmount = 15.dp,
                    chromaticAberration = false,
                ),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
