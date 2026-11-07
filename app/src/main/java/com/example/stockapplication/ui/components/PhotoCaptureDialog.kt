package com.example.stockapplication.ui.components

import android.net.Uri
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.stockapplication.R
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.io.File

/**
 * Full-screen camera capture used by the product photo field.
 *
 * Implemented with CameraX directly rather than `ActivityResultContracts.TakePicture` so the app
 * owns the framing UI, and so a capture drops straight into the same [ProductImages] pipeline the
 * gallery path uses — the caller only ever sees a `content://` [Uri] backed by a cache file.
 *
 * The intermediate file lives in `cacheDir` (shared through the existing FileProvider) because the
 * photo is re-encoded and moved into private storage afterwards by `ProductImages.saveImage`.
 * Failed or abandoned captures delete their temp file.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PhotoCaptureDialog(
    onCaptured: (Uri) -> Unit,
    onDismiss: () -> Unit,
) {
    val cameraPermission = rememberPermissionState(android.Manifest.permission.CAMERA)

    LaunchedEffect(Unit) {
        if (!cameraPermission.status.isGranted) cameraPermission.launchPermissionRequest()
    }

    Dialog(
        onDismissRequest = onDismiss,
        // A capture UI should be opaque: the product form showing through a scrim reads as a bug.
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (cameraPermission.status.isGranted) {
                CaptureSurface(onCaptured = onCaptured, onCancel = onDismiss)
            } else {
                CameraPermissionPrompt(
                    onRequest = { cameraPermission.launchPermissionRequest() },
                    onCancel = onDismiss,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

@Composable
private fun CameraPermissionPrompt(
    onRequest: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.photo_capture_permission_rationale),
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Button(onClick = onRequest) {
            Text(stringResource(R.string.scanner_grant_camera_access))
        }
        TextButton(onClick = onCancel) {
            Text(stringResource(R.string.action_cancel), color = Color.White)
        }
    }
}

@Composable
private fun CaptureSurface(
    onCaptured: (Uri) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isBound by remember { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    // Read outside the camera callbacks: `LocalContext` must not be dereferenced from a
    // non-composable lambda that runs after composition has moved on.
    val failedText = stringResource(R.string.photo_capture_failed)

    // Remembered so the shutter lambda and the DisposableEffect share one instance.
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    DisposableEffect(lifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            runCatching {
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
                )
                isBound = true
            }.onFailure {
                errorText = failedText
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose { runCatching { providerFuture.get().unbindAll() } }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        FilledIconButton(
            onClick = onCancel,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.action_cancel)
            )
        }

        errorText?.let { message ->
            Text(
                text = message,
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isCapturing) {
                CircularProgressIndicator(color = Color.White)
            } else {
                FilledIconButton(
                    onClick = {
                        isCapturing = true
                        errorText = null
                        capturePhoto(
                            context = context,
                            imageCapture = imageCapture,
                            onCaptured = { uri ->
                                isCapturing = false
                                onCaptured(uri)
                            },
                            onError = {
                                isCapturing = false
                                errorText = failedText
                            }
                        )
                    },
                    enabled = isBound,
                    modifier = Modifier.size(72.dp),
                    shape = CircleShape
                ) {
                    // Plain white disc reads as a shutter button without pulling in an icon.
                    Box(
                        Modifier
                            .size(56.dp)
                            .background(Color.White, CircleShape)
                    )
                }
            }
            Text(
                text = stringResource(R.string.photo_capture_hint),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White
            )
        }
    }
}

/**
 * Fires the shutter and hands back a `content://` Uri for the cache file.
 *
 * The file is created up front so a failed capture can be cleaned up rather than left in the cache.
 * A successful capture is *not* deleted here: the caller copies it into private storage during the
 * save, and the cache directory is swept by [clearStaleCaptures] on the next open.
 */
private fun capturePhoto(
    context: android.content.Context,
    imageCapture: ImageCapture,
    onCaptured: (Uri) -> Unit,
    onError: () -> Unit,
) {
    clearStaleCaptures(context)
    val target = File(context.cacheDir, "$CAPTURE_PREFIX${System.currentTimeMillis()}.jpg")
    val options = ImageCapture.OutputFileOptions.Builder(target).build()

    imageCapture.takePicture(
        options,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    target
                )
                onCaptured(uri)
            }

            override fun onError(exception: ImageCaptureException) {
                target.delete()
                onError()
            }
        }
    )
}

private const val CAPTURE_PREFIX = "capture-"

/**
 * Deletes captures left behind by abandoned or unsaved sessions.
 *
 * A successful capture is copied into `product_images` on save and its cache file is then
 * redundant, but nothing ties the two together, so anything older than an hour is treated as
 * garbage. Cheap enough to run on every open.
 */
private fun clearStaleCaptures(context: android.content.Context) {
    val cutoff = System.currentTimeMillis() - STALE_CAPTURE_MS
    runCatching {
        context.cacheDir.listFiles { file ->
            file.name.startsWith(CAPTURE_PREFIX) && file.lastModified() < cutoff
        }?.forEach { it.delete() }
    }
}

private const val STALE_CAPTURE_MS = 60L * 60L * 1000L
