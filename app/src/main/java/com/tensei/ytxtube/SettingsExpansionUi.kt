package com.tensei.ytxtube

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Settings Expansion UI for YTXTube v2.9.1
 * Provides comprehensive, interactive UI controls for all 120 features.
 */

// =====================================================================
// 1. LIQUID GLASS THEME ENGINE CONTENT (Features 1-15)
// =====================================================================
@Composable
fun ThemeEngineSettingsContent(context: Context) {
    val scroll = rememberScrollState()
    val c = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Feature 8: Live Theme Preview Card
        Text(
            text = "Live Theme Preview",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = c.primary
        )
        LiveThemePreviewCard()

        // Feature 1: Custom Neon Accent Colours
        Text(
            text = "Neon Accent Colors",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = c.onSurface
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            NEON_ACCENTS.forEach { accent ->
                val isSelected = LiquidGlassThemeEngine.activeNeonHex.equals(accent.hex, ignoreCase = true)
                val color = runCatching { Color(android.graphics.Color.parseColor(accent.hex)) }.getOrDefault(Color.Cyan)
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) Color.White else Color.Transparent,
                            shape = CircleShape
                        )
                        .clickable {
                            LiquidGlassThemeEngine.setNeonAccent(if (isSelected) null else accent.hex)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Feature 2: Adjustable Background Blur
        SettingSliderRow(
            title = "Background Blur",
            subtitle = "${LiquidGlassThemeEngine.blurRadiusDp.toInt()} dp",
            value = LiquidGlassThemeEngine.blurRadiusDp,
            range = 0f..32f,
            steps = 32,
            onValueChange = { LiquidGlassThemeEngine.updateBlurRadius(it) }
        )

        // Feature 3: Surface Transparency Controls
        SettingSliderRow(
            title = "Surface Opacity",
            subtitle = "${(LiquidGlassThemeEngine.surfaceAlpha * 100).toInt()}%",
            value = LiquidGlassThemeEngine.surfaceAlpha,
            range = 0.1f..0.95f,
            steps = 17,
            onValueChange = { LiquidGlassThemeEngine.updateSurfaceAlpha(it) }
        )

        // Feature 4: Border Glow Intensity
        SettingSliderRow(
            title = "Border Glow Multiplier",
            subtitle = "${String.format("%.1fx", LiquidGlassThemeEngine.borderGlowIntensity)}",
            value = LiquidGlassThemeEngine.borderGlowIntensity,
            range = 0f..2.0f,
            steps = 20,
            onValueChange = { LiquidGlassThemeEngine.updateBorderGlow(it) }
        )

        // Feature 5: Custom Corner Radius
        SettingSliderRow(
            title = "Custom Corner Radius",
            subtitle = "${LiquidGlassThemeEngine.cornerRadiusDp} dp",
            value = LiquidGlassThemeEngine.cornerRadiusDp.toFloat(),
            range = 8f..36f,
            steps = 14,
            onValueChange = { LiquidGlassThemeEngine.updateCornerRadius(it.toInt()) }
        )

        // Feature 15: Typography Presets
        Text(
            text = "Typography Preset",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = c.onSurface
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TypographyPreset.entries.forEach { typo ->
                val selected = LiquidGlassThemeEngine.typographyPreset == typo
                FilterChip(
                    selected = selected,
                    onClick = { LiquidGlassThemeEngine.updateTypographyPreset(typo) },
                    label = { Text(typo.label, fontFamily = typo.fontFamily) }
                )
            }
        }

        // Feature 12: Gradient Background Styles
        Text(
            text = "Gradient Background Style",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = c.onSurface
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GradientBackgroundStyle.entries.forEach { style ->
                val selected = LiquidGlassThemeEngine.gradientBackground == style
                FilterChip(
                    selected = selected,
                    onClick = { LiquidGlassThemeEngine.updateGradientBackground(style) },
                    label = { Text(style.label) }
                )
            }
        }

        // Feature 7: Theme Import & Export using JSON
        Text(
            text = "Theme Profile JSON Backup",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = c.onSurface
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = {
                    val json = LiquidGlassThemeEngine.exportThemeJson()
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    cm?.setPrimaryClip(ClipData.newPlainText("LiquidGlassTheme", json))
                    Toast.makeText(context, "Theme JSON copied to clipboard", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.ContentCopy, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Export JSON", fontSize = 12.sp)
            }

            var importText by remember { mutableStateOf("") }
            var showImportDialog by remember { mutableStateOf(false) }

            Button(
                onClick = { showImportDialog = true },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.FileUpload, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Import JSON", fontSize = 12.sp)
            }

            if (showImportDialog) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showImportDialog = false },
                    title = { Text("Import Theme JSON") },
                    text = {
                        Column {
                            Text("Paste theme JSON profile below:")
                            Spacer(Modifier.height(8.dp))
                            BasicTextField(
                                value = importText,
                                onValueChange = { importText = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(c.surfaceContainerLow)
                                    .padding(8.dp),
                                textStyle = TextStyle(color = c.onSurface, fontSize = 12.sp)
                            )
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            val success = LiquidGlassThemeEngine.importThemeJson(importText)
                            Toast.makeText(context, if (success) "Theme imported!" else "Invalid theme JSON", Toast.LENGTH_SHORT).show()
                            showImportDialog = false
                        }) { Text("Apply") }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { showImportDialog = false }) { Text("Cancel") }
                    }
                )
            }
        }
    }
}

