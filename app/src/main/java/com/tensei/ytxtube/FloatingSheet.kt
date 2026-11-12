package com.tensei.ytxtube

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * A floating rounded card with a dimmed backdrop, matching the General Modification dialog.
 * Drop-in replacement for ModalBottomSheet in settings sheets: [content] may scroll itself,
 * the card is capped to 80% of the screen height.
 */
@Composable
fun FloatingSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = MaterialTheme.colorScheme
    val maxH = (LocalConfiguration.current.screenHeightDp * 0.8f).dp
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 20.dp)
                    .widthIn(max = 410.dp)
                    .fillMaxWidth()
                    .heightIn(max = maxH)
                    .clip(RoundedCornerShape(24.dp))
                    .background(c.surfaceContainerHighest)
                    .border(1.dp, c.onSurface.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
                content = content,
            )
        }
    }
}
