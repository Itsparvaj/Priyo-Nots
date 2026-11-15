package com.example.ui.screens

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatClear
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entity.RemindersEntity
import com.example.ui.components.EditorFormatState
import com.example.ui.components.PriyoRichTextWebView
import com.example.ui.components.PriyoWebEditorController
import com.example.ui.components.formatTimestamp
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.NoteColorPalette
import com.example.ui.viewmodel.PriyoNotsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    noteId: Long?,
    viewModel: PriyoNotsViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark = LocalIsDarkTheme.current
    val categories by viewModel.categories.collectAsStateWithLifecycle()

    var currentNoteId by remember { mutableLongStateOf(noteId ?: 0L) }
    var title by remember { mutableStateOf("") }
    var contentHtml by remember { mutableStateOf("") }
    var plainText by remember { mutableStateOf("") }
    var wordCount by remember { mutableIntStateOf(0) }
    var colorKey by remember { mutableStateOf("default") }
    var categoryId by remember { mutableStateOf<Long?>(null) }
    var isPinned by remember { mutableStateOf(false) }
    var activeReminderMillis by remember { mutableStateOf<Long?>(null) }
    var activeReminderRepeat by remember { mutableStateOf(RemindersEntity.REPEAT_NONE) }
    var isLoaded by remember { mutableStateOf(noteId == null || noteId <= 0L) }
    var saveStatusText by remember { mutableStateOf("Ready") }
    var showColorStrip by remember { mutableStateOf(false) }
    var showCategoryDialog by remember { mutableStateOf(false) }
    var showReminderDialog by remember { mutableStateOf(false) }
    var formatState by remember { mutableStateOf(EditorFormatState()) }

    val webController = remember { PriyoWebEditorController() }

    LaunchedEffect(noteId) {
        if (noteId != null && noteId > 0L) {
            val loaded = viewModel.getNoteById(noteId)
            if (loaded != null) {
                currentNoteId = loaded.note.id
                title = loaded.note.title
                contentHtml = loaded.note.contentHtml
                plainText = loaded.note.plainText
                wordCount = loaded.note.wordCount
                colorKey = loaded.note.colorKey
                categoryId = loaded.note.categoryId
                isPinned = loaded.note.isPinned
                activeReminderMillis = loaded.activeReminder?.triggerAtMillis
                activeReminderRepeat = loaded.activeReminder?.repeatType ?: RemindersEntity.REPEAT_NONE
                webController.setHtml(loaded.note.contentHtml)
            }
            isLoaded = true
        }
    }

    suspend fun persistNow(forceEvenIfEmpty: Boolean = false): Long {
        if (!forceEvenIfEmpty && title.isBlank() && plainText.isBlank() && currentNoteId <= 0L) {
            return 0L
        }
        saveStatusText = "Saving..."
        val savedId = viewModel.saveNote(
            existingId = currentNoteId.takeIf { it > 0L },
            title = title.trim(),
            contentHtml = contentHtml,
            plainText = plainText,
            colorKey = colorKey,
            categoryId = categoryId,
            isPinned = isPinned
        )
        currentNoteId = savedId
        saveStatusText = "Saved"
        return savedId
    }

    // Auto-save debounce whenever any field changes
    LaunchedEffect(isLoaded, title, contentHtml, colorKey, categoryId, isPinned) {
        if (!isLoaded) return@LaunchedEffect
        if (title.isBlank() && plainText.isBlank() && currentNoteId <= 0L) return@LaunchedEffect
        saveStatusText = "Editing..."
        delay(650L)
        persistNow()
    }

    val handleBack: () -> Unit = {
        scope.launch {
            webController.requestSnapshot()
            delay(60L)
            persistNow()
            onNavigateBack()
        }
    }

    BackHandler(onBack = handleBack)

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showReminderDialog = true
        } else {
            viewModel.emitMessage("Notification permission denied. Enable it in Settings to receive reminders.")
        }
    }

    val noteColor = NoteColorPalette.resolve(colorKey)
    val bgColor = noteColor.backgroundFor(isDark)
    val bgHex = noteColor.hexFor(isDark)

    Scaffold(
        containerColor = bgColor,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor),
                navigationIcon = {
                    IconButton(
                        onClick = handleBack,
                        modifier = Modifier.testTag("editor_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Save and go back")
                    }
                },
                title = {
                    Column {
                        Text(
                            text = if (currentNoteId > 0L) "Edit Note" else "New Note",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "$saveStatusText · $wordCount words",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { isPinned = !isPinned },
                        modifier = Modifier.testTag("editor_pin_button")
                    ) {
                        Icon(
                            imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = if (isPinned) "Unpin note" else "Pin note",
                            tint = if (isPinned) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { showColorStrip = !showColorStrip },
                        modifier = Modifier.testTag("editor_color_button")
                    ) {
                        Icon(Icons.Outlined.Palette, contentDescription = "Note color")
                    }
                    IconButton(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                            ) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                showReminderDialog = true
                            }
                        },
                        modifier = Modifier.testTag("editor_reminder_button")
                    ) {
                        Icon(
                            imageVector = if (activeReminderMillis != null) Icons.Filled.NotificationsActive else Icons.Outlined.Notifications,
                            contentDescription = "Set reminder",
                            tint = if (activeReminderMillis != null) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = handleBack,
                        modifier = Modifier.testTag("editor_done_button")
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = "Save note")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime).only(WindowInsetsSides.Bottom))
        ) {
            if (showColorStrip) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NoteColorPalette.options.forEach { option ->
                        val selected = option.key == colorKey
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(option.backgroundFor(isDark))
                                .border(
                                    width = if (selected) 2.5.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    shape = CircleShape
                                )
                                .clickable { colorKey = option.key },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = option.label,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // Category & Reminder chips row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val activeCat = categories.firstOrNull { it.category.id == categoryId }?.category
                AssistChip(
                    onClick = { showCategoryDialog = true },
                    label = { Text(activeCat?.name ?: "Add Category") },
                    modifier = Modifier.testTag("editor_category_chip")
                )
                if (activeReminderMillis != null) {
                    AssistChip(
                        onClick = { showReminderDialog = true },
                        label = { Text("Reminder: ${formatTimestamp(activeReminderMillis!!)}") },
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

            // Title input
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                if (title.isEmpty()) {
                    Text(
                        text = "Note title",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                    )
                }
                BasicTextField(
                    value = title,
                    onValueChange = { title = it },
                    textStyle = MaterialTheme.typography.headlineMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("editor_title_input")
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            )

            // HTML5 + CSS3 + JS WebView Rich Text Surface
            PriyoRichTextWebView(
                initialHtml = contentHtml,
                isEditable = true,
                isDarkTheme = isDark,
                backgroundHex = bgHex,
                controller = webController,
                onContentChanged = { html, plain, words ->
                    contentHtml = html
                    plainText = plain
                    wordCount = words
                },
                onFormatStateChanged = { formatState = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            // Rich Text Formatting Toolbar communicating with JS bridge
            Surface(
                tonalElevation = 3.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    FormatToolButton(Icons.AutoMirrored.Filled.Undo, "Undo", false) {
                        webController.execFormat("undo")
                    }
                    FormatToolButton(Icons.AutoMirrored.Filled.Redo, "Redo", false) {
                        webController.execFormat("redo")
                    }
                    FormatToolButton(Icons.Filled.FormatBold, "Bold", formatState.bold) {
                        webController.execFormat("bold")
                    }
                    FormatToolButton(Icons.Filled.FormatItalic, "Italic", formatState.italic) {
                        webController.execFormat("italic")
                    }
                    FormatToolButton(Icons.Filled.FormatUnderlined, "Underline", formatState.underline) {
                        webController.execFormat("underline")
                    }
                    FormatToolButton(Icons.Filled.FormatStrikethrough, "Strikethrough", formatState.strike) {
                        webController.execFormat("strikeThrough")
                    }
                    TextFormatBadge("H1", formatState.block == "h1") {
                        webController.execFormat("heading", "h1")
                    }
                    TextFormatBadge("H2", formatState.block == "h2") {
                        webController.execFormat("heading", "h2")
                    }
                    FormatToolButton(Icons.AutoMirrored.Filled.FormatListBulleted, "Bullet List", formatState.ul) {
                        webController.execFormat("insertUnorderedList")
                    }
                    FormatToolButton(Icons.Filled.FormatListNumbered, "Numbered List", formatState.ol) {
                        webController.execFormat("insertOrderedList")
                    }
                    FormatToolButton(Icons.Filled.CheckBox, "Checklist", false) {
                        webController.execFormat("checklist")
                    }
                    FormatToolButton(Icons.Filled.FormatQuote, "Quote", formatState.block == "blockquote") {
                        webController.execFormat("quote")
                    }
                    FormatToolButton(Icons.Filled.Code, "Code", false) {
                        webController.execFormat("code")
                    }
                    FormatToolButton(Icons.Filled.Highlight, "Highlight", false) {
                        webController.execFormat("highlight")
                    }
                    FormatToolButton(Icons.Filled.HorizontalRule, "Divider", false) {
                        webController.execFormat("hr")
                    }
                    FormatToolButton(Icons.Filled.FormatClear, "Clear Formatting", false) {
                        webController.execFormat("removeFormat")
                    }
                }
            }
        }
    }

    if (showCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showCategoryDialog = false },
            title = { Text("Select Category") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = categoryId == null,
                        onClick = {
                            categoryId = null
                            showCategoryDialog = false
                        },
                        label = { Text("Uncategorized") }
                    )
                    categories.forEach { cat ->
                        FilterChip(
                            selected = categoryId == cat.category.id,
                            onClick = {
                                categoryId = cat.category.id
                                showCategoryDialog = false
                            },
                            label = { Text(cat.category.name) }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCategoryDialog = false }) { Text("Close") }
            }
        )
    }

    if (showReminderDialog) {
        ReminderPickerDialog(
            initialMillis = activeReminderMillis ?: (System.currentTimeMillis() + 3600_000L),
            initialRepeat = activeReminderRepeat,
            hasExistingReminder = activeReminderMillis != null,
            onDismiss = { showReminderDialog = false },
            onSaveReminder = { triggerMillis, repeatType ->
                scope.launch {
                    val savedId = persistNow(forceEvenIfEmpty = true)
                    if (savedId > 0L) {
                        viewModel.setReminder(savedId, triggerMillis, repeatType)
                        activeReminderMillis = triggerMillis
                        activeReminderRepeat = repeatType
                    }
                    showReminderDialog = false
                }
            },
            onRemoveReminder = {
                if (currentNoteId > 0L) {
                    viewModel.cancelReminderForNote(currentNoteId)
                }
                activeReminderMillis = null
                showReminderDialog = false
            }
        )
    }
}

