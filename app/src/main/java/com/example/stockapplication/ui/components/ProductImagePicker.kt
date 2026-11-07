package com.example.stockapplication.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.stockapplication.R

/**
 * Photo section of the product form: a preview, an add/change button and a remove button.
 *
 * Tapping the photo opens a small sheet offering the two sources, so the user never has to guess
 * which one is wired up:
 *  - **Camera** — [PhotoCaptureDialog], an in-app CameraX capture.
 *  - **Gallery** — the system photo picker ([ActivityResultContracts.PickVisualMedia]), which hands
 *    back a single-image grant and needs **no** storage permission on any supported API level.
 *
 * Both paths end up as a `content://` [android.net.Uri]; the composable only reports it. Copying it
 * into app storage is the ViewModel's job so the form stays free of file I/O.
 */
@Composable
fun ProductImagePicker(
    /** Currently stored file name, used for the preview. Null when no photo is set. */
    currentFileName: String?,
    /** Newly picked image that hasn't been copied to app storage yet; takes precedence. */
    pendingUri: android.net.Uri?,
    onPick: (android.net.Uri) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasImage = (pendingUri != null) || (currentFileName != null)
    var showSourceSheet by remember { mutableStateOf(false) }
    var showCamera by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) onPick(uri) }

    val launchGallery = {
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            stringResource(R.string.product_image_label),
            style = MaterialTheme.typography.labelLarge
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                onClick = { showSourceSheet = true },
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                color = MaterialTheme.colorScheme.surface
            ) {
                if (hasImage) {
                    if (pendingUri != null) {
                        ProductThumbnail(
                            uri = pendingUri,
                            size = 88.dp,
                            cornerRadius = 16.dp
                        )
                    } else {
                        ProductThumbnail(
                            fileName = currentFileName,
                            size = 88.dp,
                            cornerRadius = 16.dp
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier.size(88.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                Icons.Default.AddAPhoto,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                stringResource(R.string.product_image_choose),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    stringResource(
                        if (hasImage) R.string.product_image_change
                        else R.string.product_image_choose
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    stringResource(R.string.product_image_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (hasImage) {
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.product_image_remove)
                    )
                }
            }
        }
    }

    if (showSourceSheet) {
        PhotoSourceSheet(
            onCamera = {
                showSourceSheet = false
                showCamera = true
            },
            onGallery = {
                showSourceSheet = false
                launchGallery()
            },
            onDismiss = { showSourceSheet = false }
        )
    }

    if (showCamera) {
        PhotoCaptureDialog(
            onCaptured = { uri ->
                showCamera = false
                onPick(uri)
            },
            onDismiss = { showCamera = false }
        )
    }
}

/**
 * The "where does this photo come from" chooser.
 *
 * A bottom sheet rather than a dialog: the two options are a navigation decision, and the sheet is
 * the platform-idiomatic place for that on phones.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoSourceSheet(
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                stringResource(R.string.product_image_source_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
            )
            PhotoSourceRow(
                icon = Icons.Default.PhotoCamera,
                label = stringResource(R.string.product_image_source_camera),
                onClick = onCamera
            )
            PhotoSourceRow(
                icon = Icons.Default.PhotoLibrary,
                label = stringResource(R.string.product_image_source_gallery),
                onClick = onGallery
            )
        }
    }
}

@Composable
private fun PhotoSourceRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
