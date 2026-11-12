package com.tensei.ytxtube

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth

/** Hands the live player/library to the Labs sheet, which is opened from a nested settings section. */
object LabsBridge {
    var player: PlayerState? = null
    var lib: LibraryData? = null
    var openVideo: (VideoItem) -> Unit = {}
    var openChannel: (String) -> Unit = {}
}

/** One entry in the Labs hub: a feature area and how to show its settings. */
private enum class LabArea(val title: String) {
    THEME("Theme engine"), NAV("Navigation"), HOME("Home feed"), PLAYER("Player"),
    GESTURES("Gestures"), MINI("Mini player"), MUSIC("Music"), DOWNLOADS("Downloads"),
    PRIVACY("Privacy"), PREMIUM("Loading and extras"),
    GLASS("Glass studio"), VIDEO("Video studio"), AI("AI tools"), OFFLINE("Offline"),
    DISCOVERY("Discovery"), PRO_MUSIC("Music studio"), AUTOMATION("Automation"), PERF("Performance"),
}

/**
 * Entry point for the settings modules added from the 2.9.1 and 2.9.2 zips. Everything here is
 * compile-tested only; none of it has been run on a phone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabsHubSheet(
    player: PlayerState?,
    lib: LibraryData?,
    onOpenVideo: (VideoItem) -> Unit,
    onOpenChannel: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var area by remember { mutableStateOf<LabArea?>(null) }
    val c = MaterialTheme.colorScheme
    val dismiss = { area = null }

    FloatingSheet(onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 18.dp, bottom = 22.dp)) {
            Text("Labs", fontWeight = FontWeight.ExtraBold, fontSize = 21.sp)
            Text(
                "Experimental modules. Built and unit-tested in CI, not yet checked on a device.",
                color = c.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
            )
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LabArea.entries.forEach { a ->
                    FilterChip(selected = area == a, onClick = { area = if (area == a) null else a }, label = { Text(a.title) })
                }
            }
            when (area) {
                LabArea.THEME -> ThemeEngineSettingsContent(context)
                LabArea.NAV -> NavigationSettingsContent(context)
                LabArea.HOME -> HomeFeedBuilderSettingsContent(context)
                LabArea.PLAYER -> AdvancedPlayerSettingsContent(context)
                LabArea.GESTURES -> GestureDesignerSettingsContent(context)
                LabArea.MINI -> MiniPlayerSettingsContent(context)
                LabArea.MUSIC -> MusicExperienceSettingsContent(context)
                LabArea.DOWNLOADS -> DownloadManagerSettingsContent(context)
                LabArea.PRIVACY -> PrivacyPersonalisationSettingsContent(context)
                LabArea.PREMIUM -> PremiumExperienceSettingsContent(context)
                LabArea.GLASS -> LiquidGlassDesignStudioSheet(onDismiss = dismiss)
                LabArea.VIDEO -> if (player != null) VideoExperienceStudioSheet(player, dismiss)
                LabArea.AI -> if (player != null) AiVideoIntelligenceSheet(player, dismiss)
                LabArea.OFFLINE -> OfflineIntelligenceDashboardSheet(onOpenVideo = onOpenVideo, onDismiss = dismiss)
                LabArea.DISCOVERY -> if (lib != null) SmartDiscoveryAndSubsSheet(lib, onOpenVideo, onOpenChannel, dismiss)
                LabArea.PRO_MUSIC -> if (player != null) ProfessionalMusicStudioSheet(player, dismiss)
                LabArea.AUTOMATION -> AutomationAndPrivacySheet(onDismiss = dismiss)
                LabArea.PERF -> if (player != null) PerformanceAndExperimentalSheet(player, dismiss)
                null -> {}
            }
        }
    }
}