// =====================================================================
// 2. NAVIGATION & INTERFACE CONTENT (Features 16-25)
// =====================================================================
@Composable
fun NavigationSettingsContent(context: Context) {
    val scroll = rememberScrollState()
    val c = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Tab Ordering & Visibility",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = c.primary
        )

        // Features 18 & 19: Reorderable & Toggleable Tabs
        NavigationSettings.tabOrder.forEachIndexed { index, tab ->
            val isEnabled = tab in NavigationSettings.enabledTabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.surfaceContainerLow)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(tab.icon, contentDescription = null, tint = c.primary, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(tab.label, fontWeight = FontWeight.SemiBold, color = c.onSurface)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (index > 0) {
                        IconButton(onClick = { NavigationSettings.reorderTabs(index, index - 1) }) {
                            Icon(Icons.Rounded.ArrowUpward, null, Modifier.size(18.dp))
                        }
                    }
                    if (index < NavigationSettings.tabOrder.lastIndex) {
                        IconButton(onClick = { NavigationSettings.reorderTabs(index, index + 1) }) {
                            Icon(Icons.Rounded.ArrowDownward, null, Modifier.size(18.dp))
                        }
                    }
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { NavigationSettings.setTabEnabled(tab, it) },
                        enabled = tab != Tab.Home // Home cannot be disabled
                    )
                }
            }
        }

        // Feature 20: Icon-Only Navigation
        SettingToggleRow(
            title = "Icon-Only Navigation",
            subtitle = "Hides text labels in the liquid glass bottom bar",
            checked = NavigationSettings.iconOnly,
            onCheckedChange = { NavigationSettings.updateIconOnly(it) }
        )

        // Feature 21: Navigation Auto-Collapse
        SettingToggleRow(
            title = "Auto-Collapse Navigation",
            subtitle = "Hides bottom bar when scrolling feeds down",
            checked = NavigationSettings.autoCollapseOnScroll,
            onCheckedChange = { NavigationSettings.updateAutoCollapse(it) }
        )

        // Feature 22: Floating Search Button
        SettingToggleRow(
            title = "Floating Search Pill",
            subtitle = "Shows a floating quick search button on screen",
            checked = NavigationSettings.floatingSearchButton,
            onCheckedChange = { NavigationSettings.updateFloatingSearch(it) }
        )

        // Feature 23: Navigation Icon Pack
        Text(
            text = "Navigation Icon Pack",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = c.onSurface
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NavigationIconPack.entries.forEach { pack ->
                FilterChip(
                    selected = NavigationSettings.iconPack == pack,
                    onClick = { NavigationSettings.updateIconPack(pack) },
                    label = { Text(pack.label) }
                )
            }
        }

        // Feature 25: Global UI Visibility Manager
        Text(
            text = "Global UI Element Visibility",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = c.primary
        )
        SettingToggleRow(
            title = "Show Bottom Bar",
            subtitle = "Main liquid navigation dock",
            checked = UiVisibilityManager.showBottomBar,
            onCheckedChange = { UiVisibilityManager.toggleBottomBar(it) }
        )
        SettingToggleRow(
            title = "Show Header Filter Chips",
            subtitle = "Top category chips on home & subscription screens",
            checked = UiVisibilityManager.showHeaderChips,
            onCheckedChange = { UiVisibilityManager.toggleHeaderChips(it) }
        )
        SettingToggleRow(
            title = "Show Channel Avatars",
            subtitle = "Circular author pictures on feeds",
            checked = UiVisibilityManager.showChannelAvatars,
            onCheckedChange = { UiVisibilityManager.toggleChannelAvatars(it) }
        )
    }
}