@Composable
private fun FormatToolButton(
    icon: ImageVector,
    description: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0f)
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TextFormatBadge(
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0f),
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ReminderPickerDialog(
    initialMillis: Long,
    initialRepeat: String,
    hasExistingReminder: Boolean,
    onDismiss: () -> Unit,
    onSaveReminder: (Long, String) -> Unit,
    onRemoveReminder: () -> Unit
) {
    val context = LocalContext.current
    var selectedMillis by remember { mutableLongStateOf(initialMillis) }
    var repeatType by remember { mutableStateOf(initialRepeat) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Note Reminder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Scheduled for: ${formatTimestamp(selectedMillis)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = {
                            val cal = Calendar.getInstance().apply { timeInMillis = selectedMillis }
                            DatePickerDialog(
                                context,
                                { _, year, month, day ->
                                    cal.set(Calendar.YEAR, year)
                                    cal.set(Calendar.MONTH, month)
                                    cal.set(Calendar.DAY_OF_MONTH, day)
                                    selectedMillis = cal.timeInMillis
                                },
                                cal.get(Calendar.YEAR),
                                cal.get(Calendar.MONTH),
                                cal.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        },
                        label = { Text("Pick Date") }
                    )
                    AssistChip(
                        onClick = {
                            val cal = Calendar.getInstance().apply { timeInMillis = selectedMillis }
                            TimePickerDialog(
                                context,
                                { _, hour, minute ->
                                    cal.set(Calendar.HOUR_OF_DAY, hour)
                                    cal.set(Calendar.MINUTE, minute)
                                    cal.set(Calendar.SECOND, 0)
                                    selectedMillis = cal.timeInMillis
                                },
                                cal.get(Calendar.HOUR_OF_DAY),
                                cal.get(Calendar.MINUTE),
                                false
                            ).show()
                        },
                        label = { Text("Pick Time") }
                    )
                }

                Text("Repeat", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        RemindersEntity.REPEAT_NONE to "Once",
                        RemindersEntity.REPEAT_DAILY to "Daily",
                        RemindersEntity.REPEAT_WEEKLY to "Weekly"
                    ).forEach { (type, label) ->
                        FilterChip(
                            selected = repeatType == type,
                            onClick = { repeatType = type },
                            label = { Text(label) }
                        )
                    }
                }
            }
        },
        dismissButton = {
            Row {
                if (hasExistingReminder) {
                    TextButton(onClick = onRemoveReminder) {
                        Text("Remove", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSaveReminder(selectedMillis, repeatType) }) {
                Text("Set Reminder")
            }
        }
    )
}
