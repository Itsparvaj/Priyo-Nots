package com.example

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.local.entity.AppSettingsEntity
import com.example.data.update.DownloadState
import com.example.data.update.UpdateMetadata
import com.example.data.update.UpdateNotificationHelper
import com.example.ui.components.formatFileSize
import com.example.ui.navigation.PriyoRoutes
import com.example.ui.screens.AboutScreen
import com.example.ui.screens.ArchivedNotesScreen
import com.example.ui.screens.BackupHistoryScreen
import com.example.ui.screens.BackupRestoreScreen
import com.example.ui.screens.CategoriesScreen
import com.example.ui.screens.ContactUsScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.NoteDetailScreen
import com.example.ui.screens.NoteEditorScreen
import com.example.ui.screens.PinnedNotesScreen
import com.example.ui.screens.PrivacyPolicyScreen
import com.example.ui.screens.ReminderSettingsScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.TermsScreen
import com.example.ui.screens.TrashScreen
import com.example.ui.screens.UpdateScreen
import com.example.ui.theme.PriyoNotsTheme
import com.example.ui.viewmodel.PriyoNotsViewModel
import com.example.ui.viewmodel.UpdateCheckUiState

class MainActivity : ComponentActivity() {

    private var deepLinkNoteId by mutableLongStateOf(-1L)
    private var deepLinkOpenUpdates by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deepLinkNoteId = intent?.getLongExtra(EXTRA_OPEN_NOTE_ID, -1L) ?: -1L
        deepLinkOpenUpdates = intent?.getBooleanExtra(EXTRA_OPEN_UPDATES, false) == true
        enableEdgeToEdge()

        setContent {
            val viewModel: PriyoNotsViewModel = viewModel()
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val isDark = when (settings.themeMode) {
                AppSettingsEntity.THEME_DARK -> true
                AppSettingsEntity.THEME_LIGHT -> false
                else -> systemDark
            }

            LaunchedEffect(isDark) {
                enableEdgeToEdge(
                    statusBarStyle = if (isDark) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    },
                    navigationBarStyle = if (isDark) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    }
                )
            }

            PriyoNotsTheme(themeMode = settings.themeMode) {
                PriyoNotsRootNav(
                    viewModel = viewModel,
                    pendingDeepLinkNoteId = deepLinkNoteId,
                    pendingOpenUpdates = deepLinkOpenUpdates,
                    onConsumeDeepLink = { deepLinkNoteId = -1L },
                    onConsumeOpenUpdates = { deepLinkOpenUpdates = false }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val noteId = intent.getLongExtra(EXTRA_OPEN_NOTE_ID, -1L)
        if (noteId > 0L) {
            deepLinkNoteId = noteId
        }
        if (intent.getBooleanExtra(EXTRA_OPEN_UPDATES, false)) {
            deepLinkOpenUpdates = true
        }
    }

    companion object {
        const val EXTRA_OPEN_NOTE_ID = "extra_open_note_id"
        const val EXTRA_OPEN_UPDATES = "extra_open_updates"
    }
}

