package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.data.local.entity.AppSettingsEntity
import com.example.data.update.DownloadState
import com.example.ui.components.formatFileSize
import com.example.ui.components.formatTimestamp
import com.example.ui.viewmodel.PriyoNotsViewModel
import com.example.ui.viewmodel.UpdateCheckUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateScreen(
    viewModel: PriyoNotsViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val downloadState by viewModel.downloadState.collectAsStateWithLifecycle()

    var githubRepoInput by remember(settings.githubReleaseRepo) {
        mutableStateOf(settings.githubReleaseRepo)
    }
    var endpointInput by remember(settings.updateEndpointUrl) {
        mutableStateOf(settings.updateEndpointUrl)
    }
    val useGitHub = settings.updateSourceType == AppSettingsEntity.UPDATE_SOURCE_GITHUB

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("update_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("App Updates") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Installed Version", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = "Priyo Nots v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    settings.lastUpdateCheckAt?.let {
                        Text(
                            text = "Last checked: ${formatTimestamp(it)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Card(
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Update Source", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = useGitHub,
                            onClick = {
                                viewModel.updateSettings {
                                    it.copy(updateSourceType = AppSettingsEntity.UPDATE_SOURCE_GITHUB)
                                }
                            },
                            label = { Text("GitHub Releases") },
                            modifier = Modifier.testTag("update_source_github")
                        )
                        FilterChip(
                            selected = !useGitHub,
                            onClick = {
                                viewModel.updateSettings {
                                    it.copy(updateSourceType = AppSettingsEntity.UPDATE_SOURCE_CUSTOM_JSON)
                                }
                            },
                            label = { Text("Custom HTTPS JSON") },
                            modifier = Modifier.testTag("update_source_json")
                        )
                    }

                    if (useGitHub) {
                        Text(
                            text = "Checks latest GitHub Release tag and downloads attached .apk asset directly.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = githubRepoInput,
                            onValueChange = { githubRepoInput = it },
                            label = { Text("GitHub Repository (owner/repo)") },
                            placeholder = { Text("mohammadparvaj/priyo-nots") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("github_release_repo_input")
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = {
                                    val cleaned = githubRepoInput.trim()
                                    viewModel.updateSettings { it.copy(githubReleaseRepo = cleaned) }
                                    viewModel.emitMessage("GitHub release repository saved")
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Save Repo")
                            }
                            Button(
                                onClick = {
                                    val cleaned = githubRepoInput.trim()
                                    viewModel.updateSettings { it.copy(githubReleaseRepo = cleaned) }
                                    viewModel.checkForUpdates(isAutomaticStartup = false)
                                },
                                modifier = Modifier.weight(1f).testTag("check_updates_button")
                            ) {
                                Icon(Icons.Outlined.Refresh, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Check Releases")
                            }
                        }
                    } else {
                        Text(
                            text = "Enter an HTTPS JSON endpoint returning versionCode, versionName, updateTitle, updateMessage, downloadUrl, releaseNotes, and forceUpdate.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = endpointInput,
                            onValueChange = { endpointInput = it },
                            label = { Text("HTTPS Update JSON URL") },
                            placeholder = { Text("https://your-domain.example/priyo-nots-update.json") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("update_url_input")
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = {
                                    val cleaned = endpointInput.trim()
                                    viewModel.updateSettings { it.copy(updateEndpointUrl = cleaned) }
                                    viewModel.emitMessage("Update endpoint saved")
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Save URL")
                            }
                            Button(
                                onClick = {
                                    val cleaned = endpointInput.trim()
                                    viewModel.updateSettings { it.copy(updateEndpointUrl = cleaned) }
                                    viewModel.checkForUpdates(isAutomaticStartup = false)
                                },
                                modifier = Modifier.weight(1f).testTag("check_updates_button")
                            ) {
                                Icon(Icons.Outlined.Refresh, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Check Now")
                            }
                        }
                    }
                }
            }

            when (val st = updateState) {
                UpdateCheckUiState.Idle -> Unit
                UpdateCheckUiState.Checking -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Checking for updates...")
                    }
                }
                is UpdateCheckUiState.UpToDate -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                        )
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("You are on the latest version of Priyo Nots.")
                        }
                    }
                }
                is UpdateCheckUiState.Error -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Text(
                            text = st.message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
                is UpdateCheckUiState.Available -> {
                    val meta = st.metadata
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(meta.updateTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(
                                text = "Current: v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})  →  Latest: v${meta.versionName}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(meta.updateMessage, style = MaterialTheme.typography.bodyMedium)
                            if (meta.releaseNotes.isNotBlank()) {
                                Text("Release Notes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(meta.releaseNotes, style = MaterialTheme.typography.bodySmall)
                            }

                            when (val dl = downloadState) {
                                DownloadState.Idle -> {
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        if (meta.hasDirectApkAsset) {
                                            Button(
                                                onClick = { viewModel.startUpdateDownload(meta) },
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Outlined.Download, contentDescription = null)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Update Now")
                                            }
                                        } else if (meta.downloadUrl.isNotBlank()) {
                                            Button(
                                                onClick = {
                                                    runCatching {
                                                        context.startActivity(
                                                            Intent(Intent.ACTION_VIEW, Uri.parse(meta.downloadUrl))
                                                        )
                                                    }
                                                },
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("View Release")
                                            }
                                        }
                                        if (!meta.forceUpdate) {
                                            OutlinedButton(
                                                onClick = { viewModel.dismissUpdateForVersion(meta.versionCode) },
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("Later")
                                            }
                                        }
                                    }
                                }
                                is DownloadState.Downloading -> {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = if (dl.totalBytes > 0) {
                                                "Downloading APK: ${formatFileSize(dl.bytesRead)} / ${formatFileSize(dl.totalBytes)}"
                                            } else {
                                                "Downloading APK: ${formatFileSize(dl.bytesRead)}"
                                            },
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                        if (dl.totalBytes > 0) {
                                            LinearProgressIndicator(
                                                progress = { dl.progress },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        } else {
                                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                        }
                                    }
                                }
                                is DownloadState.Verified -> {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = "APK verified (${dl.sha256.take(12)}...). Ready to install.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.tertiary
                                        )
                                        Button(
                                            onClick = {
                                                if (!viewModel.canInstallPackages()) {
                                                    context.startActivity(viewModel.buildInstallSettingsIntent())
                                                    viewModel.emitMessage("Allow unknown app installs for Priyo Nots, then tap Install APK again.")
                                                } else {
                                                    context.startActivity(viewModel.buildInstallApkIntent(dl.apkFile))
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Open Package Installer")
                                        }
                                    }
                                }
                                is DownloadState.Failed -> {
                                    Text(dl.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                    OutlinedButton(onClick = { viewModel.resetDownloadState() }) {
                                        Text("Try Again")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
