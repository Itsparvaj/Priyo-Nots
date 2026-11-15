package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.backup.BackupCrypto
import com.example.data.backup.RestoreMode
import com.example.data.drive.GoogleDriveAuthHelper
import com.example.data.local.entity.AppSettingsEntity
import com.example.ui.components.formatFileSize
import com.example.ui.components.formatTimestamp
import com.example.ui.viewmodel.BackupOperationState
import com.example.ui.viewmodel.PriyoNotsViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    viewModel: PriyoNotsViewModel,
    onNavigateBack: () -> Unit,
    onOpenHistory: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val opState by viewModel.backupState.collectAsStateWithLifecycle()
    val githubGists by viewModel.githubGists.collectAsStateWithLifecycle()
    val githubBusy by viewModel.githubBusy.collectAsStateWithLifecycle()
    val driveFiles by viewModel.driveFiles.collectAsStateWithLifecycle()
    val driveBusy by viewModel.driveBusy.collectAsStateWithLifecycle()

    var encryptEnabled by remember { mutableStateOf(false) }
    var backupPassword by remember { mutableStateOf("") }

    // Pending restore state (local Uri or cloud raw text)
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCloudRestoreText by remember { mutableStateOf<String?>(null) }
    var restoreRequiresPassword by remember { mutableStateOf(false) }
    var restorePassword by remember { mutableStateOf("") }
    var restoreMode by remember { mutableStateOf(RestoreMode.MERGE) }

    // Google Drive state
    var driveAccessToken by remember { mutableStateOf<String?>(null) }
    var pendingDriveAction by remember { mutableStateOf<String?>(null) }
    var showDrivePickerDialog by remember { mutableStateOf(false) }
    val isDriveConnected = settings.googleDriveEmail.isNotBlank()

    // GitHub Cloud config state
    var showGitHubTokenDialog by remember { mutableStateOf(false) }
    var showGistPickerDialog by remember { mutableStateOf(false) }
    var gitHubTokenInput by remember { mutableStateOf("") }
    var repoSlugInput by remember(settings.githubRepoSlug) { mutableStateOf(settings.githubRepoSlug) }
    var repoBranchInput by remember(settings.githubRepoBranch) { mutableStateOf(settings.githubRepoBranch) }
    var repoPathInput by remember(settings.githubRepoFilePath) { mutableStateOf(settings.githubRepoFilePath) }
    val isGitHubConnected = settings.githubUsername.isNotBlank() && viewModel.hasGitHubToken()
    val isGistMode = settings.githubBackupMode != AppSettingsEntity.GITHUB_MODE_REPO

    val driveAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        val action = pendingDriveAction
        pendingDriveAction = null
        GoogleDriveAuthHelper.extractTokenFromConsentResult(context, activityResult.data)
            .onSuccess { token ->
                driveAccessToken = token
                viewModel.onGoogleDriveAuthorized(token, null)
                when (action) {
                    "BACKUP" -> viewModel.backupToGoogleDrive(
                        accessToken = token,
                        knownEmail = null,
                        password = if (encryptEnabled) backupPassword else null
                    )
                    "RESTORE" -> {
                        viewModel.refreshGoogleDriveBackups(token)
                        showDrivePickerDialog = true
                    }
                }
            }
            .onFailure { err ->
                viewModel.emitMessage(err.message ?: "Google Drive authorization failed")
            }
    }

    fun startGoogleDriveAction(action: String, forceAccountPicker: Boolean = false) {
        if (action == "BACKUP" && encryptEnabled && backupPassword.isBlank()) {
            viewModel.emitMessage("Enter a password for encrypted backup")
            return
        }
        val activity = context.findActivity()
        if (activity == null) {
            viewModel.emitMessage("Unable to open Google account picker from this context.")
            return
        }
        pendingDriveAction = action
        scope.launch {
            GoogleDriveAuthHelper.connectAndAuthorize(
                activity = activity,
                promptAccountSelection = forceAccountPicker,
                onNeedResolution = { intentSenderReq ->
                    driveAuthLauncher.launch(intentSenderReq)
                },
                onAuthorized = { token, email ->
                    pendingDriveAction = null
                    driveAccessToken = token
                    viewModel.onGoogleDriveAuthorized(token, email)
                    when (action) {
                        "BACKUP" -> viewModel.backupToGoogleDrive(
                            accessToken = token,
                            knownEmail = email,
                            password = if (encryptEnabled) backupPassword else null
                        )
                        "RESTORE" -> {
                            viewModel.refreshGoogleDriveBackups(token)
                            showDrivePickerDialog = true
                        }
                    }
                },
                onError = { errMsg ->
                    pendingDriveAction = null
                    viewModel.emitMessage(errMsg)
                }
            )
        }
    }

    val createBackupDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val ext = if (encryptEnabled && backupPassword.isNotBlank()) "pnbak" else "json"
            val name = "PriyoNots_Backup_$stamp.$ext"
            viewModel.runManualBackupToUri(
                uri = uri,
                displayName = name,
                password = if (encryptEnabled) backupPassword.takeIf { it.isNotBlank() } else null
            )
        }
    }

    val pickFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            val label = treeUri.lastPathSegment?.substringAfterLast(':') ?: "Selected Folder"
            viewModel.setBackupFolder(treeUri, label)
        }
    }

    val pickRestoreFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val needsPw = viewModel.inspectRequiresPassword(uri)
                restoreRequiresPassword = needsPw
                restorePassword = ""
                pendingRestoreUri = uri
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("backup_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Backup & Restore") },
                actions = {
                    IconButton(onClick = onOpenHistory, modifier = Modifier.testTag("backup_history_action")) {
                        Icon(Icons.Outlined.History, contentDescription = "Backup History")
                    }
                }
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
            // Storage Notice Banner
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Important: Backups saved only in app-private storage do not survive uninstalling the app. Choose an external folder or export a backup copy before uninstalling or switching devices.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            // Operation progress / result banner
            when (val st = opState) {
                is BackupOperationState.InProgress -> {
                    Card {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(st.message, style = MaterialTheme.typography.titleSmall)
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { st.progress },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                is BackupOperationState.BackupDone -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("Backup Complete", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                "${st.result.fileName} · ${formatFileSize(st.result.fileSizeBytes)} · ${st.result.noteCount} notes, ${st.result.categoryCount} categories, ${st.result.reminderCount} reminders",
                                style = MaterialTheme.typography.bodySmall
                            )
                            TextButton(onClick = { viewModel.resetBackupState() }) { Text("Dismiss") }
                        }
                    }
                }
                is BackupOperationState.RestoreDone -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("Restore Successful", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                "Added ${st.summary.notesAdded}, updated ${st.summary.notesUpdated}, skipped ${st.summary.notesSkippedDuplicate} duplicates, ${st.summary.categoriesImported} categories, ${st.summary.remindersImported} reminders.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            TextButton(onClick = { viewModel.resetBackupState() }) { Text("Dismiss") }
                        }
                    }
                }
                is BackupOperationState.Error -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("Operation Failed", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                            Text(st.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                            TextButton(onClick = { viewModel.resetBackupState() }) { Text("Dismiss") }
                        }
                    }
                }
                BackupOperationState.Idle -> Unit
            }

            // Manual Backup Card
            Card(
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Backup, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Manual Backup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "Includes all active, pinned, archived, and trashed notes, categories, reminders, and settings in a versioned JSON file.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                            Text("Password-protect (AES-256)", style = MaterialTheme.typography.bodyMedium)
                        }
                        Switch(checked = encryptEnabled, onCheckedChange = { encryptEnabled = it })
                    }

                    if (encryptEnabled) {
                        OutlinedTextField(
                            value = backupPassword,
                            onValueChange = { backupPassword = it },
                            label = { Text("Encryption password") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                if (encryptEnabled && backupPassword.isBlank()) {
                                    viewModel.emitMessage("Enter a password for encrypted backup")
                                    return@Button
                                }
                                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                                val ext = if (encryptEnabled) "pnbak" else "json"
                                createBackupDocLauncher.launch("PriyoNots_Backup_$stamp.$ext")
                            },
                            modifier = Modifier.weight(1f).testTag("save_backup_to_device_button")
                        ) {
                            Icon(Icons.Outlined.SaveAlt, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Choose File")
                        }
                        OutlinedButton(
                            onClick = {
                                if (encryptEnabled && backupPassword.isBlank()) {
                                    viewModel.emitMessage("Enter a password for encrypted backup")
                                    return@OutlinedButton
                                }
                                viewModel.runManualBackupToAppStorage(
                                    if (encryptEnabled) backupPassword else null
                                )
                            },
                            modifier = Modifier.weight(1f).testTag("quick_local_backup_button")
                        ) {
                            Text("Quick Local")
                        }
                    }
                }
            }

            // Automatic Backup Card
            Card(
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Automatic Backup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                text = "Last backup: ${settings.lastBackupAt?.let { formatTimestamp(it) } ?: "Never"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Status: ${settings.lastBackupStatus}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Switch(
                            checked = settings.autoBackupEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.updateSettings { it.copy(autoBackupEnabled = enabled) }
                            }
                        )
                    }

                    HorizontalDivider()
                    Text("Backup Frequency", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(6 to "6h", 12 to "12h", 24 to "Daily", 168 to "Weekly").forEach { (hrs, label) ->
                            FilterChip(
                                selected = settings.backupFrequencyHours == hrs,
                                onClick = { viewModel.updateSettings { it.copy(backupFrequencyHours = hrs) } },
                                label = { Text(label) }
                            )
                        }
                    }

                    Text("Backup Folder: ${settings.backupFolderLabel}", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { pickFolderLauncher.launch(null) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.FolderOpen, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Select Folder")
                        }
                        OutlinedButton(
                            onClick = { viewModel.runAutoBackupNow() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Run Now")
                        }
                    }
                }
            }

            // Restore Backup Card
            Card(
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Restore, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Restore Backup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "Import a Priyo Nots JSON or encrypted (.pnbak) backup file. Files are validated before restoration.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { pickRestoreFileLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxWidth().testTag("import_restore_button")
                    ) {
                        Text("Import & Restore Backup File")
                    }
                    OutlinedButton(
                        onClick = onOpenHistory,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.History, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("View Backup History")
                    }
                }
            }

            // Google Drive Cloud Backup Card
            Card(
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = if (isDriveConnected) Icons.Outlined.CloudDone else Icons.Outlined.CloudUpload,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Google Drive Backup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    text = if (isDriveConnected) "Connected as ${settings.googleDriveEmail}" else "Not connected",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isDriveConnected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (isDriveConnected) {
                            TextButton(onClick = {
                                driveAccessToken = null
                                viewModel.disconnectGoogleDrive()
                            }) {
                                Text("Disconnect", color = MaterialTheme.colorScheme.error)
                            }
                        } else {
                            OutlinedButton(
                                onClick = { startGoogleDriveAction("CONNECT", forceAccountPicker = true) },
                                modifier = Modifier.testTag("connect_google_drive_button")
                            ) {
                                Icon(Icons.Outlined.CloudUpload, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Connect")
                            }
                        }
                    }

                    Text(
                        text = "Back up and restore notes directly to your Google Drive 'Priyo Nots Backups' folder. Works with AES-256 encryption when enabled above.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    settings.googleDriveLastBackupAt?.let { syncedAt ->
                        Text(
                            text = "Last Drive backup: ${formatTimestamp(syncedAt)} · ${settings.googleDriveLastStatus}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (driveBusy) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Connecting to Google Drive...", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                startGoogleDriveAction("BACKUP", forceAccountPicker = !isDriveConnected)
                            },
                            modifier = Modifier.weight(1f).testTag("drive_backup_now_button")
                        ) {
                            Icon(Icons.Outlined.CloudUpload, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Backup")
                        }
                        OutlinedButton(
                            onClick = {
                                startGoogleDriveAction("RESTORE", forceAccountPicker = !isDriveConnected)
                            },
                            modifier = Modifier.weight(1f).testTag("drive_restore_button")
                        ) {
                            Icon(Icons.Outlined.CloudDownload, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Restore")
                        }
                    }
                }
            }

            // GitHub Cloud Backup (Secret Gist & Repository) Card
            Card(
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.CloudUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("GitHub Cloud Backup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    text = if (isGitHubConnected) "Connected as @${settings.githubUsername}" else "Not connected",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isGitHubConnected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (isGitHubConnected) {
                            TextButton(onClick = { viewModel.disconnectGitHub() }) {
                                Text("Disconnect", color = MaterialTheme.colorScheme.error)
                            }
                        } else {
                            OutlinedButton(
                                onClick = { showGitHubTokenDialog = true },
                                modifier = Modifier.testTag("connect_github_button")
                            ) {
                                Icon(Icons.Outlined.Key, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Connect")
                            }
                        }
                    }

                    Text(
                        text = "Sync backups to a private GitHub Secret Gist or Repository. Enable AES-256 encryption above to encrypt notes before uploading.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = isGistMode,
                            onClick = {
                                viewModel.updateSettings { it.copy(githubBackupMode = AppSettingsEntity.GITHUB_MODE_GIST) }
                            },
                            label = { Text("Secret Gist") }
                        )
                        FilterChip(
                            selected = !isGistMode,
                            onClick = {
                                viewModel.updateSettings { it.copy(githubBackupMode = AppSettingsEntity.GITHUB_MODE_REPO) }
                            },
                            label = { Text("Repository File") }
                        )
                    }

                    if (!isGistMode) {
                        OutlinedTextField(
                            value = repoSlugInput,
                            onValueChange = { repoSlugInput = it },
                            label = { Text("Repository (owner/repo)") },
                            placeholder = { Text("mohammadparvaj/priyo-nots-backups") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = repoBranchInput,
                                onValueChange = { repoBranchInput = it },
                                label = { Text("Branch") },
                                singleLine = true,
                                modifier = Modifier.weight(0.4f)
                            )
                            OutlinedTextField(
                                value = repoPathInput,
                                onValueChange = { repoPathInput = it },
                                label = { Text("File path") },
                                singleLine = true,
                                modifier = Modifier.weight(0.6f)
                            )
                        }
                    } else if (settings.githubGistId.isNotBlank()) {
                        Text(
                            text = "Linked Gist ID: ${settings.githubGistId}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    settings.githubLastBackupAt?.let { syncedAt ->
                        Text(
                            text = "Last GitHub sync: ${formatTimestamp(syncedAt)} · ${settings.githubLastStatus}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                if (!isGitHubConnected) {
                                    showGitHubTokenDialog = true
                                    return@Button
                                }
                                if (encryptEnabled && backupPassword.isBlank()) {
                                    viewModel.emitMessage("Enter a password for encrypted backup")
                                    return@Button
                                }
                                val pw = if (encryptEnabled) backupPassword else null
                                if (isGistMode) {
                                    viewModel.backupToGitHubGist(pw)
                                } else {
                                    viewModel.backupToGitHubRepo(repoSlugInput, repoBranchInput, repoPathInput, pw)
                                }
                            },
                            modifier = Modifier.weight(1f).testTag("github_backup_now_button")
                        ) {
                            Icon(Icons.Outlined.CloudUpload, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Backup")
                        }
                        OutlinedButton(
                            onClick = {
                                if (!isGitHubConnected) {
                                    showGitHubTokenDialog = true
                                    return@OutlinedButton
                                }
                                if (isGistMode) {
                                    viewModel.refreshGitHubGists()
                                    showGistPickerDialog = true
                                } else {
                                    scope.launch {
                                        viewModel.fetchGitHubRepoRaw(repoSlugInput, repoBranchInput, repoPathInput)
                                            .onSuccess { raw ->
                                                restoreRequiresPassword = BackupCrypto.isEncryptedEnvelope(raw)
                                                restorePassword = ""
                                                pendingCloudRestoreText = raw
                                            }
                                            .onFailure { err ->
                                                viewModel.emitMessage(err.message ?: "Failed to fetch backup from repo")
                                            }
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f).testTag("github_restore_button")
                        ) {
                            Icon(Icons.Outlined.CloudDownload, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Restore")
                        }
                    }
                }
            }
        }
    }

    if (showGitHubTokenDialog) {
        AlertDialog(
            onDismissRequest = { showGitHubTokenDialog = false },
            title = { Text("Connect GitHub Account") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Paste a GitHub Personal Access Token (PAT) with 'gist' (for Secret Gist) or 'repo' (for private repository backup). Your token is encrypted on-device using Android Keystore.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = gitHubTokenInput,
                        onValueChange = { gitHubTokenInput = it },
                        label = { Text("GitHub Personal Access Token") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("github_pat_input")
                    )
                    if (githubBusy) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Verifying token...")
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showGitHubTokenDialog = false }) { Text("Cancel") }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val pat = gitHubTokenInput.trim()
                        if (pat.isNotEmpty()) {
                            viewModel.connectGitHubToken(pat)
                            gitHubTokenInput = ""
                            showGitHubTokenDialog = false
                        }
                    },
                    enabled = gitHubTokenInput.isNotBlank() && !githubBusy
                ) {
                    Text("Verify & Connect")
                }
            }
        )
    }

    if (showGistPickerDialog) {
        AlertDialog(
            onDismissRequest = { showGistPickerDialog = false },
            title = { Text("Select GitHub Gist Backup") },
            text = {
                if (githubBusy) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Loading Gists...")
                    }
                } else if (githubGists.isEmpty()) {
                    Text("No Priyo Nots backup Gists found on your GitHub account.")
                } else {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        githubGists.forEach { gist ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showGistPickerDialog = false
                                        scope.launch {
                                            viewModel.fetchGitHubGistRaw(gist.id)
                                                .onSuccess { raw ->
                                                    restoreRequiresPassword = BackupCrypto.isEncryptedEnvelope(raw)
                                                    restorePassword = ""
                                                    pendingCloudRestoreText = raw
                                                }
                                                .onFailure { err ->
                                                    viewModel.emitMessage(err.message ?: "Failed to read Gist")
                                                }
                                        }
                                    },
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(gist.fileName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    Text(gist.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        "${formatFileSize(gist.sizeBytes)} · ${gist.updatedAtIso.take(10)}",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showGistPickerDialog = false }) { Text("Close") }
            }
        )
    }

    if (pendingRestoreUri != null || pendingCloudRestoreText != null) {
        AlertDialog(
            onDismissRequest = {
                pendingRestoreUri = null
                pendingCloudRestoreText = null
            },
            title = { Text("Restore Options") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Choose how to restore this backup:", style = MaterialTheme.typography.bodyMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = restoreMode == RestoreMode.MERGE,
                            onClick = { restoreMode = RestoreMode.MERGE }
                        )
                        Column {
                            Text("Merge (Recommended)", fontWeight = FontWeight.SemiBold)
                            Text("Keeps existing notes and skips duplicates.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = restoreMode == RestoreMode.REPLACE,
                            onClick = { restoreMode = RestoreMode.REPLACE }
                        )
                        Column {
                            Text("Replace All", fontWeight = FontWeight.SemiBold)
                            Text("Clears current database and restores snapshot.", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    if (restoreRequiresPassword) {
                        OutlinedTextField(
                            value = restorePassword,
                            onValueChange = { restorePassword = it },
                            label = { Text("Backup Password") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingRestoreUri = null
                    pendingCloudRestoreText = null
                }) { Text("Cancel") }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val targetUri = pendingRestoreUri
                        val cloudText = pendingCloudRestoreText
                        val mode = restoreMode
                        val pw = if (restoreRequiresPassword) restorePassword else null
                        pendingRestoreUri = null
                        pendingCloudRestoreText = null
                        if (targetUri != null) {
                            viewModel.restoreFromUri(targetUri, mode, pw)
                        } else if (cloudText != null) {
                            viewModel.restoreFromRawText(cloudText, mode, pw)
                        }
                    },
                    enabled = !restoreRequiresPassword || restorePassword.isNotBlank()
                ) {
                    Text("Restore")
                }
            }
        )
    }

    if (showDrivePickerDialog) {
        AlertDialog(
            onDismissRequest = { showDrivePickerDialog = false },
            title = { Text("Select Google Drive Backup") },
            text = {
                if (driveBusy) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Loading backups from Google Drive...")
                    }
                } else if (driveFiles.isEmpty()) {
                    Text("No Priyo Nots backup files were found in your Google Drive 'Priyo Nots Backups' folder yet.")
                } else {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        driveFiles.forEach { file ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val token = driveAccessToken
                                        if (token.isNullOrBlank()) {
                                            viewModel.emitMessage("Google Drive session expired. Tap Restore again.")
                                            showDrivePickerDialog = false
                                            return@clickable
                                        }
                                        showDrivePickerDialog = false
                                        scope.launch {
                                            viewModel.fetchGoogleDriveBackupRaw(token, file.id)
                                                .onSuccess { raw ->
                                                    restoreRequiresPassword = BackupCrypto.isEncryptedEnvelope(raw)
                                                    restorePassword = ""
                                                    pendingCloudRestoreText = raw
                                                }
                                                .onFailure { err ->
                                                    viewModel.emitMessage(err.message ?: "Failed to download from Google Drive")
                                                }
                                        }
                                    },
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(file.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    if (file.description.isNotBlank()) {
                                        Text(file.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(
                                        "${formatFileSize(file.sizeBytes)} · ${file.modifiedTimeIso.take(10)}",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDrivePickerDialog = false }) { Text("Close") }
            }
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