@Composable
private fun PriyoNotsRootNav(
    viewModel: PriyoNotsViewModel,
    pendingDeepLinkNoteId: Long,
    pendingOpenUpdates: Boolean,
    onConsumeDeepLink: () -> Unit,
    onConsumeOpenUpdates: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStack?.destination?.route

    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val downloadState by viewModel.downloadState.collectAsStateWithLifecycle()
    val showInAppUpdatePrompt by viewModel.showInAppUpdatePrompt.collectAsStateWithLifecycle()
    val autoTriggerInstall by viewModel.autoTriggerInstallOnVerified.collectAsStateWithLifecycle()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshUpdatesIfStale()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        viewModel.toastEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    LaunchedEffect(pendingDeepLinkNoteId) {
        if (pendingDeepLinkNoteId > 0L) {
            navController.navigate(PriyoRoutes.ROUTE_HOME) {
                popUpTo(PriyoRoutes.ROUTE_SPLASH) { inclusive = true }
            }
            navController.navigate(PriyoRoutes.noteDetails(pendingDeepLinkNoteId))
            onConsumeDeepLink()
        }
    }

    LaunchedEffect(pendingOpenUpdates) {
        if (pendingOpenUpdates) {
            UpdateNotificationHelper.cancelUpdateNotification(context)
            navController.navigate(PriyoRoutes.ROUTE_HOME) {
                popUpTo(PriyoRoutes.ROUTE_SPLASH) { inclusive = true }
            }
            navController.navigate(PriyoRoutes.ROUTE_UPDATE_AVAILABLE)
            onConsumeOpenUpdates()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = PriyoRoutes.ROUTE_SPLASH,
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() }
        ) {
            composable(PriyoRoutes.ROUTE_SPLASH) {
                SplashScreen(
                    onFinished = {
                        navController.navigate(PriyoRoutes.ROUTE_HOME) {
                            popUpTo(PriyoRoutes.ROUTE_SPLASH) { inclusive = true }
                        }
                    }
                )
            }

            composable(PriyoRoutes.ROUTE_HOME) {
                HomeScreen(
                    viewModel = viewModel,
                    onCreateNote = { navController.navigate(PriyoRoutes.ROUTE_CREATE_NOTE) },
                    onOpenNote = { id -> navController.navigate(PriyoRoutes.noteDetails(id)) },
                    onEditNote = { id -> navController.navigate(PriyoRoutes.editNote(id)) },
                    onOpenSearch = { navController.navigate(PriyoRoutes.search()) },
                    onOpenCategories = { navController.navigate(PriyoRoutes.ROUTE_CATEGORIES) },
                    onOpenPinned = { navController.navigate(PriyoRoutes.ROUTE_PINNED) },
                    onOpenArchived = { navController.navigate(PriyoRoutes.ROUTE_ARCHIVED) },
                    onOpenTrash = { navController.navigate(PriyoRoutes.ROUTE_TRASH) },
                    onOpenBackupRestore = { navController.navigate(PriyoRoutes.ROUTE_BACKUP_RESTORE) },
                    onOpenBackupHistory = { navController.navigate(PriyoRoutes.ROUTE_BACKUP_HISTORY) },
                    onOpenReminders = { navController.navigate(PriyoRoutes.ROUTE_REMINDER_SETTINGS) },
                    onOpenSettings = { navController.navigate(PriyoRoutes.ROUTE_SETTINGS) },
                    onOpenUpdates = { navController.navigate(PriyoRoutes.ROUTE_UPDATE_AVAILABLE) },
                    onOpenAbout = { navController.navigate(PriyoRoutes.ROUTE_ABOUT) }
                )
            }

            composable(PriyoRoutes.ROUTE_CREATE_NOTE) {
                NoteEditorScreen(
                    noteId = null,
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = PriyoRoutes.ROUTE_EDIT_NOTE,
                arguments = listOf(navArgument("noteId") { type = NavType.LongType })
            ) { entry ->
                val noteId = entry.arguments?.getLong("noteId") ?: 0L
                NoteEditorScreen(
                    noteId = noteId,
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = PriyoRoutes.ROUTE_NOTE_DETAILS,
                arguments = listOf(navArgument("noteId") { type = NavType.LongType })
            ) { entry ->
                val noteId = entry.arguments?.getLong("noteId") ?: 0L
                NoteDetailScreen(
                    noteId = noteId,
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onEditNote = { id -> navController.navigate(PriyoRoutes.editNote(id)) }
                )
            }

            composable(
                route = PriyoRoutes.ROUTE_SEARCH,
                arguments = listOf(
                    navArgument("categoryId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { entry ->
                val catId = entry.arguments?.getString("categoryId")?.toLongOrNull()
                SearchScreen(
                    initialCategoryId = catId,
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onOpenNote = { id -> navController.navigate(PriyoRoutes.noteDetails(id)) },
                    onEditNote = { id -> navController.navigate(PriyoRoutes.editNote(id)) }
                )
            }

            composable(PriyoRoutes.ROUTE_CATEGORIES) {
                CategoriesScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onOpenCategoryNotes = { catId -> navController.navigate(PriyoRoutes.search(catId)) }
                )
            }

            composable(PriyoRoutes.ROUTE_PINNED) {
                PinnedNotesScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onOpenNote = { id -> navController.navigate(PriyoRoutes.noteDetails(id)) },
                    onEditNote = { id -> navController.navigate(PriyoRoutes.editNote(id)) }
                )
            }

            composable(PriyoRoutes.ROUTE_ARCHIVED) {
                ArchivedNotesScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onOpenNote = { id -> navController.navigate(PriyoRoutes.noteDetails(id)) },
                    onEditNote = { id -> navController.navigate(PriyoRoutes.editNote(id)) }
                )
            }

            composable(PriyoRoutes.ROUTE_TRASH) {
                TrashScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(PriyoRoutes.ROUTE_BACKUP_RESTORE) {
                BackupRestoreScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onOpenHistory = { navController.navigate(PriyoRoutes.ROUTE_BACKUP_HISTORY) }
                )
            }

            composable(PriyoRoutes.ROUTE_BACKUP_HISTORY) {
                BackupHistoryScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(PriyoRoutes.ROUTE_SETTINGS) {
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onOpenReminderSettings = { navController.navigate(PriyoRoutes.ROUTE_REMINDER_SETTINGS) },
                    onOpenBackupRestore = { navController.navigate(PriyoRoutes.ROUTE_BACKUP_RESTORE) },
                    onOpenBackupHistory = { navController.navigate(PriyoRoutes.ROUTE_BACKUP_HISTORY) },
                    onOpenUpdateScreen = { navController.navigate(PriyoRoutes.ROUTE_UPDATE_AVAILABLE) },
                    onOpenAbout = { navController.navigate(PriyoRoutes.ROUTE_ABOUT) },
                    onOpenPrivacyPolicy = { navController.navigate(PriyoRoutes.ROUTE_PRIVACY_POLICY) }
                )
            }

            composable(PriyoRoutes.ROUTE_REMINDER_SETTINGS) {
                ReminderSettingsScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onOpenNote = { id -> navController.navigate(PriyoRoutes.noteDetails(id)) }
                )
            }

            composable(PriyoRoutes.ROUTE_UPDATE_AVAILABLE) {
                UpdateScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(PriyoRoutes.ROUTE_ABOUT) {
                AboutScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onOpenPrivacyPolicy = { navController.navigate(PriyoRoutes.ROUTE_PRIVACY_POLICY) },
                    onOpenTerms = { navController.navigate(PriyoRoutes.ROUTE_TERMS) },
                    onOpenContactUs = { navController.navigate(PriyoRoutes.ROUTE_CONTACT_US) },
                    onOpenUpdates = { navController.navigate(PriyoRoutes.ROUTE_UPDATE_AVAILABLE) }
                )
            }

            composable(PriyoRoutes.ROUTE_PRIVACY_POLICY) {
                PrivacyPolicyScreen(onNavigateBack = { navController.popBackStack() })
            }

            composable(PriyoRoutes.ROUTE_TERMS) {
                TermsScreen(onNavigateBack = { navController.popBackStack() })
            }

            composable(PriyoRoutes.ROUTE_CONTACT_US) {
                ContactUsScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }

        val availableState = updateState as? UpdateCheckUiState.Available
        if (showInAppUpdatePrompt &&
            availableState != null &&
            currentRoute != null &&
            currentRoute != PriyoRoutes.ROUTE_SPLASH &&
            currentRoute != PriyoRoutes.ROUTE_UPDATE_AVAILABLE
        ) {
            InAppUpdatePromptDialog(
                metadata = availableState.metadata,
                downloadState = downloadState,
                autoTriggerInstall = autoTriggerInstall,
                onConsumeAutoInstall = { viewModel.consumeAutoInstallTrigger() },
                onStartDownload = { viewModel.startUpdateDownload(availableState.metadata) },
                onResetDownload = { viewModel.resetDownloadState() },
                onOpenInstallSettings = {
                    context.startActivity(viewModel.buildInstallSettingsIntent())
                    viewModel.emitMessage("Allow unknown app installs for Priyo Nots, then tap Install Update.")
                },
                onInstallApk = { file ->
                    context.startActivity(viewModel.buildInstallApkIntent(file))
                },
                canInstallPackages = { viewModel.canInstallPackages() },
                onViewFullUpdateScreen = {
                    viewModel.dismissInAppUpdatePrompt()
                    navController.navigate(PriyoRoutes.ROUTE_UPDATE_AVAILABLE)
                },
                onDismissLater = {
                    if (availableState.metadata.forceUpdate) return@InAppUpdatePromptDialog
                    viewModel.dismissUpdateForVersion(availableState.metadata.versionCode)
                }
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
        )
    }
}

@Composable
private fun InAppUpdatePromptDialog(
    metadata: UpdateMetadata,
    downloadState: DownloadState,
    autoTriggerInstall: Boolean,
    onConsumeAutoInstall: () -> Unit,
    onStartDownload: () -> Unit,
    onResetDownload: () -> Unit,
    onOpenInstallSettings: () -> Unit,
    onInstallApk: (java.io.File) -> Unit,
    canInstallPackages: () -> Boolean,
    onViewFullUpdateScreen: () -> Unit,
    onDismissLater: () -> Unit
) {
    val context = LocalContext.current

    LaunchedEffect(downloadState, autoTriggerInstall) {
        if (downloadState is DownloadState.Verified && autoTriggerInstall) {
            onConsumeAutoInstall()
            if (canInstallPackages()) {
                onInstallApk(downloadState.apkFile)
            } else {
                onOpenInstallSettings()
            }
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (!metadata.forceUpdate && downloadState !is DownloadState.Downloading) {
                onDismissLater()
            }
        },
        icon = {
            Image(
                painter = painterResource(id = R.drawable.priyo_nots_logo),
                contentDescription = "Priyo Nots",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
            )
        },
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = metadata.updateTitle.ifBlank { "Update Available" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "v${BuildConfig.VERSION_NAME}  →  v${metadata.versionName}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
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
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "What's New",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = metadata.releaseNotes.trim(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                when (downloadState) {
                    DownloadState.Idle -> Unit
                    is DownloadState.Downloading -> {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = if (downloadState.totalBytes > 0) {
                                    "Downloading APK: ${formatFileSize(downloadState.bytesRead)} / ${formatFileSize(downloadState.totalBytes)}"
                                } else {
                                    "Downloading APK: ${formatFileSize(downloadState.bytesRead)}"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium
                            )
                            if (downloadState.totalBytes > 0) {
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
                                text = "APK verified (${downloadState.sha256.take(10)}…). Ready to install.",
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
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onDismissLater) {
                        Text("Later", maxLines = 1, softWrap = false)
                    }
                    TextButton(onClick = onViewFullUpdateScreen) {
                        Text("Details", maxLines = 1, softWrap = false)
                    }
                }
            }
        },
        confirmButton = {
            when (downloadState) {
                DownloadState.Idle -> {
                    if (metadata.hasDirectApkAsset) {
                        Button(onClick = onStartDownload) {
                            Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Update Now", maxLines = 1, softWrap = false)
                        }
                    } else if (metadata.downloadUrl.isNotBlank()) {
                        Button(onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(metadata.downloadUrl)))
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Download APK", maxLines = 1, softWrap = false)
                        }
                    }
                }
                is DownloadState.Downloading -> {
                    OutlinedButton(onClick = onViewFullUpdateScreen) {
                        Text("View Progress", maxLines = 1, softWrap = false)
                    }
                }
                is DownloadState.Verified -> {
                    Button(onClick = {
                        if (!canInstallPackages()) {
                            onOpenInstallSettings()
                        } else {
                            onInstallApk(downloadState.apkFile)
                        }
                    }) {
                        Text("Install Update", maxLines = 1, softWrap = false)
                    }
                }
                is DownloadState.Failed -> {
                    Button(onClick = {
                        onResetDownload()
                        onStartDownload()
                    }) {
                        Text("Retry", maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    )
}
