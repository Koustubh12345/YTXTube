package com.tensei.ytxtube

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    val profile = Account.profile
    val scrollState = rememberScrollState()
    var cacheMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.surface)
    ) {
        BackScreenHeader(onBack = onBack) {
            Text(
                text = "Settings",
                fontFamily = RoundedHeavy,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                letterSpacing = (-0.5).sp,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
                .padding(bottom = contentPadding.calculateBottomPadding() + 28.dp)
        ) {
            Spacer(Modifier.height(4.dp))
            SettingsProfileCard(
                profile = profile,
                onSignIn = onSignIn,
                onSignOut = {
                    Account.signOut()
                    onSignedOut()
                },
                onAccountSwitched = onAccountSwitched,
            )
            HorizontalDivider(
                modifier = Modifier.padding(top = 12.dp),
                color = c.onSurface.copy(alpha = 0.08f),
            )

            SettingsGroup(title = "Appearance & Theme") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Theme mode", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            Text("Match your preferred appearance", color = c.onSurfaceVariant, fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf("system" to "System", "light" to "Light", "dark" to "Dark", "amoled" to "AMOLED").forEach { (key, label) ->
                            val on = ThemeSettings.mode == key
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .pressable { ThemeSettings.chooseMode(key) }
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
                                            androidx.compose.ui.graphics.Brush.linearGradient(
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

            SettingsGroup(title = "General") {
                SettingsSwitchRow(
                    title = "Use System Font",
                    subtitle = "Use device default font throughout the app",
                    checked = ThemeSettings.useSystemFont,
                    onCheckedChange = { ThemeSettings.updateUseSystemFont(it) },
                )

                HorizontalDivider(color = c.onSurface.copy(alpha = 0.08f))

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

                HorizontalDivider(color = c.onSurface.copy(alpha = 0.08f))

                SettingsSwitchRow(
                    title = "Video gestures",
                    subtitle = "Swipe vertically on the left for brightness, right for volume",
                    checked = VideoGestureSettings.enabled,
                    onCheckedChange = { VideoGestureSettings.set(it) },
                )
            }

            SettingsGroup(title = "SponsorBlock") {
                SettingsSwitchRow(
                    title = "Automatically skip segments",
                    subtitle = "Choose what happens for each type",
                    checked = SponsorBlock.enabled,
                    onCheckedChange = SponsorBlock::updateEnabled,
                )

                if (SponsorBlock.enabled) {
                    HorizontalDivider(color = c.onSurface.copy(alpha = 0.08f))
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        SponsorBlock.Category.entries.forEach { category ->
                            val current = SponsorBlock.actionFor(category)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier
                                        .padding(end = 8.dp)
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(category.color)
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(category.label, fontWeight = FontWeight.Medium, fontSize = 12.5.sp, maxLines = 2)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    SponsorBlock.Action.entries.forEach { action ->
                                        val on = action == current
                                        Box(
                                            modifier = Modifier
                                                .pressable { SponsorBlock.setAction(category, action) }
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (on) c.secondaryContainer else Color.Transparent)
                                                .padding(horizontal = 7.dp, vertical = 7.dp),
                                        ) {
                                            Text(
                                                action.label,
                                                color = if (on) c.onSecondaryContainer else c.onSurfaceVariant,
                                                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                                                fontSize = 10.5.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            SettingsGroup(title = "Data & Storage") {
                SettingsItem(
                    icon = Icons.Rounded.CleaningServices,
                    title = "Clear Data",
                    subtitle = cacheMessage ?: "Delete cached streams, videos and music downloads",
                    onClick = {
                        StreamCache.invalidateAll()
                        Downloads.clearAll()
                        cacheMessage = "All downloads and stream cache deleted"
                    },
                    trailing = {
                        Text("Delete", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = c.primary)
                    }
                )
            }

            SettingsGroup(title = "About") {
                SettingsItem(
                    icon = Icons.Rounded.Info,
                    title = "Version",
                    subtitle = "YTXTube ${BuildConfig.VERSION_NAME}",
                )

                HorizontalDivider(color = c.onSurface.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 12.dp))

                SettingsItem(
                    icon = Icons.Rounded.SystemUpdate,
                    title = "Check for updates",
                    subtitle = "New GitHub releases install from inside the app",
                    onClick = onCheckUpdate,
                    trailing = { RepoArrowButton(onClick = onCheckUpdate) },
                )

                HorizontalDivider(color = c.onSurface.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 12.dp))

                val openRepo: (String) -> Unit = { url ->
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                }
                val ytxRepo = "https://github.com/Koustubh12345/YTXTube"


                SettingsItem(
                    icon = Icons.Rounded.Code,
                    title = "YTXTube Repo",
                    subtitle = "YTXTube: Lightweight, ad-free YouTube client with high-quality downloads.",
                    onClick = { openRepo(ytxRepo) },
                    trailing = { RepoArrowButton { openRepo(ytxRepo) } },
                )

                HorizontalDivider(color = c.onSurface.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 12.dp))

                SettingsItem(
                    icon = Icons.Rounded.Security,
                    title = "Privacy & Open Source",
                    subtitle = "Ad-free, with no tracker SDKs",
                )

                HorizontalDivider(color = c.onSurface.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 12.dp))

                val telegram = "https://t.me/getthefckoutofheree"
                val openTelegram: () -> Unit = {
                    val app = Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=getthefckoutofheree"))
                    val web = Intent(Intent.ACTION_VIEW, Uri.parse(telegram))
                    runCatching { context.startActivity(app) }
                        .onFailure { runCatching { context.startActivity(web) } }
                }
                SettingsItem(
                    icon = Icons.Rounded.Person,
                    title = "Developer",
                    subtitle = "@getthefckoutofheree",
                    onClick = openTelegram,
                    trailing = { RepoArrowButton(onClick = openTelegram) },
                )
            }
        }
    }
}

@Composable
private fun SettingsProfileCard(
    profile: Profile?,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onAccountSwitched: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val accounts = Account.accounts
    val activeId = Account.activeAccountId
    var expanded by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pressable {
                    if (profile == null) onSignIn() else expanded = !expanded
                },
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
                                .background(c.surfaceContainerHigh),
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

                    Spacer(Modifier.width(14.dp))

                    Column(Modifier.weight(1f)) {
                        Text(
                            text = current?.name ?: "YouTube account",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = c.onSurface,
                        )
                        Text(
                            text = if (current != null) {
                                current.handle ?: "${accounts.size} account${if (accounts.size == 1) "" else "s"}"
                            } else {
                                "Sign in to sync your account"
                            },
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
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) "Hide accounts" else "Switch account",
                    tint = c.onSurfaceVariant,
                    modifier = Modifier.padding(6.dp).size(22.dp),
                )
            }
        }

        AnimatedVisibility(
            visible = expanded && profile != null,
            enter = fadeIn(tween(180)) + expandVertically(tween(260)),
            exit = fadeOut(tween(140)) + shrinkVertically(tween(200)),
        ) {
            Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
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
                        .clip(RoundedCornerShape(16.dp))
                        .pressable(onClick = onSignIn)
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).background(c.surfaceContainerHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Add, null, tint = c.primary, modifier = Modifier.size(19.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("Add account", fontWeight = FontWeight.Medium, fontSize = 13.5.sp, color = c.primary)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .pressable(onClick = onSignOut)
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).background(c.surfaceContainerHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Logout,
                            null,
                            tint = c.error,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("Sign out", fontWeight = FontWeight.Medium, fontSize = 13.5.sp, color = c.error)
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
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .pressable(onClick = onSwitch)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (account.avatar != null) {
            ChannelAvatar(account.avatar, account.name, size = 34)
        } else {
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(c.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.AccountCircle, null, tint = c.onSurfaceVariant, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(account.name, fontWeight = FontWeight.Medium, fontSize = 13.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            account.handle?.let {
                Text(it, fontSize = 11.5.sp, color = c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (active) {
            Box(
                Modifier
                    .size(22.dp)
                    .graphicsLayer {
                        scaleX = activeProgress
                        scaleY = activeProgress
                        alpha = activeProgress
                    }
                    .clip(CircleShape)
                    .background(c.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Check, "Active account", tint = c.onPrimary, modifier = Modifier.size(14.dp))
            }
        } else if (onRemove != null) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .pressable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Close, "Remove account", tint = c.onSurfaceVariant, modifier = Modifier.size(15.dp))
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
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp),
        ) {
            Text(
                text = title,
                fontWeight = FontWeight.Medium,
                fontSize = 14.5.sp,
                color = c.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = c.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = c.primary),
        )
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = c.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Column(Modifier.fillMaxWidth()) { content() }
    }
}

@Composable
private fun SettingsItem(
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
            .then(onClick?.let { Modifier.pressable(onClick = it) } ?: Modifier)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIcon(icon)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = c.onSurface)
            Text(subtitle, fontSize = 12.5.sp, color = c.onSurfaceVariant)
        }
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            trailing()
        }
    }
}

@Composable
private fun SettingsIcon(icon: ImageVector) {
    val c = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(c.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = c.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}


@Composable
private fun RepoArrowButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color(0xFFE53935))
            .pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.AutoMirrored.Rounded.ArrowForward,
            contentDescription = "Open repository",
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
    }
}
