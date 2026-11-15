package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.R
import com.example.data.update.DownloadState
import com.example.data.update.UpdateMetadata

@Composable
fun InAppUpdateDialog(
    metadata: UpdateMetadata,
    downloadState: DownloadState,
    canInstallPackages: () -> Boolean,
    buildInstallSettingsIntent: () -> Intent,
    buildInstallApkIntent: (java.io.File) -> Intent,
    onStartDownload: () -> Unit,
    onResetDownload: () -> Unit,
    onOpenFullUpdateScreen: () -> Unit,
    onDismissLater: () -> Unit,
    onEmitMessage: (String) -> Unit
) {
    val context = LocalContext.current
    var autoLaunchedPath by remember(metadata.versionName) { mutableStateOf<String?>(null) }

    LaunchedEffect(downloadState) {
        if (downloadState is DownloadState.Verified &&
            autoLaunchedPath != downloadState.apkFile.absolutePath
        ) {
            autoLaunchedPath = downloadState.apkFile.absolutePath
            if (canInstallPackages()) {
                runCatching {
                    context.startActivity(buildInstallApkIntent(downloadState.apkFile))
                }
            } else {
                onEmitMessage("Allow 'Install unknown apps' for Priyo Nots, then tap Install Update.")
                runCatching {
                    context.startActivity(buildInstallSettingsIntent())
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (!metadata.forceUpdate && downloadState !is DownloadState.Downloading) {
                onDismissLater()
            }
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.priyo_nots_logo),
                    contentDescription = "Priyo Nots",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Update Available",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "v${BuildConfig.VERSION_NAME} → v${metadata.versionName}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = metadata.updateMessage,
                    style = MaterialTheme.typography.bodyMedium
                )

                if (metadata.releaseNotes.isNotBlank()) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 160.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "What's New",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = metadata.releaseNotes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                when (downloadState) {
                    DownloadState.Idle -> Unit
                    is DownloadState.Downloading -> {
                        val pct = if (downloadState.totalBytes > 0L) {
                            " (${(downloadState.progress * 100).toInt()}%)"
                        } else {
                            ""
                        }
                        Text(
                            text = if (downloadState.totalBytes > 0L) {
                                "Downloading APK$pct · ${formatFileSize(downloadState.bytesRead)} / ${formatFileSize(downloadState.totalBytes)}"
                            } else {
                                "Downloading APK · ${formatFileSize(downloadState.bytesRead)}"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium
                        )
                        if (downloadState.totalBytes > 0L) {
                            LinearProgressIndicator(
                                progress = { downloadState.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )
                        }
                    }
                    is DownloadState.Verified -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "APK downloaded & verified. Ready to install.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    is DownloadState.Failed -> {
                        Text(
                            text = downloadState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        dismissButton = {
            if (!metadata.forceUpdate && downloadState !is DownloadState.Downloading) {
                TextButton(
                    onClick = onDismissLater,
                    modifier = Modifier.testTag("inapp_update_later")
                ) {
                    Text("Later", maxLines = 1, softWrap = false)
                }
            }
        },
        confirmButton = {
            when (downloadState) {
                DownloadState.Idle -> {
                    if (metadata.hasDirectApkAsset) {
                        Button(
                            onClick = onStartDownload,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                            modifier = Modifier.testTag("inapp_update_now")
                        ) {
                            Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Update Now", maxLines = 1, softWrap = false)
                        }
                    } else if (metadata.downloadUrl.isNotBlank()) {
                        Button(
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(metadata.downloadUrl))
                                    )
                                }
                                onOpenFullUpdateScreen()
                            },
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Get Update", maxLines = 1, softWrap = false)
                        }
                    }
                }
                is DownloadState.Downloading -> {
                    OutlinedButton(
                        onClick = onOpenFullUpdateScreen,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text("Details", maxLines = 1, softWrap = false)
                    }
                }
                is DownloadState.Verified -> {
                    Button(
                        onClick = {
                            if (!canInstallPackages()) {
                                onEmitMessage("Enable 'Allow from this source' for Priyo Nots, then tap Install Update again.")
                                runCatching { context.startActivity(buildInstallSettingsIntent()) }
                            } else {
                                runCatching { context.startActivity(buildInstallApkIntent(downloadState.apkFile)) }
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        modifier = Modifier.testTag("inapp_install_apk")
                    ) {
                        Icon(Icons.Outlined.InstallMobile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Install Update", maxLines = 1, softWrap = false)
                    }
                }
                is DownloadState.Failed -> {
                    Button(
                        onClick = {
                            onResetDownload()
                            onStartDownload()
                        },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text("Try Again", maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    )
}