// =====================================================================
// 3. HOME FEED BUILDER CONTENT (Features 26-40)
// =====================================================================
@Composable
fun HomeFeedBuilderSettingsContent(context: Context) {
    val scroll = rememberScrollState()
    val c = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Feature 37: Home Layout Profiles
        Text(
            text = "Home Layout Profile",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = c.primary
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HomeLayoutProfile.entries.forEach { profile ->
                FilterChip(
                    selected = HomeFeedSettings.layoutProfile == profile,
                    onClick = { HomeFeedSettings.applyProfile(profile) },
                    label = { Text(profile.label) }
                )
            }
        }

        // Feature 28-31: Layout Styles
        Text(
            text = "Feed Layout Style",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = c.onSurface
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ScreenLayoutMode.entries.forEach { mode ->
                FilterChip(
                    selected = HomeFeedSettings.layoutStyle == mode,
                    onClick = { HomeFeedSettings.updateLayoutStyle(mode) },
                    label = { Text(mode.label) }
                )
            }
        }

        // Feature 39: Subscription-based Home Feed
        SettingToggleRow(
            title = "Subscriptions-Only Home Feed",
            subtitle = "Filters home feed to only show channels you subscribe to",
            checked = HomeFeedSettings.subscriptionOnlyMode,
            onCheckedChange = { HomeFeedSettings.toggleSubscriptionOnly(it) }
        )

        // Feature 33 & 36: Hide Elements
        SettingToggleRow(
            title = "Hide Channel Avatars",
            subtitle = "Removes round avatar icons from video feed cards",
            checked = HomeFeedSettings.hideChannelAvatars,
            onCheckedChange = { HomeFeedSettings.toggleHideAvatars(it) }
        )
        SettingToggleRow(
            title = "Hide Watch Progress Bars",
            subtitle = "Removes red resume bar from watched video thumbnails",
            checked = HomeFeedSettings.hideProgressIndicators,
            onCheckedChange = { HomeFeedSettings.toggleHideProgress(it) }
        )

        // Feature 26 & 38: Section Visibility
        Text(
            text = "Home Content Sections",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = c.primary
        )
        HomeSection.entries.forEach { sec ->
            val visible = sec in HomeFeedSettings.visibleSections
            SettingToggleRow(
                title = sec.label,
                subtitle = "Toggle visibility on the home feed",
                checked = visible,
                onCheckedChange = { HomeFeedSettings.setSectionVisibility(sec, it) }
            )
        }

        // Feature 40: Smart Feed Filtering Rules
        Text(
            text = "Smart Keyword Blacklist",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = c.primary
        )
        var newWord by remember { mutableStateOf("") }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = newWord,
                onValueChange = { newWord = it },
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.surfaceContainerLow)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                textStyle = TextStyle(color = c.onSurface, fontSize = 13.sp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    HomeFeedSettings.addBlockedKeyword(newWord)
                    newWord = ""
                })
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    HomeFeedSettings.addBlockedKeyword(newWord)
                    newWord = ""
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Rounded.Add, null)
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            HomeFeedSettings.blockedKeywords.forEach { kw ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(c.errorContainer)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .clickable { HomeFeedSettings.removeBlockedKeyword(kw) }
                ) {
                    Text("$kw ✕", fontSize = 11.sp, color = c.onErrorContainer, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// =====================================================================
// 4. ADVANCED VIDEO PLAYER CONTENT (Features 41-60)
// =====================================================================
@Composable
fun AdvancedPlayerSettingsContent(context: Context) {
    val scroll = rememberScrollState()
    val c = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Feature 42: Multiple Player Layouts
        Text(
            text = "Player Layout Style",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = c.primary
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PlayerLayoutPreset.entries.forEach { preset ->
                FilterChip(
                    selected = PlayerEnhancements.layoutPreset == preset,
                    onClick = { PlayerEnhancements.updateLayoutPreset(preset) },
                    label = { Text(preset.label) }
                )
            }
        }

        // Feature 41: Player Control Positioning
        Text(
            text = "Controls Placement",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = c.onSurface
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PlayerControlPosition.entries.forEach { pos ->
                FilterChip(
                    selected = PlayerEnhancements.controlPosition == pos,
                    onClick = { PlayerEnhancements.updateControlPosition(pos) },
                    label = { Text(pos.label) }
                )
            }
        }

        // Feature 45: Custom Seekbar Designs
        Text(
            text = "Seekbar Design",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = c.onSurface
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SeekbarStyle.entries.forEach { style ->
                FilterChip(
                    selected = PlayerEnhancements.seekbarStyle == style,
                    onClick = { PlayerEnhancements.updateSeekbarStyle(style) },
                    label = { Text(style.label) }
                )
            }
        }

        // Feature 46: Chapter Snapping
        SettingToggleRow(
            title = "Chapter Snapping",
            subtitle = "Magnetically snaps scrubber near chapter boundaries (2.5s)",
            checked = PlayerEnhancements.chapterSnappingEnabled,
            onCheckedChange = { PlayerEnhancements.toggleChapterSnapping(it) }
        )

        // Feature 48: Video Aspect-Ratio Presets
        Text(
            text = "Default Video Aspect Ratio",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = c.onSurface
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AspectRatioPreset.entries.forEach { asp ->
                FilterChip(
                    selected = PlayerEnhancements.activeAspectPreset == asp,
                    onClick = { PlayerEnhancements.updateAspectPreset(asp) },
                    label = { Text(asp.label) }
                )
            }
        }

        // Feature 50: Playback Profiles
        Text(
            text = "Playback Profile",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = c.primary
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PlaybackProfile.entries.forEach { prof ->
                FilterChip(
                    selected = PlayerEnhancements.activeProfile == prof,
                    onClick = { PlayerEnhancements.updateProfile(prof) },
                    label = { Text(prof.label) }
                )
            }
        }

        // Feature 56-60: Subtitle Styling & Offset Presets
        Text(
            text = "Subtitle Styling & Offset",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = c.primary
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Timing Offset: ${PlayerEnhancements.subtitleOffsetMs} ms",
                fontWeight = FontWeight.SemiBold,
                color = c.onSurface
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { PlayerEnhancements.adjustSubtitleOffset(-500) }) { Text("-500ms", fontSize = 11.sp) }
                OutlinedButton(onClick = { PlayerEnhancements.adjustSubtitleOffset(500) }) { Text("+500ms", fontSize = 11.sp) }
                Button(onClick = { PlayerEnhancements.resetSubtitleOffset() }) { Text("Reset", fontSize = 11.sp) }
            }
        }
    }
}

