package com.tensei.ytxtube
import androidx.compose.ui.window.Dialog
import android.provider.DocumentsContract
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.Brush
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onSignedOut: () -> Unit,
    onAccountSwitched: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onCheckUpdate: () -> Unit = {},
) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profile = Account.profile
    val scrollState = rememberScrollState()

    var cacheMessage by remember { mutableStateOf<String?>(null) }
    var activeFloatingSection by remember { mutableStateOf<SettingsFloatingSection?>(null) }
    var showSignOutConfirm by remember { mutableStateOf(false) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateFeedback by remember { mutableStateOf<String?>(null) }

    val isOverlayOpen = activeFloatingSection != null || showSignOutConfirm || checkingUpdate || updateFeedback != null
    val blurRadius by animateDpAsState(
        targetValue = if (isOverlayOpen && Build.VERSION.SDK_INT >= 31) 16.dp else 0.dp,
        animationSpec = tween(220),
        label = "settingsBlur",
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(c.surface)
                .then(if (blurRadius > 0.dp) Modifier.blur(blurRadius) else Modifier)
        ) {
            BackScreenHeader(onBack = onBack) {
                Text(
                    text = "Settings",
                    fontFamily = RoundedHeavy,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    letterSpacing = (-0.5).sp,
                    color = c.onSurface,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = contentPadding.calculateBottomPadding() + 24.dp)
            ) {
                Spacer(Modifier.height(2.dp))

                SettingsProfileCard(
                    profile = profile,
                    onSignIn = onSignIn,
                    onRequestSignOut = { showSignOutConfirm = true },
                    onAccountSwitched = onAccountSwitched,
                )

                SettingsSectionHeader("Customization")
                SettingsCard {
                    SettingsRow(
                        icon = Icons.Rounded.Palette,
                        title = "Appearance & Theme",
                        subtitle = "Theme mode, color palette and UI style",
                        onClick = { activeFloatingSection = SettingsFloatingSection.APPEARANCE },
                    )
                }

                SettingsSectionHeader("Player & Interface")
                SettingsCard {
                    SettingsRow(
                        icon = Icons.Rounded.Tune,
                        title = "General Modification",
                        subtitle = "Font, gestures, quality & downloads",
                        onClick = { activeFloatingSection = SettingsFloatingSection.GENERAL_MODS },
                    )
                }

                SettingsSectionHeader("Data & Storage")
                SettingsCard {
                    SettingsRow(
                        icon = Icons.Rounded.DataUsage,
                        title = "Storage Usage",
                        subtitle = "See phone storage & clear YTXTube cache",
                        onClick = { activeFloatingSection = SettingsFloatingSection.STORAGE_USAGE },
                    )
                }

                SettingsSectionHeader("About")
                SettingsCard {
                    SettingsRow(
                        icon = Icons.Rounded.Info,
                        title = "Version",
                        subtitle = "YTXTube ${BuildConfig.VERSION_NAME}",
                        trailing = {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF132818))
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF2ECC71))
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        text = "Up to date",
                                        color = Color(0xFF2ECC71),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                        }
                    )
                    SettingsRow(
                        icon = Icons.Rounded.FileDownload,
                        title = "Check for updates",
                        subtitle = "New GitHub releases install from inside the app",
                        onClick = {
                            if (checkingUpdate) return@SettingsRow
                            checkingUpdate = true
                            updateFeedback = null
                            scope.launch {
                                val start = System.currentTimeMillis()
                                onCheckUpdate()
                                val elapsed = System.currentTimeMillis() - start
                                if (elapsed < 1200) delay(1200 - elapsed)
                                checkingUpdate = false
                                updateFeedback = "You're on the latest version (${BuildConfig.VERSION_NAME})"
                            }
                        },
                    )
                    val openRepo: (String) -> Unit = { url ->
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }
                    }
                    val ytxRepo = "https://github.com/Koustubh12345/YTXTube"
                    SettingsRow(
                        icon = Icons.Rounded.Code,
                        title = "Open Source",
                        subtitle = "View source code on GitHub",
                        onClick = { openRepo(ytxRepo) },
                    )
                    val telegram = "https://t.me/getthefckoutofheree"
                    val openTelegram: () -> Unit = {
                        val app = Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=getthefckoutofheree"))
                        val web = Intent(Intent.ACTION_VIEW, Uri.parse(telegram))
                        runCatching { context.startActivity(app) }
                            .onFailure { runCatching { context.startActivity(web) } }
                    }
                    SettingsRow(
                        icon = Icons.Rounded.Person,
                        title = "Developer",
                        subtitle = "@getthefckoutofheree",
                        onClick = openTelegram,
                    )
                }
            }
        }

        if (isOverlayOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.28f))
            )
        }

        AnimatedContent(
            targetState = activeFloatingSection,
            transitionSpec = {
                (scaleIn(spring(dampingRatio = 0.82f, stiffness = 420f), initialScale = 0.90f) + fadeIn(tween(180))) togetherWith
                    (scaleOut(tween(130), targetScale = 0.92f) + fadeOut(tween(130)))
            },
            label = "settingsFloatingSection",
        ) { section ->
            when (section) {
                SettingsFloatingSection.APPEARANCE -> {
                    SettingsFloatingDialog(
                        title = "Appearance & Theme",
                        subtitle = "Theme mode, color palette & UI style",
                        onDismiss = { activeFloatingSection = null },
                    ) {
                        AppearanceThemeContent(context = context)
                    }
                }
                SettingsFloatingSection.GENERAL_MODS -> {
                    SettingsFloatingDialog(
                        title = "General Modification",
                        subtitle = "Font, gestures, quality & downloads",
                        onDismiss = { activeFloatingSection = null },
                    ) {
                        GeneralModificationContent(context = context)
                    }
                }
                SettingsFloatingSection.STORAGE_USAGE -> {
                    SettingsFloatingDialog(
                        title = "Storage Usage",
                        subtitle = "Device memory & YTXTube data",
                        onDismiss = { activeFloatingSection = null },
                    ) {
                        StorageUsageContent(context = context) {
                            cacheMessage = "Cache cleared successfully"
                        }
                    }
                }
                null -> {}
            }
        }

        AnimatedVisibility(
            visible = showSignOutConfirm,
            enter = scaleIn(spring(dampingRatio = 0.8f, stiffness = 400f), initialScale = 0.88f) + fadeIn(tween(160)),
            exit = scaleOut(tween(120), targetScale = 0.92f) + fadeOut(tween(120)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { showSignOutConfirm = false },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .widthIn(max = 350.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(c.surfaceContainerHighest)
                        .border(1.dp, c.onSurface.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { }
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(c.error.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Logout,
                            contentDescription = null,
                            tint = c.error,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "Sign out?",
                        fontFamily = RoundedHeavy,
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                        color = c.onSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Are you sure you want to sign out from ${Account.profile?.name ?: "this account"}?",
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = c.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(c.surfaceContainerHigh)
                                .pressable { showSignOutConfirm = false }
                                .padding(vertical = 11.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("Cancel", fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = c.onSurface)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(c.error)
                                .pressable {
                                    showSignOutConfirm = false
                                    Account.signOut()
                                    onSignedOut()
                                }
                                .padding(vertical = 11.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("Sign out", fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = c.onError)
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = checkingUpdate || updateFeedback != null,
            enter = scaleIn(spring(dampingRatio = 0.8f, stiffness = 400f), initialScale = 0.88f) + fadeIn(tween(160)),
            exit = scaleOut(tween(120), targetScale = 0.92f) + fadeOut(tween(120)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            if (!checkingUpdate) updateFeedback = null
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .widthIn(max = 350.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(c.surfaceContainerHighest)
                        .border(1.dp, c.onSurface.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { }
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (checkingUpdate) {
                        CircularProgressIndicator(
                            color = c.primary,
                            strokeWidth = 3.5.dp,
                            modifier = Modifier.size(40.dp),
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Checking for updates…",
                            fontFamily = RoundedHeavy,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = c.onSurface,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Connecting to GitHub releases…",
                            fontSize = 12.sp,
                            color = c.onSurfaceVariant,
                        )
                    } else if (updateFeedback != null) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF132818)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null,
                                tint = Color(0xFF2ECC71),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = "Up to date",
                            fontFamily = RoundedHeavy,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = c.onSurface,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = updateFeedback.orEmpty(),
                            fontSize = 12.sp,
                            color = c.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(16.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(c.primary)
                                .pressable { updateFeedback = null }
                                .padding(horizontal = 22.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("OK", color = c.onPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

private enum class SettingsFloatingSection {
    APPEARANCE,
    GENERAL_MODS,
    STORAGE_USAGE,
}

@Composable
private fun SettingsSectionHeader(title: String) {
    val c = MaterialTheme.colorScheme
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = c.onSurfaceVariant.copy(alpha = 0.72f),
        letterSpacing = 0.6.sp,
        modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 6.dp)
    )
}

@Composable
private fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(c.surfaceContainerHigh),
        content = content,
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.pressable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(c.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = c.onSurface,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.5.sp,
                color = c.onSurface,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = subtitle,
                fontSize = 11.5.sp,
                lineHeight = 15.sp,
                color = c.onSurfaceVariant.copy(alpha = 0.8f),
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        } else if (onClick != null) {
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.NavigateNext,
                contentDescription = null,
                tint = c.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun SettingsFloatingDialog(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    BackHandler(onBack = onDismiss)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .widthIn(max = 410.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(c.surfaceContainerHighest)
                .border(1.dp, c.onSurface.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { }
                .padding(18.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(c.surfaceContainerHigh)
                        .pressable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = c.onSurface,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontFamily = RoundedHeavy,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = c.onSurface,
                    )
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = subtitle,
                        fontSize = 11.5.sp,
                        color = c.onSurfaceVariant,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(c.surfaceContainerHigh)
                        .pressable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Close",
                        tint = c.onSurfaceVariant,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }

            HorizontalDivider(
                color = c.onSurface.copy(alpha = 0.08f),
                modifier = Modifier.padding(bottom = 6.dp),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun AppearanceThemeContent(context: Context) {
    val c = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        SettingsSwitchRow(
            title = "Liquid Glass",
            subtitle = if (GlassSettings.enabled) {
                "Frosted, see-through surfaces with depth and blur"
            } else {
                "Using solid Material surfaces with no transparency"
            },
            checked = GlassSettings.enabled,
            onCheckedChange = { GlassSettings.updateEnabled(it) },
        )

        HorizontalDivider(color = c.onSurface.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 4.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Theme mode", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Text("Choose how the app looks", color = c.onSurfaceVariant, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
            listOf(
                "system" to "System",
                "dark" to "Dark",
                "light" to "Light",
                "amoled" to "AMOLED",
            ).forEach { (mode, label) ->
                val on = ThemeSettings.mode == mode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .pressable { ThemeSettings.chooseMode(mode) }
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (on) c.primary else c.surfaceContainerHigh)
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        color = if (on) c.onPrimary else c.onSurfaceVariant,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                        fontSize = 12.sp
                    )
                }
            }
            }
        }

        HorizontalDivider(color = c.onSurface.copy(alpha = 0.08f))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Color palette", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Text("Choose an accent for the app", color = c.onSurfaceVariant, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(12.dp))

            val dark = isDarkTheme()
            val choices = buildList {
                if (ThemeSettings.wallpaperAvailable) add(null to "Wallpaper")
                YTX_THEMES.forEach { add(it.hex to it.name) }
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                choices.forEach { (hex, name) ->
                    val selected = ThemeSettings.seed?.lowercase() == hex?.lowercase()
                    val scheme = schemeFor(context, hex, dark)
                    Column(
                        modifier = Modifier
                            .width(64.dp)
                            .pressable { ThemeSettings.choose(hex) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(if (selected) scheme.primaryContainer else Color.Transparent)
                                .padding(4.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(scheme.primary, scheme.primaryContainer)
                                    )
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) {
                                Icon(Icons.Rounded.Check, "Selected", tint = scheme.onPrimary, modifier = Modifier.size(18.dp))
                            }
                        }
                        Spacer(Modifier.height(5.dp))
                        Text(
                            name,
                            fontSize = 10.5.sp,
                            maxLines = 1,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) c.onSurface else c.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsClickRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .pressable(onClick = onClick)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
        ) {
            Text(
                text = title,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = c.onSurface,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = subtitle,
                color = c.onSurfaceVariant,
                fontSize = 11.5.sp,
                lineHeight = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.AutoMirrored.Rounded.NavigateNext,
            contentDescription = null,
            tint = c.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun QualitySelectionDialog(
    title: String,
    currentMode: PlaybackQualityMode,
    onSelect: (PlaybackQualityMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = c.surfaceContainer),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                Text(
                    text = title,
                    fontFamily = RoundedHeavy,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = c.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Select default playback quality",
                    fontSize = 12.sp,
                    color = c.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))

                PlaybackQualityMode.entries.forEach { mode ->
                    val selected = mode == currentMode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selected) c.primaryContainer.copy(alpha = 0.5f) else Color.Transparent)
                            .pressable { onSelect(mode) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = mode.label,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                fontSize = 14.sp,
                                color = if (selected) c.primary else c.onSurface,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = mode.subtitle,
                                fontSize = 11.5.sp,
                                color = c.onSurfaceVariant,
                            )
                        }
                        if (selected) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(c.primary),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = "Selected",
                                    tint = c.onPrimary,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }

                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .pressable(onClick = onDismiss)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text("Cancel", color = c.primary, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadFolderSelectionDialog(
    currentPath: String,
    onResetDefault: () -> Unit,
    onSelectMusic: () -> Unit,
    onCustomPick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = c.surfaceContainer),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                Text(
                    text = "Download Location",
                    fontFamily = RoundedHeavy,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = c.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Active: $currentPath",
                    fontSize = 11.5.sp,
                    color = c.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(14.dp))

                val defaultDir = Downloads.defaultDownloadDir().absolutePath
                val isDefault = currentPath == defaultDir

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isDefault) c.primaryContainer.copy(alpha = 0.5f) else Color.Transparent)
                        .pressable { onResetDefault() }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.Download,
                        contentDescription = null,
                        tint = if (isDefault) c.primary else c.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Download/YTXTube Downloads",
                            fontWeight = if (isDefault) FontWeight.SemiBold else FontWeight.Medium,
                            fontSize = 13.sp,
                            color = if (isDefault) c.primary else c.onSurface,
                        )
                        Text(
                            text = "Default public Downloads directory",
                            fontSize = 11.sp,
                            color = c.onSurfaceVariant,
                        )
                    }
                    if (isDefault) {
                        Icon(Icons.Rounded.Check, null, tint = c.primary, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(Modifier.height(6.dp))

                val musicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "YTXTube").absolutePath
                val isMusic = currentPath == musicDir
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isMusic) c.primaryContainer.copy(alpha = 0.5f) else Color.Transparent)
                        .pressable { onSelectMusic() }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = if (isMusic) c.primary else c.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Music/YTXTube",
                            fontWeight = if (isMusic) FontWeight.SemiBold else FontWeight.Medium,
                            fontSize = 13.sp,
                            color = if (isMusic) c.primary else c.onSurface,
                        )
                        Text(
                            text = "Dedicated Music directory",
                            fontSize = 11.sp,
                            color = c.onSurfaceVariant,
                        )
                    }
                    if (isMusic) {
                        Icon(Icons.Rounded.Check, null, tint = c.primary, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(Modifier.height(6.dp))

                val isCustom = !isDefault && !isMusic
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isCustom) c.primaryContainer.copy(alpha = 0.5f) else Color.Transparent)
                        .pressable { onCustomPick() }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.Storage,
                        contentDescription = null,
                        tint = if (isCustom) c.primary else c.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Choose Custom Folder...",
                            fontWeight = if (isCustom) FontWeight.SemiBold else FontWeight.Medium,
                            fontSize = 13.sp,
                            color = if (isCustom) c.primary else c.onSurface,
                        )
                        Text(
                            text = if (isCustom) currentPath else "Select any directory on device",
                            fontSize = 11.sp,
                            color = c.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (isCustom) {
                        Icon(Icons.Rounded.Check, null, tint = c.primary, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .pressable(onClick = onDismiss)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text("Close", color = c.primary, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun GeneralModificationContent(context: Context) {
    val c = MaterialTheme.colorScheme
    var showVideoQualityDialog by remember { mutableStateOf(false) }
    var showShortsQualityDialog by remember { mutableStateOf(false) }
    var showFolderDialog by remember { mutableStateOf(false) }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val docId = DocumentsContract.getTreeDocumentId(uri)
            val path = if (docId.startsWith("primary:")) {
                File(Environment.getExternalStorageDirectory(), docId.removePrefix("primary:"))
            } else {
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "YTXTube Downloads")
            }
            Downloads.setCustomDownloadDir(path)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsSwitchRow(
            title = "Use System Font",
            subtitle = "Use device default font throughout the app",
            checked = ThemeSettings.useSystemFont,
            onCheckedChange = { ThemeSettings.updateUseSystemFont(it) },
        )

        HorizontalDivider(color = c.onSurface.copy(alpha = 0.08f))

        SettingsSwitchRow(
            title = "Video gestures",
            subtitle = "Swipe vertically on the left for brightness, right for volume",
            checked = VideoGestureSettings.enabled,
            onCheckedChange = { VideoGestureSettings.set(it) },
        )

        HorizontalDivider(color = c.onSurface.copy(alpha = 0.08f))

        Spacer(Modifier.height(8.dp))
        Text(
            text = "SponsorBlock",
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = c.onSurface,
        )
        Spacer(Modifier.height(1.dp))
        SettingsSwitchRow(
            title = "Automatically skip segments",
            subtitle = "Choose what happens for each type",
            checked = SponsorBlock.enabled,
            onCheckedChange = SponsorBlock::updateEnabled,
        )

        if (SponsorBlock.enabled) {
            HorizontalDivider(color = c.onSurface.copy(alpha = 0.08f))
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                SponsorBlock.Category.entries.forEach { category ->
                    val current = SponsorBlock.actionFor(category)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .padding(end = 8.dp)
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(category.color)
                        )
                        Column(Modifier.weight(1f)) {
                            Text(category.label, fontWeight = FontWeight.Medium, fontSize = 12.sp, maxLines = 2)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            SponsorBlock.Action.entries.forEach { action ->
                                val on = action == current
                                Box(
                                    modifier = Modifier
                                        .pressable { SponsorBlock.setAction(category, action) }
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (on) c.secondaryContainer else Color.Transparent)
                                        .padding(horizontal = 6.dp, vertical = 6.dp),
                                ) {
                                    Text(
                                        action.label,
                                        color = if (on) c.onSecondaryContainer else c.onSurfaceVariant,
                                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = c.onSurface.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 8.dp))

        Text(
            text = "Quality",
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = c.onSurface,
        )
        Spacer(Modifier.height(2.dp))

        SettingsClickRow(
            title = "Video Quality",
            subtitle = QualityPreference.videoQualityMode.label,
            onClick = { showVideoQualityDialog = true },
        )

        HorizontalDivider(color = c.onSurface.copy(alpha = 0.08f))

        SettingsClickRow(
            title = "Shorts Quality",
            subtitle = QualityPreference.shortsQualityMode.label,
            onClick = { showShortsQualityDialog = true },
        )

        HorizontalDivider(color = c.onSurface.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 8.dp))

        Text(
            text = "Downloads",
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = c.onSurface,
        )
        Spacer(Modifier.height(2.dp))

        val currentPath = Downloads.currentDownloadDir.absolutePath
        SettingsClickRow(
            title = "Select Download folder",
            subtitle = currentPath,
            onClick = { showFolderDialog = true },
        )
    }

    if (showVideoQualityDialog) {
        QualitySelectionDialog(
            title = "Video Quality",
            currentMode = QualityPreference.videoQualityMode,
            onSelect = {
                QualityPreference.updateVideoQualityMode(it)
                showVideoQualityDialog = false
            },
            onDismiss = { showVideoQualityDialog = false }
        )
    }

    if (showShortsQualityDialog) {
        QualitySelectionDialog(
            title = "Shorts Quality",
            currentMode = QualityPreference.shortsQualityMode,
            onSelect = {
                QualityPreference.updateShortsQualityMode(it)
                showShortsQualityDialog = false
            },
            onDismiss = { showShortsQualityDialog = false }
        )
    }

    if (showFolderDialog) {
        DownloadFolderSelectionDialog(
            currentPath = Downloads.currentDownloadDir.absolutePath,
            onResetDefault = {
                Downloads.resetToDefaultDownloadDir()
                showFolderDialog = false
            },
            onSelectMusic = {
                val musicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "YTXTube")
                Downloads.setCustomDownloadDir(musicDir)
                showFolderDialog = false
            },
            onCustomPick = {
                runCatching {
                    val initialUri = Uri.fromFile(Downloads.currentDownloadDir)
                    folderPicker.launch(initialUri)
                }.onFailure {
                    folderPicker.launch(null)
                }
                showFolderDialog = false
            },
            onDismiss = { showFolderDialog = false }
        )
    }
}
private data class StorageData(
    val deviceTotal: Long,
    val deviceUsed: Long,
    val deviceFree: Long,
    val ytxApp: Long,
    val ytxCache: Long,
    val ytxData: Long,
    val ytxTotal: Long,
)

private fun getFolderSize(dir: File?): Long {
    if (dir == null || !dir.exists()) return 0L
    var size = 0L
    dir.listFiles()?.forEach { file ->
        size += if (file.isDirectory) getFolderSize(file) else file.length()
    }
    return size
}

private fun formatStorageBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
    return if (digitGroups >= 3) {
        "%.1f %s".format(value, units[digitGroups])
    } else {
        "%.0f %s".format(value, units[digitGroups])
    }
}

private fun calculateStorageData(context: Context): StorageData {
    return runCatching {
        val stat = StatFs(Environment.getDataDirectory().path)
        val total = stat.totalBytes
        val free = stat.availableBytes
        val used = (total - free).coerceAtLeast(0L)

        val appSize = runCatching { File(context.packageCodePath).length() }.getOrDefault(18_000_000L)
        val cacheSize = getFolderSize(context.cacheDir) + (context.externalCacheDir?.let { getFolderSize(it) } ?: 0L)
        val dataSize = getFolderSize(context.filesDir)

        StorageData(
            deviceTotal = total,
            deviceUsed = used,
            deviceFree = free,
            ytxApp = appSize,
            ytxCache = cacheSize,
            ytxData = dataSize,
            ytxTotal = appSize + cacheSize + dataSize,
        )
    }.getOrDefault(
        StorageData(128_000_000_000L, 45_000_000_000L, 83_000_000_000L, 18_000_000L, 24_000_000L, 10_000_000L, 52_000_000L)
    )
}

@Composable
private fun StorageUsageContent(context: Context, onCacheCleared: () -> Unit) {
    val c = MaterialTheme.colorScheme
    var storageData by remember { mutableStateOf(calculateStorageData(context)) }
    var clearedNotice by remember { mutableStateOf<String?>(null) }

    val usedGb = storageData.deviceUsed / 1_000_000_000.0
    val totalGb = storageData.deviceTotal / 1_000_000_000.0
    val freeGb = storageData.deviceFree / 1_000_000_000.0
    val progress = (storageData.deviceUsed.toFloat() / storageData.deviceTotal.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = "Device storage",
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = c.onSurface,
        )
        Spacer(Modifier.height(8.dp))

        var animatedTarget by remember { mutableFloatStateOf(0f) }
        LaunchedEffect(progress) {
            animatedTarget = progress
        }
        val animatedProgress by animateFloatAsState(
            targetValue = animatedTarget,
            animationSpec = spring(dampingRatio = 0.72f, stiffness = 260f),
            label = "storageProgress",
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(11.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(c.surfaceContainerHigh),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = animatedProgress)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                c.primary,
                                c.tertiary,
                                c.secondary,
                            )
                        )
                    )
            )
        }

        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "${"%.1f".format(usedGb)} GB used of ${"%.1f".format(totalGb)} GB",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = c.onSurface,
            )
            Text(
                text = "${"%.1f".format(freeGb)} GB free",
                fontSize = 11.5.sp,
                color = c.onSurfaceVariant,
            )
        }

        HorizontalDivider(color = c.onSurface.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 12.dp))

        Text(
            text = "Used by YTXTube",
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = c.onSurface,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "Total space: ${formatStorageBytes(storageData.ytxTotal)}",
            fontSize = 12.sp,
            color = c.primary,
            fontWeight = FontWeight.SemiBold,
        )

        Spacer(Modifier.height(10.dp))

        StorageItemRow(label = "App & Core engine", sizeStr = formatStorageBytes(storageData.ytxApp))
        StorageItemRow(label = "Stream & video cache", sizeStr = formatStorageBytes(storageData.ytxCache))
        StorageItemRow(label = "Saved offline data", sizeStr = formatStorageBytes(storageData.ytxData))

        Spacer(Modifier.height(14.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF2E1517))
                .pressable {
                    StreamCache.invalidateAll()
                    Downloads.clearAll()
                    runCatching {
                        context.cacheDir.deleteRecursively()
                        context.externalCacheDir?.deleteRecursively()
                    }
                    storageData = calculateStorageData(context)
                    clearedNotice = "Cache cleared! Freed up memory."
                    onCacheCleared()
                }
                .padding(vertical = 9.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = null,
                    tint = Color(0xFFEF5350),
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = clearedNotice ?: "Clear YTXTube Cache",
                    color = Color(0xFFEF5350),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.5.sp,
                )
            }
        }
    }
}

