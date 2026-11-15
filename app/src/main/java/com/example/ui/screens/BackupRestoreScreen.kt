package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.backup.BackupCrypto
import com.example.data.backup.RestoreMode
import com.example.data.drive.GoogleDriveAuthError
import com.example.data.drive.GoogleDriveAuthHelper
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
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val opState by viewModel.backupState.collectAsStateWithLifecycle()
    val driveFiles by viewModel.driveFiles.collectAsStateWithLifecycle()
    val driveBusy by viewModel.driveBusy.collectAsStateWithLifecycle()

    var encryptEnabled by remember { mutableStateOf(false) }
    var backupPassword by remember { mutableStateOf("") }

    // Pending restore state (local/SAF Uri or cloud raw text)
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCloudRestoreText by remember { mutableStateOf<String?>(null) }
    var restoreRequiresPassword by remember { mutableStateOf(false) }
    var restorePassword by remember { mutableStateOf("") }
    var restoreMode by remember { mutableStateOf(RestoreMode.MERGE) }

    // Google Drive state
    var driveAccessToken by remember { mutableStateOf<String?>(null) }
    var pendingDriveAction by remember { mutableStateOf<String?>(null) }
    var showDrivePickerDialog by remember { mutableStateOf(false) }
    var driveSetupDialogError by remember { mutableStateOf<GoogleDriveAuthError?>(null) }
    val isDriveConnected = settings.googleDriveEmail.isNotBlank()

    val driveAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        val action = pendingDriveAction
        pendingDriveAction = null
        GoogleDriveAuthHelper.extractTokenFromConsentResult(
            context = context,
            resultCode = activityResult.resultCode,
            data = activityResult.data
        )
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
                val authErr = (err as? GoogleDriveAuthHelper.GoogleAuthFailureException)?.authError
                if (authErr != null && authErr.isConsoleRegistrationIssue) {
                    driveSetupDialogError = authErr
                } else {
                    viewModel.emitMessage(err.message ?: "Google Drive authorization failed")
                }
            }
    }

    fun startGoogleDriveAction(action: String) {
        if (action == "BACKUP" && encryptEnabled && backupPassword.isBlank()) {
            viewModel.emitMessage("Enter a password for encrypted backup")
            return
        }
        val cached = driveAccessToken
        if (!cached.isNullOrBlank() && action != "CONNECT") {
            when (action) {
                "BACKUP" -> viewModel.backupToGoogleDrive(
                    accessToken = cached,
                    knownEmail = settings.googleDriveEmail.takeIf { it.isNotBlank() },
                    password = if (encryptEnabled) backupPassword else null
                )
                "RESTORE" -> {
                    viewModel.refreshGoogleDriveBackups(cached)
                    showDrivePickerDialog = true
                }
            }
            return
        }

        val activity = context.findActivity()
        if (activity == null) {
            viewModel.emitMessage("Unable to open Google account picker from this screen.")
            return
        }
        pendingDriveAction = action
        GoogleDriveAuthHelper.connectAndAuthorize(
            activity = activity,
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
            onError = { authErr ->
                pendingDriveAction = null
                if (authErr.isConsoleRegistrationIssue) {
                    driveSetupDialogError = authErr
                } else {
                    viewModel.emitMessage(authErr.message)
                }
            }
        )
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

    fun launchDriveOrDeviceCreatePicker() {
        if (encryptEnabled && backupPassword.isBlank()) {
            viewModel.emitMessage("Enter a password for encrypted backup")
            return
        }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val ext = if (encryptEnabled) "pnbak" else "json"
        createBackupDocLauncher.launch("PriyoNots_Backup_$stamp.$ext")
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Operation progress / result banner
                when (val st = opState) {
                    is BackupOperationState.InProgress -> {
                        Card(
                            shape = MaterialTheme.shapes.medium,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(st.message, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.height(10.dp))
                                LinearProgressIndicator(
                                    progress = { st.progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                )
                            }
                        }
                    }

                    is BackupOperationState.BackupDone -> {
                        Card(
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Backup Complete", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                }
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
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Restore Successful", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                }
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
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Operation Failed", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(st.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                                TextButton(onClick = { viewModel.resetBackupState() }) { Text("Dismiss") }
                            }
                        }
                    }

                    BackupOperationState.Idle -> Unit
                }

                // 1. Google Drive Cloud Backup Card (Featured for all users)
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(
                        1.dp,
                        if (isDriveConnected) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f)
                        else MaterialTheme.colorScheme.outlineVariant
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SectionIconBadge(
                                icon = if (isDriveConnected) Icons.Outlined.CloudDone else Icons.Outlined.CloudUpload,
                                containerColor = if (isDriveConnected) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
                                contentColor = if (isDriveConnected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Google Drive Backup",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isDriveConnected) {
                                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    }
                                ) {
                                    Text(
                                        text = if (isDriveConnected) "Connected · ${settings.googleDriveEmail}" else "Cloud Sync · Not connected",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isDriveConnected) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            if (isDriveConnected) {
                                TextButton(
                                    onClick = {
                                        driveAccessToken = null
                                        viewModel.disconnectGoogleDrive()
                                    }
                                ) {
                                    Text(
                                        text = "Disconnect",
                                        color = MaterialTheme.colorScheme.error,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }

                        Text(
                            text = "Save and restore your notes securely in your Google Drive 'Priyo Nots Backups' folder across any Android phone.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        settings.googleDriveLastBackupAt?.let { syncedAt ->
                            Text(
                                text = "Last Drive sync: ${formatTimestamp(syncedAt)} · ${settings.googleDriveLastStatus}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (driveBusy) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Communicating with Google Drive...", style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        if (!isDriveConnected) {
                            FilledTonalButton(
                                onClick = { startGoogleDriveAction("CONNECT") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("connect_google_drive_button")
                            ) {
                                Icon(Icons.Outlined.CloudUpload, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Connect Google Drive", maxLines = 1, softWrap = false)
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { startGoogleDriveAction("BACKUP") },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("drive_backup_now_button")
                            ) {
                                Icon(Icons.Outlined.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Back Up", maxLines = 1, softWrap = false)
                            }
                            OutlinedButton(
                                onClick = { startGoogleDriveAction("RESTORE") },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("drive_restore_button")
                            ) {
                                Icon(Icons.Outlined.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Restore", maxLines = 1, softWrap = false)
                            }
                        }

                        OutlinedButton(
                            onClick = { launchDriveOrDeviceCreatePicker() },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.DriveFileMove, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save via Drive File Picker", maxLines = 1, softWrap = false)
                        }
                    }
                }

                // 2. Encryption Security Option Card (applies to Drive & Device backups)
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Column {
                                    Text(
                                        text = "Password Encryption (AES-256)",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Lock backups with a password before saving or uploading",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(checked = encryptEnabled, onCheckedChange = { encryptEnabled = it })
                        }

                        if (encryptEnabled) {
                            OutlinedTextField(
                                value = backupPassword,
                                onValueChange = { backupPassword = it },
                                label = { Text("Backup encryption password") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // 3. Manual Device Backup Card
                Card(
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SectionIconBadge(
                                icon = Icons.Outlined.Backup,
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Device Backup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "Export all notes, tags, reminders, and settings",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { launchDriveOrDeviceCreatePicker() },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("save_backup_to_device_button")
                            ) {
                                Icon(Icons.Outlined.SaveAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Export File", maxLines = 1, softWrap = false)
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
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("quick_local_backup_button")
                            ) {
                                Text("Quick Local", maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }

                // 4. Automatic Backup Card
                Card(
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                SectionIconBadge(
                                    icon = Icons.Outlined.Schedule,
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.secondary
                                )
                                Column {
                                    Text("Automatic Backup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = "Last: ${settings.lastBackupAt?.let { formatTimestamp(it) } ?: "Never"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
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
                                    label = { Text(label, maxLines = 1, softWrap = false) }
                                )
                            }
                        }

                        Text(
                            text = "Folder: ${settings.backupFolderLabel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = { pickFolderLauncher.launch(null) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Select Folder", maxLines = 1, softWrap = false)
                            }
                            OutlinedButton(
                                onClick = { viewModel.runAutoBackupNow() },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Run Now", maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }

                // 5. Restore from File & History Card
                Card(
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SectionIconBadge(
                                icon = Icons.Outlined.Restore,
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Restore from File", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "Import any .json or encrypted .pnbak backup from Device or Google Drive",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Button(
                            onClick = { pickRestoreFileLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("import_restore_button")
                        ) {
                            Text("Import & Restore Backup File", maxLines = 1, softWrap = false)
                        }
                        OutlinedButton(
                            onClick = onOpenHistory,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.History, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("View Backup History", maxLines = 1, softWrap = false)
                        }
                    }
                }

                // Storage Tip Footer
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Tip: Back up to Google Drive or export a file to an external folder before uninstalling Priyo Nots or switching phones.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }

    // Restore Mode + Optional Password Dialog
    if (pendingRestoreUri != null || pendingCloudRestoreText != null) {
        AlertDialog(
            onDismissRequest = {
                pendingRestoreUri = null
                pendingCloudRestoreText = null
            },
            title = { Text("Restore Backup") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Choose how to restore this backup:", style = MaterialTheme.typography.bodyMedium)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { restoreMode = RestoreMode.MERGE }
                    ) {
                        RadioButton(
                            selected = restoreMode == RestoreMode.MERGE,
                            onClick = { restoreMode = RestoreMode.MERGE }
                        )
                        Column {
                            Text("Merge (Recommended)", fontWeight = FontWeight.SemiBold)
                            Text("Keeps existing notes and skips duplicates.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { restoreMode = RestoreMode.REPLACE }
                    ) {
                        RadioButton(
                            selected = restoreMode == RestoreMode.REPLACE,
                            onClick = { restoreMode = RestoreMode.REPLACE }
                        )
                        Column {
                            Text("Replace All", fontWeight = FontWeight.SemiBold)
                            Text("Clears current notes and restores snapshot.", style = MaterialTheme.typography.bodySmall)
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
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                        Spacer(modifier = Modifier.width(12.dp))
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

    driveSetupDialogError?.let { setupErr ->
        AlertDialog(
            onDismissRequest = { driveSetupDialogError = null },
            title = { Text("Save to Google Drive") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Direct in-app Google Drive sync needs an Android OAuth Client ID registered in Google Cloud Console with Drive API enabled. Until then, you can still save directly to Google Drive via Android's Drive Picker below.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (!setupErr.appSha1.isNullOrBlank()) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Package: ${setupErr.packageName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "APK SHA-1: ${setupErr.appSha1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(setupErr.appSha1))
                                viewModel.emitMessage("APK SHA-1 copied to clipboard")
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy APK SHA-1", maxLines = 1, softWrap = false)
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { driveSetupDialogError = null }) { Text("Close") }
            },
            confirmButton = {
                Button(
                    onClick = {
                        driveSetupDialogError = null
                        launchDriveOrDeviceCreatePicker()
                    }
                ) {
                    Text("Open Drive Picker", maxLines = 1, softWrap = false)
                }
            }
        )
    }
}

@Composable
private fun SectionIconBadge(
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        modifier = Modifier.size(42.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
