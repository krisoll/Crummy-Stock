package com.example.stockapplication.ui.screens.transfer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stockapplication.R

/**
 * Wi-Fi backup transfer.
 *
 * ## One direction, because the connection is already two-way
 *
 * This screen only **receives**: it starts a server and shows what to type into the desktop app.
 * The desktop connects to that server, and once it is connected it can both push a backup here and
 * pull this phone's data back — so a separate "send" half would be a second way to do something the
 * connection already does, and would ask the user to understand which side initiates what.
 *
 * ## What the screen says out loud
 *
 * The code and the "unencrypted" warning are not decoration. A port reachable by the whole Wi-Fi is
 * the one genuinely risky thing this feature does, so the UI names it rather than burying it.
 *
 * @param receiveState the server's current state.
 */
@Composable
fun TransferScreen(
    wifiAddress: String?,
    port: String,
    code: String,
    localNetworkGranted: Boolean,
    onRequestLocalNetwork: () -> Unit,
    receiveState: ReceiveState,
    receiveProgress: Float?,
    receiveError: String?,
    onStartReceiving: () -> Unit,
    onStopReceiving: () -> Unit,
    onBack: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.transfer_back),
                    )
                }
                Text(
                    stringResource(R.string.transfer_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                WarningCard()

                if (!localNetworkGranted) {
                    PermissionCard(onRequestLocalNetwork)
                }

                // ---- Receive -------------------------------------------------------------
                SectionCard(
                    title = stringResource(R.string.transfer_receive_title),
                    description = stringResource(R.string.transfer_receive_description),
                    icon = { Icon(Icons.Default.Wifi, contentDescription = null) },
                ) {
                    when (receiveState) {
                        ReceiveState.Idle -> {
                            Button(onClick = onStartReceiving, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.transfer_receive_start))
                            }
                        }

                        ReceiveState.Failed -> {
                            Text(
                                stringResource(R.string.transfer_port_unavailable, port),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Button(onClick = onStartReceiving, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.transfer_receive_retry))
                            }
                        }

                        ReceiveState.Waiting, ReceiveState.Receiving -> {
                            LabeledValue(
                                stringResource(R.string.transfer_address),
                                wifiAddress ?: stringResource(R.string.transfer_no_wifi),
                                monospace = true,
                            )
                            LabeledValue(
                                stringResource(R.string.transfer_port),
                                port,
                                monospace = true,
                            )
                            LabeledValue(
                                stringResource(R.string.transfer_code),
                                code,
                                monospace = true,
                                big = true,
                            )
                            Text(
                                stringResource(R.string.transfer_receive_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            if (receiveState == ReceiveState.Receiving) {
                                LinearProgressIndicator(
                                    progress = { receiveProgress ?: 0f },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Text(
                                    stringResource(R.string.transfer_receiving),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                    )
                                    Text(
                                        stringResource(R.string.transfer_waiting),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }

                            TextButton(onClick = onStopReceiving) {
                                Text(stringResource(R.string.transfer_receive_stop))
                            }
                        }

                        ReceiveState.Done -> {
                            Text(
                                stringResource(R.string.transfer_receive_done),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    if (receiveError != null) {
                        Text(
                            receiveError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** The state of the receiving server, as far as the screen is concerned. */
enum class ReceiveState { Idle, Waiting, Receiving, Done, Failed }

@Composable
private fun WarningCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Text(
            stringResource(R.string.transfer_warning),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(12.dp),
        )
    }
}

/**
 * Shown when Android 17 is holding back local network access.
 *
 * The system prompt is raised when the screen opens, but a denial (or a "don't ask again")
 * leaves nothing on screen explaining why every address times out, so this card stays until the
 * permission is granted and offers a second chance to ask.
 */
@Composable
private fun PermissionCard(onRequest: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.transfer_permission_required),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Button(onClick = onRequest) {
                Text(stringResource(R.string.transfer_permission_action))
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    description: String,
    icon: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                icon()
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            content()
        }
    }
}

/** A label above a value, monospaced so an IP and a code are easy to read out and retype. */
@Composable
private fun LabeledValue(
    label: String,
    value: String,
    monospace: Boolean = false,
    big: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = if (big) {
                MaterialTheme.typography.headlineMedium
            } else {
                MaterialTheme.typography.titleMedium
            }.let { if (monospace) it.copy(fontFamily = FontFamily.Monospace) else it },
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Start,
            letterSpacing = if (big) 4.sp else 0.sp,
        )
    }
}