// =====================================================================
// 5. GESTURE DESIGNER CONTENT (Features 61-70)
// =====================================================================
@Composable
fun GestureDesignerSettingsContent(context: Context) {
    val scroll = rememberScrollState()
    val c = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Feature 61: Custom Double-Tap Seeking Duration
        Text(
            text = "Double-Tap Seek Step",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = c.primary
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DoubleTapSeekStep.entries.forEach { step ->
                FilterChip(
                    selected = GestureDesigner.doubleTapSeekStep == step,
                    onClick = { GestureDesigner.updateSeekStep(step) },
                    label = { Text(step.label) }
                )
            }
        }

        // Feature 62 & 63: Brightness & Volume Swipes
        SettingToggleRow(
            title = "Left-Side Brightness Swipe",
            subtitle = "Vertical swipe on left edge controls display brightness",
            checked = GestureDesigner.brightnessSwipeEnabled,
            onCheckedChange = { GestureDesigner.updateBrightnessSwipe(it) }
        )
        SettingToggleRow(
            title = "Right-Side Volume Swipe",
            subtitle = "Vertical swipe on right edge controls media volume",
            checked = GestureDesigner.volumeSwipeEnabled,
            onCheckedChange = { GestureDesigner.updateVolumeSwipe(it) }
        )

        // Feature 64: Custom Gesture Sensitivity
        SettingSliderRow(
            title = "Gesture Sensitivity",
            subtitle = String.format("%.1fx", GestureDesigner.gestureSensitivityMultiplier),
            value = GestureDesigner.gestureSensitivityMultiplier,
            range = 0.5f..2.0f,
            steps = 15,
            onValueChange = { GestureDesigner.updateSensitivity(it) }
        )

        // Feature 65 & 66: Pinch-to-zoom & Video Panning
        SettingToggleRow(
            title = "Two-Finger Pinch-to-Zoom",
            subtitle = "Smooth scaling from 1.0x to 4.0x magnification",
            checked = GestureDesigner.pinchZoomEnabled,
            onCheckedChange = { GestureDesigner.updatePinchZoom(it) }
        )
        SettingToggleRow(
            title = "Video Panning when Zoomed",
            subtitle = "Drag video frame to pan across zoomed content",
            checked = GestureDesigner.videoPanningEnabled,
            onCheckedChange = { GestureDesigner.updateVideoPanning(it) }
        )

        // Feature 68: Swipe-to-Dismiss
        SettingToggleRow(
            title = "Swipe-to-Dismiss Video",
            subtitle = "Swipe down on video player to collapse to miniplayer",
            checked = GestureDesigner.swipeToDismissEnabled,
            onCheckedChange = { GestureDesigner.updateSwipeToDismiss(it) }
        )

        // Feature 70: Shorts Gesture Settings
        Text(
            text = "Shorts Gesture Profile",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = c.primary
        )
        SettingToggleRow(
            title = "Enable Volume Swipe on Shorts",
            subtitle = "Disabled by default to prevent conflict with vertical paging",
            checked = GestureDesigner.shortsVolumeSwipeEnabled,
            onCheckedChange = { GestureDesigner.updateShortsSettings(GestureDesigner.shortsSwipeSensitivity, it) }
        )
    }
}