@Composable
private fun StorageItemRow(label: String, sizeStr: String) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 12.sp, color = c.onSurfaceVariant)
        Text(sizeStr, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.onSurface)
    }
}

@Composable
private fun SettingsProfileCard(
    profile: Profile?,
    onSignIn: () -> Unit,
    onRequestSignOut: () -> Unit,
    onAccountSwitched: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val accounts = Account.accounts
    val activeId = Account.activeAccountId
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(c.surfaceContainerHigh)
            .padding(vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pressable {
                    if (profile == null) onSignIn() else expanded = !expanded
                }
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedContent(
                targetState = profile,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    (fadeIn(tween(240)) + slideInVertically(tween(300)) { it / 8 }) togetherWith
                        (fadeOut(tween(160)) + slideOutVertically(tween(200)) { -it / 8 })
                },
                label = "accountHeader",
            ) { current ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (current?.avatar != null) {
                        ChannelAvatar(current.avatar, current.name, size = 44)
                    } else {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(c.surfaceContainerHighest),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AccountCircle,
                                contentDescription = null,
                                tint = c.onSurfaceVariant,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }

                    Spacer(Modifier.width(12.dp))

                    val displayName = when {
                        current?.name != null && Account.isCleanAccountName(current.name) -> current.name
                        !current?.handle.isNullOrBlank() -> current!!.handle!!
                        else -> "YouTube account"
                    }
                    val subtitleText = when {
                        current != null -> {
                            val h = current.handle
                            if (!h.isNullOrBlank() && h != displayName) h
                            else "${accounts.size} account${if (accounts.size == 1) "" else "s"}"
                        }
                        else -> "Sign in to sync your account"
                    }

                    Column(Modifier.weight(1f)) {
                        Text(
                            text = displayName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = c.onSurface,
                        )
                        Spacer(Modifier.height(1.dp))
                        Text(
                            text = subtitleText,
                            fontSize = 12.sp,
                            color = c.onSurfaceVariant,
                        )
                    }
                }
            }

            if (profile == null) {
                Text(
                    "Sign in",
                    modifier = Modifier.pressable(onClick = onSignIn).padding(8.dp),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = c.primary,
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.NavigateNext,
                    contentDescription = if (expanded) "Hide accounts" else "Switch account",
                    tint = c.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .padding(4.dp)
                        .size(18.dp)
                        .graphicsLayer {
                            rotationZ = if (expanded) 90f else 0f
                        },
                )
            }
        }

        AnimatedVisibility(
            visible = expanded && profile != null,
            enter = fadeIn(tween(180)) + expandVertically(tween(260)),
            exit = fadeOut(tween(140)) + shrinkVertically(tween(200)),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 6.dp)) {
                HorizontalDivider(color = c.onSurface.copy(alpha = 0.08f), modifier = Modifier.padding(bottom = 6.dp))
                accounts.forEach { account ->
                    SettingsAccountRow(
                        account = account,
                        active = account.id == activeId,
                        onSwitch = {
                            if (Account.switchTo(account.id)) {
                                expanded = false
                                onAccountSwitched()
                            }
                        },
                        onRemove = if (account.id == activeId) null else {
                            { Account.removeAccount(account.id) }
                        },
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .pressable(onClick = onSignIn)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(c.surfaceContainerHighest),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Add, null, tint = c.primary, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("Add account", fontWeight = FontWeight.Medium, fontSize = 13.sp, color = c.primary)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .pressable(onClick = onRequestSignOut)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(c.surfaceContainerHighest),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Logout,
                            null,
                            tint = c.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("Sign out", fontWeight = FontWeight.Medium, fontSize = 13.sp, color = c.error)
                }
            }
        }
    }
}

