package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.model.NoteWithRelations
import com.example.ui.components.NoteCardItem
import com.example.ui.components.NoteQuickActionsSheet
import com.example.ui.components.PriyoEmptyState
import com.example.ui.components.formatTimestamp
import com.example.ui.viewmodel.PriyoNotsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinnedNotesScreen(
    viewModel: PriyoNotsViewModel,
    onNavigateBack: () -> Unit,
    onOpenNote: (Long) -> Unit,
    onEditNote: (Long) -> Unit
) {
    val notes by viewModel.pinnedNotes.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var longPressedNote by remember { mutableStateOf<NoteWithRelations?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("pinned_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Pinned Notes (${notes.size})") }
            )
        }
    ) { innerPadding ->
        if (notes.isEmpty()) {
            PriyoEmptyState(
                icon = Icons.Outlined.PushPin,
                title = "No pinned notes",
                subtitle = "Pin important notes from the editor or note viewer so they stay right at the top.",
                modifier = Modifier.fillMaxSize().padding(innerPadding)
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize().padding(innerPadding)
            ) {
                items(notes, key = { it.note.id }) { item ->
                    NoteCardItem(
                        item = item,
                        hidePreviews = settings.privacyModeHidePreviews,
                        onClick = { onOpenNote(item.note.id) },
                        onLongClick = { longPressedNote = item }
                    )
                }
            }
        }
    }

    longPressedNote?.let { target ->
        NoteQuickActionsSheet(
            item = target,
            onDismiss = { longPressedNote = null },
            onEdit = { onEditNote(target.note.id) },
            onTogglePin = { viewModel.togglePin(target.note.id, target.note.isPinned) },
            onToggleArchive = { viewModel.toggleArchive(target.note.id, target.note.isArchived) },
            onMoveToTrash = { viewModel.moveToTrash(target.note.id) },
            onDeletePermanently = { viewModel.deletePermanently(target.note.id) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivedNotesScreen(
    viewModel: PriyoNotsViewModel,
    onNavigateBack: () -> Unit,
    onOpenNote: (Long) -> Unit,
    onEditNote: (Long) -> Unit
) {
    val notes by viewModel.archivedNotes.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var longPressedNote by remember { mutableStateOf<NoteWithRelations?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("archived_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Archived Notes (${notes.size})") }
            )
        }
    ) { innerPadding ->
        if (notes.isEmpty()) {
            PriyoEmptyState(
                icon = Icons.Outlined.Archive,
                title = "Archive is empty",
                subtitle = "Archive finished notes to keep your workspace clean without deleting them.",
                modifier = Modifier.fillMaxSize().padding(innerPadding)
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize().padding(innerPadding)
            ) {
                items(notes, key = { it.note.id }) { item ->
                    Column {
                        NoteCardItem(
                            item = item,
                            hidePreviews = settings.privacyModeHidePreviews,
                            onClick = { onOpenNote(item.note.id) },
                            onLongClick = { longPressedNote = item }
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { viewModel.toggleArchive(item.note.id, true) }) {
                                Icon(Icons.Outlined.Unarchive, contentDescription = null)
                                Text(" Restore")
                            }
                            TextButton(onClick = { viewModel.moveToTrash(item.note.id) }) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Text(" Trash", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    longPressedNote?.let { target ->
        NoteQuickActionsSheet(
            item = target,
            onDismiss = { longPressedNote = null },
            onEdit = { onEditNote(target.note.id) },
            onTogglePin = { viewModel.togglePin(target.note.id, target.note.isPinned) },
            onToggleArchive = { viewModel.toggleArchive(target.note.id, target.note.isArchived) },
            onMoveToTrash = { viewModel.moveToTrash(target.note.id) },
            onDeletePermanently = { viewModel.deletePermanently(target.note.id) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TrashScreen(
    viewModel: PriyoNotsViewModel,
    onNavigateBack: () -> Unit
) {
    val notes by viewModel.trashedNotes.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    var showEmptyConfirm by remember { mutableStateOf(false) }
    var longPressedTrashNote by remember { mutableStateOf<NoteWithRelations?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("trash_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Trash (${notes.size})") },
                actions = {
                    if (notes.isNotEmpty()) {
                        TextButton(onClick = { showEmptyConfirm = true }) {
                            Text("Empty Trash", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (notes.isEmpty()) {
            PriyoEmptyState(
                icon = Icons.Outlined.DeleteOutline,
                title = "Trash is empty",
                subtitle = "Deleted notes appear here so you can restore them anytime.",
                modifier = Modifier.fillMaxSize().padding(innerPadding)
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize().padding(innerPadding)
            ) {
                items(notes, key = { it.note.id }) { item ->
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {},
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    longPressedTrashNote = item
                                }
                            )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = item.note.title.ifBlank { "Untitled Note" },
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (item.note.plainText.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = item.note.plainText.take(140),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Deleted ${formatTimestamp(item.note.trashedAt ?: item.note.updatedAt)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row {
                                    IconButton(onClick = { viewModel.restoreFromTrash(item.note.id) }) {
                                        Icon(Icons.Outlined.RestoreFromTrash, contentDescription = "Restore note")
                                    }
                                    IconButton(onClick = { viewModel.deletePermanently(item.note.id) }) {
                                        Icon(
                                            Icons.Outlined.DeleteForever,
                                            contentDescription = "Delete permanently",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEmptyConfirm) {
        AlertDialog(
            onDismissRequest = { showEmptyConfirm = false },
            title = { Text("Empty Trash?") },
            text = { Text("All ${notes.size} notes in Trash will be permanently deleted and cannot be recovered.") },
            dismissButton = {
                TextButton(onClick = { showEmptyConfirm = false }) { Text("Cancel") }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.emptyTrash()
                    showEmptyConfirm = false
                }) {
                    Text("Delete All", color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }

    longPressedTrashNote?.let { target ->
        AlertDialog(
            onDismissRequest = { longPressedTrashNote = null },
            title = { Text(target.note.title.ifBlank { "Untitled Note" }) },
            text = { Text("Choose an action for this trashed note.") },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.restoreFromTrash(target.note.id)
                    longPressedTrashNote = null
                }) {
                    Text("Restore Note")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePermanently(target.note.id)
                    longPressedTrashNote = null
                }) {
                    Text("Delete Permanently", color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }
}
