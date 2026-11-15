package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NoteAlt
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.entity.AppSettingsEntity
import com.example.data.local.model.NoteWithRelations
import com.example.ui.components.NoteCardItem
import com.example.ui.components.NoteQuickActionsSheet
import com.example.ui.components.PriyoEmptyState
import com.example.ui.components.formatShortDate
import com.example.ui.viewmodel.PriyoNotsViewModel
import com.example.ui.viewmodel.UpdateCheckUiState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: PriyoNotsViewModel,
    onCreateNote: () -> Unit,
    onOpenNote: (Long) -> Unit,
    onEditNote: (Long) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenPinned: () -> Unit,
    onOpenArchived: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenBackupRestore: () -> Unit,
    onOpenBackupHistory: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenUpdates: () -> Unit,
    onOpenAbout: () -> Unit
) {
    val notes by viewModel.activeNotes.collectAsStateWithLifecycle()
    val pinnedNotes by viewModel.pinnedNotes.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val selectedCategoryId by viewModel.selectedHomeCategoryId.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showSortMenu by remember { mutableStateOf(false) }
    var longPressedNote by remember { mutableStateOf<NoteWithRelations?>(null) }

    val drawerContent: @Composable () -> Unit = {
        ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.priyo_nots_logo),
                        contentDescription = "Priyo Nots Logo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Priyo Nots",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "By Mohammad Parvaj",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                NavigationDrawerItem(
                    label = { Text("All Notes") },
                    selected = true,
                    onClick = { scope.launch { drawerState.close() } },
                    icon = { Icon(Icons.Outlined.NoteAlt, contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                NavigationDrawerItem(
                    label = { Text("Pinned Notes (${pinnedNotes.size})") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onOpenPinned()
                    },
                    icon = { Icon(Icons.Outlined.PushPin, contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 12.dp).testTag("drawer_pinned")
                )
                NavigationDrawerItem(
                    label = { Text("Categories (${categories.size})") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onOpenCategories()
                    },
                    icon = { Icon(Icons.Outlined.Category, contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 12.dp).testTag("drawer_categories")
                )
                NavigationDrawerItem(
                    label = { Text("Archived Notes") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onOpenArchived()
                    },
                    icon = { Icon(Icons.Outlined.Archive, contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 12.dp).testTag("drawer_archived")
                )
                NavigationDrawerItem(
                    label = { Text("Trash") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onOpenTrash()
                    },
                    icon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 12.dp).testTag("drawer_trash")
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                NavigationDrawerItem(
                    label = { Text("Backup & Restore") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onOpenBackupRestore()
                    },
                    icon = { Icon(Icons.Outlined.Backup, contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 12.dp).testTag("drawer_backup")
                )
                NavigationDrawerItem(
                    label = { Text("Backup History") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onOpenBackupHistory()
                    },
                    icon = { Icon(Icons.Outlined.History, contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 12.dp).testTag("drawer_backup_history")
                )
                NavigationDrawerItem(
                    label = { Text("Reminders") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onOpenReminders()
                    },
                    icon = { Icon(Icons.Outlined.Notifications, contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 12.dp).testTag("drawer_reminders")
                )
                NavigationDrawerItem(
                    label = { Text("App Updates") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onOpenUpdates()
                    },
                    icon = { Icon(Icons.Outlined.SystemUpdate, contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 12.dp).testTag("drawer_updates")
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                NavigationDrawerItem(
                    label = { Text("Settings") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onOpenSettings()
                    },
                    icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 12.dp).testTag("drawer_settings")
                )
                NavigationDrawerItem(
                    label = { Text("About Priyo Nots") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onOpenAbout()
                    },
                    icon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 12.dp).testTag("drawer_about")
                )
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = drawerContent
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isExpanded = maxWidth >= 840.dp

            Row(modifier = Modifier.fillMaxSize()) {
                if (isExpanded) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ) {
                        Spacer(modifier = Modifier.height(12.dp))
                        NavigationRailItem(
                            selected = true,
                            onClick = {},
                            icon = { Icon(Icons.Outlined.NoteAlt, contentDescription = "Notes") },
                            label = { Text("Notes") }
                        )
                        NavigationRailItem(
                            selected = false,
                            onClick = onOpenPinned,
                            icon = { Icon(Icons.Outlined.PushPin, contentDescription = "Pinned") },
                            label = { Text("Pinned") }
                        )
                        NavigationRailItem(
                            selected = false,
                            onClick = onOpenCategories,
                            icon = { Icon(Icons.Outlined.Category, contentDescription = "Categories") },
                            label = { Text("Tags") }
                        )
                        NavigationRailItem(
                            selected = false,
                            onClick = onOpenBackupRestore,
                            icon = { Icon(Icons.Outlined.Backup, contentDescription = "Backup") },
                            label = { Text("Backup") }
                        )
                        NavigationRailItem(
                            selected = false,
                            onClick = onOpenSettings,
                            icon = { Icon(Icons.Outlined.Settings, contentDescription = "Settings") },
                            label = { Text("Settings") }
                        )
                    }
                }

                Scaffold(
                    contentWindowInsets = WindowInsets.safeDrawing,
                    topBar = {
                        TopAppBar(
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background
                            ),
                            navigationIcon = {
                                IconButton(
                                    onClick = { scope.launch { drawerState.open() } },
                                    modifier = Modifier.testTag("home_menu_button")
                                ) {
                                    Icon(Icons.Filled.Menu, contentDescription = "Open navigation menu")
                                }
                            },
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Image(
                                        painter = painterResource(id = R.drawable.priyo_nots_logo),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(30.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Priyo Nots",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            },
                            actions = {
                                IconButton(
                                    onClick = { viewModel.toggleGridView() },
                                    modifier = Modifier.testTag("toggle_view_button")
                                ) {
                                    Icon(
                                        imageVector = if (settings.isGridView) Icons.Outlined.ViewAgenda else Icons.Outlined.GridView,
                                        contentDescription = "Toggle grid or list view"
                                    )
                                }
                                Box {
                                    IconButton(
                                        onClick = { showSortMenu = true },
                                        modifier = Modifier.testTag("sort_notes_button")
                                    ) {
                                        Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = "Sort notes")
                                    }
                                    DropdownMenu(
                                        expanded = showSortMenu,
                                        onDismissRequest = { showSortMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Recently Modified") },
                                            onClick = {
                                                viewModel.setDefaultSortOrder(AppSettingsEntity.SORT_MODIFIED_DESC)
                                                showSortMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Newest Created") },
                                            onClick = {
                                                viewModel.setDefaultSortOrder(AppSettingsEntity.SORT_CREATED_DESC)
                                                showSortMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Title A → Z") },
                                            onClick = {
                                                viewModel.setDefaultSortOrder(AppSettingsEntity.SORT_TITLE_ASC)
                                                showSortMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Title Z → A") },
                                            onClick = {
                                                viewModel.setDefaultSortOrder(AppSettingsEntity.SORT_TITLE_DESC)
                                                showSortMenu = false
                                            }
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = onOpenSettings,
                                    modifier = Modifier.testTag("home_settings_button")
                                ) {
                                    Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                                }
                            }
                        )
                    },
                    floatingActionButton = {
                        ExtendedFloatingActionButton(
                            onClick = onCreateNote,
                            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                            text = { Text("New Note") },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.testTag("create_note_fab")
                        )
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        // Instant Search Trigger Bar
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable(onClick = onOpenSearch)
                                .testTag("home_search_bar"),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = "Search notes",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Search notes, checklists, tags...",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // Backup status & Update available banner strip
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val backupSummary = settings.lastBackupAt?.let {
                                "Backup: ${formatShortDate(it)}"
                            } ?: "Not backed up yet"
                            AssistChip(
                                onClick = onOpenBackupRestore,
                                label = {
                                    Text(
                                        text = backupSummary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Outlined.Backup,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )

                            if (updateState is UpdateCheckUiState.Available) {
                                val meta = (updateState as UpdateCheckUiState.Available).metadata
                                AssistChip(
                                    onClick = onOpenUpdates,
                                    label = { Text("Update v${meta.versionName}") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Outlined.SystemUpdate,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                )
                            }
                        }

                        // Category Shortcuts Row
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                FilterChip(
                                    selected = selectedCategoryId == null,
                                    onClick = { viewModel.selectHomeCategory(null) },
                                    label = { Text("All") }
                                )
                            }
                            items(categories, key = { it.category.id }) { catItem ->
                                FilterChip(
                                    selected = selectedCategoryId == catItem.category.id,
                                    onClick = { viewModel.selectHomeCategory(catItem.category.id) },
                                    label = { Text("${catItem.category.name} (${catItem.noteCount})") }
                                )
                            }
                            item {
                                AssistChip(
                                    onClick = onOpenCategories,
                                    label = { Text("Manage") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Outlined.Category,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                )
                            }
                        }

                        if (notes.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                PriyoEmptyState(
                                    icon = Icons.Outlined.NoteAlt,
                                    title = if (selectedCategoryId == null) "No notes yet" else "No notes in this category",
                                    subtitle = "Tap New Note to start writing with rich text, checklists, colors, and reminders.",
                                    actionButton = {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .clickable(onClick = onCreateNote)
                                        ) {
                                            Text(
                                                text = "Create your first note",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                                            )
                                        }
                                    }
                                )
                            }
                        } else {
                            val pinnedVisible = notes.filter { it.note.isPinned }
                            val recentVisible = notes.filter { !it.note.isPinned }

                            if (settings.isGridView) {
                                val columns = if (isExpanded) 3 else 2
                                LazyVerticalStaggeredGrid(
                                    columns = StaggeredGridCells.Fixed(columns),
                                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 96.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalItemSpacing = 10.dp,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    if (pinnedVisible.isNotEmpty()) {
                                        item(span = StaggeredGridItemSpan.FullLine) {
                                            SectionLabel(
                                                title = "Pinned",
                                                icon = Icons.Filled.PushPin,
                                                onSeeAll = onOpenPinned
                                            )
                                        }
                                        items(pinnedVisible, key = { "pin_${it.note.id}" }) { noteItem ->
                                            NoteCardItem(
                                                item = noteItem,
                                                hidePreviews = settings.privacyModeHidePreviews,
                                                onClick = { onOpenNote(noteItem.note.id) },
                                                onLongClick = { longPressedNote = noteItem }
                                            )
                                        }
                                    }
                                    if (recentVisible.isNotEmpty()) {
                                        item(span = StaggeredGridItemSpan.FullLine) {
                                            SectionLabel(title = "Notes", icon = null, onSeeAll = null)
                                        }
                                        items(recentVisible, key = { it.note.id }) { noteItem ->
                                            NoteCardItem(
                                                item = noteItem,
                                                hidePreviews = settings.privacyModeHidePreviews,
                                                onClick = { onOpenNote(noteItem.note.id) },
                                                onLongClick = { longPressedNote = noteItem }
                                            )
                                        }
                                    }
                                }
                            } else {
                                LazyColumn(
                                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 96.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    if (pinnedVisible.isNotEmpty()) {
                                        item {
                                            SectionLabel(
                                                title = "Pinned",
                                                icon = Icons.Filled.PushPin,
                                                onSeeAll = onOpenPinned
                                            )
                                        }
                                        items(pinnedVisible, key = { "pin_${it.note.id}" }) { noteItem ->
                                            NoteCardItem(
                                                item = noteItem,
                                                hidePreviews = settings.privacyModeHidePreviews,
                                                onClick = { onOpenNote(noteItem.note.id) },
                                                onLongClick = { longPressedNote = noteItem }
                                            )
                                        }
                                    }
                                    if (recentVisible.isNotEmpty()) {
                                        item {
                                            SectionLabel(title = "Notes", icon = null, onSeeAll = null)
                                        }
                                        items(recentVisible, key = { it.note.id }) { noteItem ->
                                            NoteCardItem(
                                                item = noteItem,
                                                hidePreviews = settings.privacyModeHidePreviews,
                                                onClick = { onOpenNote(noteItem.note.id) },
                                                onLongClick = { longPressedNote = noteItem }
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

@Composable
private fun SectionLabel(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    onSeeAll: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (onSeeAll != null) {
            Text(
                text = "View all",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onSeeAll)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}
