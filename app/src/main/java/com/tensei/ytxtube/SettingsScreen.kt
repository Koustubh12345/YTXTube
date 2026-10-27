package com.tensei.ytxtube

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Security
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onSignedOut: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
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
                }
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


                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Use System Font", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Text(
                            "Use device default font throughout the app",
                            color = c.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = ThemeSettings.useSystemFont,
                        onCheckedChange = { ThemeSettings.updateUseSystemFont(it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = c.primary)
                    )
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
                        FLASHTUBE_THEMES.forEach { add(it.hex to it.name) }
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

            SettingsGroup(title = "SponsorBlock") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Automatically skip segments", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Text("Choose what happens for each type", color = c.onSurfaceVariant, fontSize = 12.sp)
                    }
                    Switch(
                        checked = SponsorBlock.enabled,
                        onCheckedChange = SponsorBlock::updateEnabled,
                        colors = SwitchDefaults.colors(checkedTrackColor = c.primary)
                    )
                }

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
                    subtitle = "YTX Tube ${BuildConfig.VERSION_NAME}",
                )

                HorizontalDivider(color = c.onSurface.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 12.dp))

                val openRepo: (String) -> Unit = { url ->
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                }
                val ytxRepo = "https://github.com/Koustubh12345/YTXTube"
                val flashRepo = "https://github.com/justathinker/flashtube"

                SettingsItem(
                    icon = Icons.Rounded.Code,
                    title = "YTXTube Repo",
                    subtitle = "YTXTube: Lightweight, ad-free YouTube client with high-quality downloads.",
                    onClick = { openRepo(ytxRepo) },
                    trailing = { RepoArrowButton { openRepo(ytxRepo) } },
                )

                HorizontalDivider(color = c.onSurface.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 12.dp))

                SettingsItem(
                    icon = Icons.Rounded.Code,
                    title = "Original FlashTube Repo",
                    subtitle = "Original repo",
                    onClick = { openRepo(flashRepo) },
                    trailing = { RepoArrowButton { openRepo(flashRepo) } },
                )

                HorizontalDivider(color = c.onSurface.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 12.dp))

                SettingsItem(
                    icon = Icons.Rounded.Security,
                    title = "Privacy & Open Source",
                    subtitle = "Ad-free, with no tracker SDKs",
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
) {
    val c = MaterialTheme.colorScheme
    val isLoggedIn = profile != null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (!isLoggedIn) Modifier.pressable(onClick = onSignIn) else Modifier)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isLoggedIn && profile.avatar != null) {
            ChannelAvatar(profile.avatar, profile.name, size = 44)
        } else {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(c.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.AccountCircle,
                    contentDescription = null,
                    tint = c.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = if (isLoggedIn) profile.name else "YouTube account",
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = c.onSurface
            )
            Text(
                text = if (isLoggedIn) (profile.handle ?: "Signed in") else "Sign in to sync your account",
                fontSize = 12.sp,
                color = c.onSurfaceVariant
            )
        }

        if (isLoggedIn) {
            Text(
                "Sign out",
                modifier = Modifier.pressable(onClick = onSignOut).padding(8.dp),
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = c.onSurfaceVariant,
            )
        } else {
            Text(
                "Sign in",
                modifier = Modifier.pressable(onClick = onSignIn).padding(8.dp),
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = c.primary,
            )
        }
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