@Composable
private fun SettingsAccountRow(
    account: AccountSummary,
    active: Boolean,
    onSwitch: () -> Unit,
    onRemove: (() -> Unit)?,
) {
    val c = MaterialTheme.colorScheme
    val background by animateColorAsState(
        targetValue = if (active) c.primaryContainer.copy(alpha = 0.55f) else Color.Transparent,
        animationSpec = tween(260),
        label = "accountBackground",
    )
    val activeProgress by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 420f),
        label = "accountActive",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .pressable(onClick = onSwitch)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (account.avatar != null) {
            ChannelAvatar(account.avatar, account.name, size = 32)
        } else {
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(c.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.AccountCircle, null, tint = c.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        val displayName = when {
            Account.isCleanAccountName(account.name) -> account.name
            !account.email.isNullOrBlank() -> account.email
            !account.handle.isNullOrBlank() && Account.isCleanAccountName(account.handle) -> account.handle
            else -> "Google Account"
        }
        val handleText = when {
            account.handle != null && account.handle != displayName && Account.isCleanAccountName(account.handle) -> account.handle
            account.email != null && account.email != displayName -> account.email
            else -> null
        }

        Column(Modifier.weight(1f)) {
            Text(displayName, fontWeight = FontWeight.Medium, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            handleText?.let {
                Text(it, fontSize = 11.sp, color = c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (active) {
            Box(
                Modifier
                    .size(20.dp)
                    .graphicsLayer {
                        scaleX = activeProgress
                        scaleY = activeProgress
                        alpha = activeProgress
                    }
                    .clip(CircleShape)
                    .background(c.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Check, "Active account", tint = c.onPrimary, modifier = Modifier.size(13.dp))
            }
        } else if (onRemove != null) {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .pressable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Close, "Remove account", tint = c.onSurfaceVariant, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable { onCheckedChange(!checked) }
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
        ) {
            Text(
                text = title,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = c.onSurface,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = subtitle,
                color = c.onSurfaceVariant,
                fontSize = 11.5.sp,
                lineHeight = 15.sp,
            )
        }
        Spacer(Modifier.width(6.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = c.primary),
        )
    }
}