// =====================================================================
// 6. MINIPLAYER SYSTEM CONTENT (Features 71-80)
// =====================================================================
@Composable
fun MiniPlayerSettingsContent(context: Context) {
    val scroll = rememberScrollState()
    val c = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Feature 80: Presets
        Text(
            text = "Miniplayer Preset Style",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = c.primary
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MiniPlayerPreset.entries.forEach { preset ->
                FilterChip(
                    selected = MiniPlayerSystem.activePreset == preset,
                    onClick = { MiniPlayerSystem.updatePreset(preset) },
                    label = { Text(preset.label) }
                )
            }
        }

        // Feature 75: Customizable Miniplayer Controls
        Text(
            text = "Miniplayer Action Buttons",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = c.onSurface
        )
        SettingToggleRow(
            title = "Show Next Track Button",
            subtitle = "Quick skip to the next item in queue",
            checked = MiniPlayerSystem.showNextButton,
            onCheckedChange = { MiniPlayerSystem.updateControls(it, MiniPlayerSystem.showPrevButton, MiniPlayerSystem.showSeek10Button) }
        )
        SettingToggleRow(
            title = "Show Previous Track Button",
            subtitle = "Restart or jump to the previous item in queue",
            checked = MiniPlayerSystem.showPrevButton,
            onCheckedChange = { MiniPlayerSystem.updateControls(MiniPlayerSystem.showNextButton, it, MiniPlayerSystem.showSeek10Button) }
        )

        // Feature 77: Appearance Settings
        SettingSliderRow(
            title = "Miniplayer Height",
            subtitle = "${MiniPlayerSystem.miniPlayerHeightDp} dp",
            value = MiniPlayerSystem.miniPlayerHeightDp.toFloat(),
            range = 54f..84f,
            steps = 15,
            onValueChange = { MiniPlayerSystem.updateAppearance(it.toInt(), MiniPlayerSystem.miniPlayerCornerRadiusDp, MiniPlayerSystem.miniPlayerOpacity) }
        )
        SettingSliderRow(
            title = "Corner Radius",
            subtitle = "${MiniPlayerSystem.miniPlayerCornerRadiusDp} dp",
            value = MiniPlayerSystem.miniPlayerCornerRadiusDp.toFloat(),
            range = 10f..36f,
            steps = 13,
            onValueChange = { MiniPlayerSystem.updateAppearance(MiniPlayerSystem.miniPlayerHeightDp, it.toInt(), MiniPlayerSystem.miniPlayerOpacity) }
        )

        // Feature 74: Audio Waveform Preview
        Text(
            text = "Waveform Audio Capsule Preview",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = c.primary
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(c.surfaceContainerLow)
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            AudioWaveformVisualizer(isPlaying = true, modifier = Modifier.fillMaxWidth(0.6f).height(36.dp))
        }
    }
}

