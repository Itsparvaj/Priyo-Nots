package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.PriyoEmptyState
import com.example.ui.components.PriyoRichTextWebView
import com.example.ui.components.PriyoWebEditorController
import com.example.ui.components.formatTimestamp
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.NoteColorPalette
import com.example.ui.viewmodel.PriyoNotsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteDetailScreen(
    noteId: Long,
    viewModel: PriyoNotsViewModel,
    onNavigateBack: () -> Unit,
    onEditNote: (Long) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark = LocalIsDarkTheme.current
    val noteRel by viewModel.observeNote(noteId).collectAsStateWithLifecycle(initialValue = null)
    val webController = remember { PriyoWebEditorController() }
    var showOverflow by remember { mutableStateOf(false) }

    val note = noteRel?.note
    val colorOption = NoteColorPalette.resolve(note?.colorKey ?: "default")
    val bgColor = colorOption.backgroundFor(isDark)
    val bgHex = colorOption.hexFor(isDark)

    Scaffold(
        containerColor = bgColor,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor),
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("detail_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Note Details", style = MaterialTheme.typography.titleMedium) },
                actions = {
                    if (note != null) {
                        if (!note.isTrashed && !note.isArchived) {
                            IconButton(
                                onClick = { viewModel.togglePin(note.id, note.isPinned) },
                                modifier = Modifier.testTag("detail_pin_button")
                            ) {
                                Icon(
                                    imageVector = if (note.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                    contentDescription = if (note.isPinned) "Unpin" else "Pin",
                                    tint = if (note.isPinned) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                val shareBody = buildString {
                                    if (note.title.isNotBlank()) appendLine(note.title)
                                    if (note.plainText.isNotBlank()) append(note.plainText)
                                }
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, note.title.ifBlank { "Priyo Nots Note" })
                                    putExtra(Intent.EXTRA_TEXT, shareBody)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share Note"))
                            },
                            modifier = Modifier.testTag("detail_share_button")
                        ) {
                            Icon(Icons.Outlined.Share, contentDescription = "Share note")
                        }
                        IconButton(onClick = { showOverflow = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "More actions")
                        }
                        DropdownMenu(expanded = showOverflow, onDismissRequest = { showOverflow = false }) {
                            DropdownMenuItem(
                                text = { Text("Copy text") },
                                leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                                onClick = {
                                    showOverflow = false
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val textToCopy = listOf(note.title, note.plainText).filter { it.isNotBlank() }.joinToString("\n\n")
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Priyo Nots", textToCopy))
                                    viewModel.emitMessage("Copied to clipboard")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Export JSON") },
                                leadingIcon = { Icon(Icons.Outlined.FileUpload, contentDescription = null) },
                                onClick = {
                                    showOverflow = false
                                    scope.launch {
                                        val file = viewModel.exportNoteAsJsonFile(note)
                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            file
                                        )
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/json"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Export Note JSON"))
                                    }
                                }
                            )
                            if (!note.isTrashed) {
                                DropdownMenuItem(
                                    text = { Text(if (note.isArchived) "Unarchive" else "Archive") },
                                    leadingIcon = {
                                        Icon(
                                            if (note.isArchived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        showOverflow = false
                                        viewModel.toggleArchive(note.id, note.isArchived)
                                        onNavigateBack()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Move to Trash") },
                                    leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
                                    onClick = {
                                        showOverflow = false
                                        viewModel.moveToTrash(note.id)
                                        onNavigateBack()
                                    }
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text("Restore Note") },
                                    leadingIcon = { Icon(Icons.Outlined.RestoreFromTrash, contentDescription = null) },
                                    onClick = {
                                        showOverflow = false
                                        viewModel.restoreFromTrash(note.id)
                                        onNavigateBack()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete Permanently") },
                                    leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
                                    onClick = {
                                        showOverflow = false
                                        viewModel.deletePermanently(note.id)
                                        onNavigateBack()
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (note != null && !note.isTrashed) {
                ExtendedFloatingActionButton(
                    onClick = { onEditNote(note.id) },
                    icon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                    text = { Text("Edit") },
                    modifier = Modifier.testTag("detail_edit_fab")
                )
            }
        }
    ) { innerPadding ->
        if (note == null) {
            PriyoEmptyState(
                icon = Icons.Outlined.DeleteOutline,
                title = "Note unavailable",
                subtitle = "This note may have been deleted.",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = note.title.ifBlank { "Untitled Note" },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Modified ${formatTimestamp(note.updatedAt)} · Created ${formatTimestamp(note.createdAt)} · ${note.wordCount} words",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val cat = noteRel?.category
                val rem = noteRel?.activeReminder
                if (cat != null || rem != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (cat != null) {
                            AssistChip(onClick = {}, label = { Text(cat.name) })
                        }
                        if (rem != null) {
                            AssistChip(
                                onClick = { onEditNote(note.id) },
                                label = { Text(formatTimestamp(rem.triggerAtMillis)) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.NotificationsActive,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(4.dp))

                PriyoRichTextWebView(
                    initialHtml = note.contentHtml.ifBlank { "<p>${note.plainText}</p>" },
                    isEditable = false,
                    isDarkTheme = isDark,
                    backgroundHex = bgHex,
                    controller = webController,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }
    }
}
