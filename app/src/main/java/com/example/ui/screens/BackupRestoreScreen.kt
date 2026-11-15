package com.example.ui.screens

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
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.example.data.backup.RestoreMode
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

    var encryptEnabled by remember { mutableStateOf(false) }
    var backupPassword by remember { mutableStateOf("") }

    // Pending restore state
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var restoreRequiresPassword by remember { mutableStateOf(false) }
    var restorePassword by remember { mutableStateOf("") }
    var restoreMode by remember { mutableStateOf(RestoreMode.MERGE) }

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
        }
    }

    pendingRestoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
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
                TextButton(onClick = { pendingRestoreUri = null }) { Text("Cancel") }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = uri
                        val mode = restoreMode
                        val pw = if (restoreRequiresPassword) restorePassword else null
                        pendingRestoreUri = null
                        viewModel.restoreFromUri(target, mode, pw)
                    },
                    enabled = !restoreRequiresPassword || restorePassword.isNotBlank()
                ) {
                    Text("Restore")
                }
            }
        )
    }
}