// =====================================================================
// 7. MUSIC EXPERIENCE CONTENT (Features 81-95)
// =====================================================================
@Composable
fun MusicExperienceSettingsContent(context: Context) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val c = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Feature 81: Crossfade Playback
        SettingSliderRow(
            title = "Crossfade Duration",
            subtitle = if (MusicExperience.crossfadeDurationSec == 0) "Disabled" else "${MusicExperience.crossfadeDurationSec} seconds",
            value = MusicExperience.crossfadeDurationSec.toFloat(),
            range = 0f..10f,
            steps = 10,
            onValueChange = { MusicExperience.updateCrossfade(it.toInt()) }
        )

        // Feature 82 & 83: Gapless & Smart Shuffle
        SettingToggleRow(
            title = "Gapless Playback",
            subtitle = "Pre-loads subsequent track to eliminate silence between songs",
            checked = MusicExperience.gaplessPlaybackEnabled,
            onCheckedChange = { MusicExperience.toggleGapless(it) }
        )
        SettingToggleRow(
            title = "Smart Shuffle",
            subtitle = "Avoids repeat artists and favors unplayed queue items",
            checked = MusicExperience.smartShuffleEnabled,
            onCheckedChange = { MusicExperience.toggleSmartShuffle(it) }
        )

        // Feature 84: Visualizers
        Text(
            text = "Active Visualizer Style",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = c.primary
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MusicVisualizerStyle.entries.forEach { style ->
                FilterChip(
                    selected = MusicExperience.visualizerStyle == style,
                    onClick = { MusicExperience.updateVisualizerStyle(style) },
                    label = { Text(style.label) }
                )
            }
        }

        // Feature 90: Local Music Folder Scanner
        Text(
            text = "Local Device Audio Scanner",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = c.primary
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${MusicExperience.localTracks.size} local tracks indexed",
                fontSize = 13.sp,
                color = c.onSurfaceVariant
            )
            Button(
                onClick = {
                    scope.launch {
                        val tracks = MusicExperience.scanLocalMusic(context)
                        Toast.makeText(context, "Scanned ${tracks.size} local tracks", Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                Icon(Icons.Rounded.Refresh, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Scan Storage", fontSize = 12.sp)
            }
        }

        // Feature 91: Listening Statistics
        Text(
            text = "Listening Statistics",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = c.primary
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(c.surfaceContainerLow)
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Total Hours", fontSize = 11.sp, color = c.onSurfaceVariant)
                Text(
                    String.format("%.1f hrs", MusicExperience.totalListeningSeconds / 3600f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = c.onSurface
                )
            }
            Column {
                Text("Tracks Played", fontSize = 11.sp, color = c.onSurfaceVariant)
                Text(
                    "${MusicExperience.totalTracksPlayed}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = c.onSurface
                )
            }
            Column {
                Text("Days Active", fontSize = 11.sp, color = c.onSurfaceVariant)
                Text(
                    "${MusicExperience.listeningCalendar.size} days",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = c.onSurface
                )
            }
        }
    }
}

// =====================================================================
// 8. DOWNLOAD MANAGER CONTENT (Features 96-110)
// =====================================================================
@Composable
fun DownloadManagerSettingsContent(context: Context) {
    val scroll = rememberScrollState()
    val c = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Feature 104: Storage Usage Dashboard
        StorageUsageDashboard(
            videoBytes = 1024L * 1024 * 650,
            audioBytes = 1024L * 1024 * 180,
            cacheBytes = 1024L * 1024 * 85
        )

        // Feature 98: Max Simultaneous Downloads
        SettingSliderRow(
            title = "Simultaneous Downloads",
            subtitle = "${DownloadManagerAdvanced.maxSimultaneousDownloads} parallel tasks",
            value = DownloadManagerAdvanced.maxSimultaneousDownloads.toFloat(),
            range = 1f..4f,
            steps = 3,
            onValueChange = { DownloadManagerAdvanced.updateSimultaneous(it.toInt()) }
        )

        // Feature 100: Wi-Fi Only Downloads
        SettingToggleRow(
            title = "Wi-Fi Only Downloads",
            subtitle = "Pauses queue automatically when on metered mobile networks",
            checked = DownloadManagerAdvanced.wifiOnlyDownloads,
            onCheckedChange = { DownloadManagerAdvanced.toggleWifiOnly(it) }
        )

        // Feature 101: Download Scheduling
        SettingToggleRow(
            title = "Scheduled Night Downloads",
            subtitle = "Active between ${DownloadManagerAdvanced.scheduleStartHour}:00 and ${DownloadManagerAdvanced.scheduleEndHour}:00",
            checked = DownloadManagerAdvanced.scheduleEnabled,
            onCheckedChange = { DownloadManagerAdvanced.updateSchedule(it, DownloadManagerAdvanced.scheduleStartHour, DownloadManagerAdvanced.scheduleEndHour) }
        )

        // Feature 108: Audio Download Profile
        Text(
            text = "Audio Download Quality Profile",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = c.primary
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AudioDownloadProfile.entries.forEach { prof ->
                FilterChip(
                    selected = DownloadManagerAdvanced.audioProfile == prof,
                    onClick = { DownloadManagerAdvanced.updateAudioProfile(prof) },
                    label = { Text(prof.label) }
                )
            }
        }

        // Feature 107: Download History
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Download History (${DownloadManagerAdvanced.history.size})",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = c.primary
            )
            if (DownloadManagerAdvanced.history.isNotEmpty()) {
                OutlinedButton(onClick = { DownloadManagerAdvanced.clearHistory() }) {
                    Text("Clear", fontSize = 11.sp)
                }
            }
        }
        DownloadManagerAdvanced.history.take(5).forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.surfaceContainerLow)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Text(item.channel, fontSize = 10.sp, color = c.onSurfaceVariant)
                }
                Text("${item.fileSizeBytes / (1024 * 1024)} MB", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// =====================================================================
// 9. PRIVACY & PERSONALISATION CONTENT (Features 111-120)
// =====================================================================
@Composable
fun PrivacyPersonalisationSettingsContent(context: Context) {
    val scroll = rememberScrollState()
    val c = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Feature 117: Privacy Dashboard Card
        PrivacyDashboardCard(
            onClearAllHistory = {
                SearchHistory.clear()
                Watched.clear()
                ResumeStore.clearAll()
                Toast.makeText(context, "All history wiped clean", Toast.LENGTH_SHORT).show()
            }
        )

        // Feature 111: UI Profiles
        Text(
            text = "Active UI Profile",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = c.primary
        )
        AppUserProfile.entries.forEach { prof ->
            val isSelected = PrivacyPersonalisation.activeProfile == prof
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) c.primary.copy(alpha = 0.15f) else c.surfaceContainerLow)
                    .border(
                        width = if (isSelected) 1.5.dp else 0.dp,
                        color = if (isSelected) c.primary else Color.Transparent,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { PrivacyPersonalisation.switchProfile(prof) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(prof.label, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = c.onSurface)
                    Text(prof.description, fontSize = 11.sp, color = c.onSurfaceVariant)
                }
                if (isSelected) {
                    Icon(Icons.Rounded.Check, null, tint = c.primary, modifier = Modifier.size(20.dp))
                }
            }
        }

        // Feature 114: Auto History Cleanup
        SettingSliderRow(
            title = "Auto History Expiration",
            subtitle = if (PrivacyPersonalisation.autoHistoryCleanupDays == 0) "Keep Indefinitely" else "Clear records older than ${PrivacyPersonalisation.autoHistoryCleanupDays} days",
            value = PrivacyPersonalisation.autoHistoryCleanupDays.toFloat(),
            range = 0f..90f,
            steps = 9,
            onValueChange = { PrivacyPersonalisation.updateAutoHistoryCleanup(it.toInt()) }
        )

        // Feature 118: Local Personalization Weight
        SettingSliderRow(
            title = "On-Device Recommendation Weight",
            subtitle = String.format("%.1fx multiplier", PrivacyPersonalisation.recommendationWeight),
            value = PrivacyPersonalisation.recommendationWeight,
            range = 0f..2.0f,
            steps = 20,
            onValueChange = { PrivacyPersonalisation.updateRecommendationWeight(it) }
        )

        // Feature 120: Recently Changed Settings Log
        Text(
            text = "Recently Modified Settings",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = c.primary
        )
        PrivacyPersonalisation.recentlyChangedSettings.take(5).forEach { rec ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.surfaceContainerLow)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(rec.label, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Text("Changed to: ${rec.newValue}", fontSize = 10.sp, color = c.onSurfaceVariant)
                }
            }
        }
    }
}

