package com.devuloopers.knet.companion.sharedui.screen.connect

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.companion.sharedui.component.*
import com.devuloopers.knet.companion.sharedui.generated.resources.*
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import org.jetbrains.compose.resources.stringResource

/** Dismissible, app-owned recovery shown only after an explicit Scan tap needs native Settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CameraPermissionSheet(onDismiss: () -> Unit, onSettings: () -> Unit, onChooseImage: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = KNetTheme.shapes.extraLarge, containerColor = KNetTheme.colors.surfaceVariant,
        contentColor = KNetTheme.colors.textPrimary) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(Modifier.fillMaxWidth()) {
                Box(Modifier.align(Alignment.Center).size(56.dp).clip(CircleShape)
                    .background(KNetTheme.colors.accent.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                    Icon(KNetIcons.Camera, null, Modifier.size(30.dp), tint = KNetTheme.colors.accent)
                }
                IconButton(onDismiss, Modifier.align(Alignment.TopEnd)) {
                    Icon(KNetIcons.Close, stringResource(Res.string.dismiss))
                }
            }
            CompanionHeading(stringResource(Res.string.camera_enable_title), stringResource(Res.string.camera_enable_summary))
            CompanionActionButton(stringResource(Res.string.camera_open_settings), onSettings)
            ChooseQrImage(onClick = onChooseImage)
            CompanionActionButton(stringResource(Res.string.not_now), onDismiss, variant = ButtonVariant.Ghost)
        }
    }
}
