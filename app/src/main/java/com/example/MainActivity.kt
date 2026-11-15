package com.example

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.local.entity.AppSettingsEntity
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

class MainActivity : ComponentActivity() {

    private var deepLinkNoteId by mutableLongStateOf(-1L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deepLinkNoteId = intent?.getLongExtra(EXTRA_OPEN_NOTE_ID, -1L) ?: -1L
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
                    onConsumeDeepLink = { deepLinkNoteId = -1L }
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
    }

    companion object {
        const val EXTRA_OPEN_NOTE_ID = "extra_open_note_id"
    }
}

@Composable
private fun PriyoNotsRootNav(
    viewModel: PriyoNotsViewModel,
    pendingDeepLinkNoteId: Long,
    onConsumeDeepLink: () -> Unit
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

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
                    onOpenNote = { id -> navController.navigate(PriyoRoutes.noteDetails(id)) }
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
                    onOpenNote = { id -> navController.navigate(PriyoRoutes.noteDetails(id)) }
                )
            }

            composable(PriyoRoutes.ROUTE_ARCHIVED) {
                ArchivedNotesScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onOpenNote = { id -> navController.navigate(PriyoRoutes.noteDetails(id)) }
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

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
        )
    }
}