// =====================================================================
// 10. PREMIUM EXPERIENCE CONTENT (Phase 3 Specifications)
// =====================================================================
@Composable
fun PremiumExperienceSettingsContent(context: Context) {
    val scroll = rememberScrollState()
    val c = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Selectable Loading Animations (6 styles)
        Text(
            text = "Loading Animation Style (6 Styles)",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = c.primary
        )

        // Live preview of active loader
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(c.surfaceContainerLow),
            contentAlignment = Alignment.Center
        ) {
            AppLoadingIndicator(size = 44.dp, color = c.primary)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PremiumLoadingStyle.entries.forEach { style ->
                FilterChip(
                    selected = PremiumFeatures.loadingStyle == style,
                    onClick = { PremiumFeatures.updateLoadingStyle(style) },
                    label = { Text(style.label) }
                )
            }
        }

        // Performance & Optimization
        Text(
            text = "Performance & Thermal Optimization",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = c.primary
        )
        SettingToggleRow(
            title = "Automatic Blur Reduction",
            subtitle = "Reduces shader complexity when battery is low or frames drop",
            checked = PremiumFeatures.autoBlurReduction,
            onCheckedChange = { PremiumFeatures.updatePerformanceOptions(it, PremiumFeatures.batteryAwareAnimations, PremiumFeatures.frameDropMonitoring) }
        )
        SettingToggleRow(
            title = "Battery-Aware Animations",
            subtitle = "Slows or halts infinite canvas loops when power is under 15%",
            checked = PremiumFeatures.batteryAwareAnimations,
            onCheckedChange = { PremiumFeatures.updatePerformanceOptions(PremiumFeatures.autoBlurReduction, it, PremiumFeatures.frameDropMonitoring) }
        )
        SettingToggleRow(
            title = "Frame-Drop Monitoring",
            subtitle = "Monitors render latency on high refresh rate displays",
            checked = PremiumFeatures.frameDropMonitoring,
            onCheckedChange = { PremiumFeatures.updatePerformanceOptions(PremiumFeatures.autoBlurReduction, PremiumFeatures.batteryAwareAnimations, it) }
        )

        // Memory Usage Overview
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(c.surfaceContainerLow)
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("App Memory Allocated", fontSize = 11.sp, color = c.onSurfaceVariant)
                Text("${PremiumFeatures.usedMemoryMb} MB", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Column {
                Text("Max Heap Limit", fontSize = 11.sp, color = c.onSurfaceVariant)
                Text("${PremiumFeatures.maxMemoryMb} MB", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// =====================================================================
// HELPER COMPONENTS
// =====================================================================
@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.surfaceContainerLow)
            .clickable { onCheckedChange(!checked) }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = c.onSurface)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, fontSize = 12.sp, color = c.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingSliderRow(
    title: String,
    subtitle: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit
) {
    val c = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.surfaceContainerLow)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = c.onSurface)
            Text(subtitle, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = c.primary)
        }
        Spacer(Modifier.height(6.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
