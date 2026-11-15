package com.example.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.PriyoNotsApp
import com.example.data.backup.AutoBackupScheduler
import com.example.data.backup.BackupResult
import com.example.data.backup.RestoreMode
import com.example.data.backup.RestoreSummary
import com.example.data.github.GitHubGistSummary
import com.example.data.local.dao.ReminderWithNoteTitle
import com.example.data.local.entity.AppSettingsEntity
import com.example.data.local.entity.BackupHistoryEntity
import com.example.data.local.entity.CategoriesEntity
import com.example.data.local.entity.NotesEntity
import com.example.data.local.model.CategoryWithCount
import com.example.data.local.model.NoteWithRelations
import com.example.data.update.DownloadState
import com.example.data.update.UpdateMetadata
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

sealed interface BackupOperationState {
    data object Idle : BackupOperationState
    data class InProgress(val progress: Float, val message: String) : BackupOperationState
    data class BackupDone(val result: BackupResult) : BackupOperationState
    data class RestoreDone(val summary: RestoreSummary) : BackupOperationState
    data class Error(val message: String) : BackupOperationState
}

sealed interface UpdateCheckUiState {
    data object Idle : UpdateCheckUiState
    data object Checking : UpdateCheckUiState
    data class Available(val metadata: UpdateMetadata) : UpdateCheckUiState
    data class UpToDate(val checkedAt: Long) : UpdateCheckUiState
    data class Error(val message: String) : UpdateCheckUiState
}

class PriyoNotsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as PriyoNotsApp).repository

    private val _toastEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toastEvents = _toastEvents.asSharedFlow()

    val settings: StateFlow<AppSettingsEntity> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettingsEntity())

    val categories: StateFlow<List<CategoryWithCount>> = repository.categoriesWithCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedHomeCategoryId = MutableStateFlow<Long?>(null)
    val selectedHomeCategoryId: StateFlow<Long?> = _selectedHomeCategoryId.asStateFlow()

    val activeNotes: StateFlow<List<NoteWithRelations>> = combine(
        repository.activeNotes,
        settings,
        _selectedHomeCategoryId
    ) { notes, currentSettings, catId ->
        val filtered = if (catId == null) notes else notes.filter { it.note.categoryId == catId }
        sortNotes(filtered, currentSettings.defaultSortOrder)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pinnedNotes: StateFlow<List<NoteWithRelations>> = repository.pinnedNotes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val archivedNotes: StateFlow<List<NoteWithRelations>> = repository.archivedNotes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trashedNotes: StateFlow<List<NoteWithRelations>> = repository.trashedNotes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeReminders: StateFlow<List<ReminderWithNoteTitle>> = repository.activeReminders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val backupHistory: StateFlow<List<BackupHistoryEntity>> = repository.backupHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Search state
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchCategoryId = MutableStateFlow<Long?>(null)
    val searchCategoryId: StateFlow<Long?> = _searchCategoryId.asStateFlow()

    private val _includeArchivedInSearch = MutableStateFlow(false)
    val includeArchivedInSearch: StateFlow<Boolean> = _includeArchivedInSearch.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val searchResults: StateFlow<List<NoteWithRelations>> = combine(
        _searchQuery,
        _searchCategoryId,
        _includeArchivedInSearch,
        settings
    ) { q, catId, incArch, s ->
        Triple(q, catId, Pair(incArch, s.defaultSortOrder))
    }.flatMapLatest { (q, catId, pair) ->
        val (incArch, sortOrder) = pair
        repository.searchNotes(q, catId, incArch).combine(MutableStateFlow(sortOrder)) { list, order ->
            sortNotes(list, order)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Backup & Restore State
    private val _backupState = MutableStateFlow<BackupOperationState>(BackupOperationState.Idle)
    val backupState: StateFlow<BackupOperationState> = _backupState.asStateFlow()

    // Update State
    private val _updateState = MutableStateFlow<UpdateCheckUiState>(UpdateCheckUiState.Idle)
    val updateState: StateFlow<UpdateCheckUiState> = _updateState.asStateFlow()

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private val _githubGists = MutableStateFlow<List<GitHubGistSummary>>(emptyList())
    val githubGists: StateFlow<List<GitHubGistSummary>> = _githubGists.asStateFlow()

    private val _githubBusy = MutableStateFlow(false)
    val githubBusy: StateFlow<Boolean> = _githubBusy.asStateFlow()

    init {
        // Automatic update check on launch if configured and enabled
        viewModelScope.launch {
            val s = repository.getSettings()
            val hasSource = when (s.updateSourceType) {
                AppSettingsEntity.UPDATE_SOURCE_GITHUB -> s.githubReleaseRepo.isNotBlank()
                else -> s.updateEndpointUrl.isNotBlank()
            }
            if (s.updateCheckEnabled && hasSource) {
                checkForUpdates(isAutomaticStartup = true)
            }
        }
    }

    fun emitMessage(msg: String) {
        _toastEvents.tryEmit(msg)
    }

    fun selectHomeCategory(categoryId: Long?) {
        _selectedHomeCategoryId.value = if (_selectedHomeCategoryId.value == categoryId) null else categoryId
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateSearchCategory(categoryId: Long?) {
        _searchCategoryId.value = categoryId
    }

    fun setIncludeArchivedInSearch(include: Boolean) {
        _includeArchivedInSearch.value = include
    }

    fun observeNote(noteId: Long): Flow<NoteWithRelations?> = repository.observeNoteById(noteId)

    suspend fun getNoteById(noteId: Long): NoteWithRelations? = repository.getNoteById(noteId)

    suspend fun saveNote(
        existingId: Long?,
        title: String,
        contentHtml: String,
        plainText: String,
        colorKey: String,
        categoryId: Long?,
        isPinned: Boolean
    ): Long {
        return repository.saveNote(
            existingId = existingId,
            title = title,
            contentHtml = contentHtml,
            plainText = plainText,
            colorKey = colorKey,
            categoryId = categoryId,
            isPinned = isPinned
        )
    }

    fun togglePin(noteId: Long, currentlyPinned: Boolean) {
        viewModelScope.launch {
            repository.setPinned(noteId, !currentlyPinned)
            emitMessage(if (!currentlyPinned) "Note pinned" else "Note unpinned")
        }
    }

    fun toggleArchive(noteId: Long, currentlyArchived: Boolean) {
        viewModelScope.launch {
            repository.setArchived(noteId, !currentlyArchived)
            emitMessage(if (!currentlyArchived) "Note archived" else "Note restored from archive")
        }
    }

    fun moveToTrash(noteId: Long) {
        viewModelScope.launch {
            repository.moveToTrash(noteId)
            emitMessage("Note moved to Trash")
        }
    }

    fun restoreFromTrash(noteId: Long) {
        viewModelScope.launch {
            repository.restoreFromTrash(noteId)
            emitMessage("Note restored")
        }
    }

    fun deletePermanently(noteId: Long) {
        viewModelScope.launch {
            repository.deletePermanently(noteId)
            emitMessage("Note permanently deleted")
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            repository.emptyTrash()
            emitMessage("Trash emptied")
        }
    }

    fun createCategory(name: String, colorHex: String, iconName: String) {
        viewModelScope.launch {
            val result = repository.createCategory(name, colorHex, iconName)
            result.onSuccess { emitMessage("Category \"$name\" created") }
                .onFailure { emitMessage(it.message ?: "Failed to create category") }
        }
    }

    fun updateCategory(category: CategoriesEntity) {
        viewModelScope.launch {
            val result = repository.updateCategory(category)
            result.onSuccess { emitMessage("Category updated") }
                .onFailure { emitMessage(it.message ?: "Failed to update category") }
        }
    }

    fun deleteCategory(categoryId: Long) {
        viewModelScope.launch {
            repository.deleteCategory(categoryId)
            if (_selectedHomeCategoryId.value == categoryId) {
                _selectedHomeCategoryId.value = null
            }
            emitMessage("Category deleted")
        }
    }

    fun setReminder(noteId: Long, triggerAtMillis: Long, repeatType: String) {
        viewModelScope.launch {
            repository.setNoteReminder(noteId, triggerAtMillis, repeatType)
            emitMessage("Reminder scheduled")
        }
    }

    fun cancelReminder(reminderId: Long) {
        viewModelScope.launch {
            repository.cancelReminder(reminderId)
            emitMessage("Reminder cancelled")
        }
    }

    fun cancelReminderForNote(noteId: Long) {
        viewModelScope.launch {
            repository.cancelReminderForNote(noteId)
            emitMessage("Reminder removed")
        }
    }

    fun toggleGridView() {
        viewModelScope.launch {
            repository.updateSettings { it.copy(isGridView = !it.isGridView) }
        }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(themeMode = mode) }
        }
    }

    fun setDefaultSortOrder(sortOrder: String) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(defaultSortOrder = sortOrder) }
        }
    }

    fun updateSettings(transform: (AppSettingsEntity) -> AppSettingsEntity) {
        viewModelScope.launch {
            val updated = repository.updateSettings(transform)
            val app = getApplication<Application>()
            if (updated.autoBackupEnabled) {
                AutoBackupScheduler.schedulePeriodicBackup(app, updated.backupFrequencyHours)
            } else {
                AutoBackupScheduler.cancelPeriodicBackup(app)
            }
        }
    }

    fun setBackupFolder(uri: Uri, label: String) {
        viewModelScope.launch {
            repository.updateSettings {
                it.copy(
                    backupFolderUri = uri.toString(),
                    backupFolderLabel = label
                )
            }
            emitMessage("Backup folder set to $label")
        }
    }

    fun resetBackupState() {
        _backupState.value = BackupOperationState.Idle
    }

    fun runManualBackupToAppStorage(password: String?) {
        viewModelScope.launch {
            _backupState.value = BackupOperationState.InProgress(0.4f, "Creating backup...")
            runCatching {
                repository.backupToAppStorage(password)
            }.onSuccess { result ->
                _backupState.value = BackupOperationState.BackupDone(result)
            }.onFailure { err ->
                _backupState.value = BackupOperationState.Error(err.message ?: "Backup failed")
            }
        }
    }

    fun runManualBackupToUri(uri: Uri, displayName: String, password: String?) {
        viewModelScope.launch {
            _backupState.value = BackupOperationState.InProgress(0.4f, "Writing backup file...")
            runCatching {
                repository.backupToUri(uri, displayName, password)
            }.onSuccess { result ->
                _backupState.value = BackupOperationState.BackupDone(result)
            }.onFailure { err ->
                _backupState.value = BackupOperationState.Error(err.message ?: "Backup failed")
            }
        }
    }

    fun runAutoBackupNow() {
        viewModelScope.launch {
            _backupState.value = BackupOperationState.InProgress(0.5f, "Running automatic backup...")
            runCatching {
                repository.runAutoBackupNow()
            }.onSuccess { result ->
                _backupState.value = BackupOperationState.BackupDone(result)
            }.onFailure { err ->
                _backupState.value = BackupOperationState.Error(err.message ?: "Automatic backup failed")
            }
        }
    }

    suspend fun inspectRequiresPassword(uri: Uri): Boolean =
        runCatching { repository.inspectRequiresPassword(uri) }.getOrDefault(false)

    fun restoreFromUri(uri: Uri, mode: RestoreMode, password: String?) {
        viewModelScope.launch {
            _backupState.value = BackupOperationState.InProgress(0.1f, "Starting restore...")
            runCatching {
                repository.restoreFromUri(uri, mode, password) { progress, msg ->
                    _backupState.value = BackupOperationState.InProgress(progress, msg)
                }
            }.onSuccess { summary ->
                _backupState.value = BackupOperationState.RestoreDone(summary)
            }.onFailure { err ->
                _backupState.value = BackupOperationState.Error(err.message ?: "Restore failed")
            }
        }
    }

    fun deleteBackupHistoryRecord(id: Long) {
        viewModelScope.launch {
            repository.deleteBackupHistoryRecord(id)
        }
    }

    suspend fun exportNoteAsJsonFile(note: NotesEntity): File =
        repository.exportNoteAsJsonFile(note)

    fun hasGitHubToken(): Boolean = repository.hasGitHubToken()

    fun connectGitHubToken(token: String) {
        viewModelScope.launch {
            _githubBusy.value = true
            repository.connectGitHubToken(token)
                .onSuccess { user ->
                    emitMessage("Connected to GitHub as @${user.login}")
                }
                .onFailure { err ->
                    emitMessage(err.message ?: "GitHub connection failed")
                }
            _githubBusy.value = false
        }
    }

    fun disconnectGitHub() {
        viewModelScope.launch {
            repository.disconnectGitHub()
            _githubGists.value = emptyList()
            emitMessage("GitHub disconnected")
        }
    }

    fun backupToGitHubGist(password: String?) {
        viewModelScope.launch {
            _backupState.value = BackupOperationState.InProgress(0.4f, "Uploading backup to GitHub Secret Gist...")
            repository.backupToGitHubGist(password)
                .onSuccess { cloud ->
                    _backupState.value = BackupOperationState.Idle
                    emitMessage("GitHub Secret Gist synced (${cloud.fileName})")
                }
                .onFailure { err ->
                    _backupState.value = BackupOperationState.Error(err.message ?: "GitHub Gist backup failed")
                }
        }
    }

    fun backupToGitHubRepo(repoSlug: String, branch: String, filePath: String, password: String?) {
        viewModelScope.launch {
            _backupState.value = BackupOperationState.InProgress(0.4f, "Pushing backup to GitHub repository...")
            repository.backupToGitHubRepo(repoSlug, branch, filePath, password)
                .onSuccess { cloud ->
                    _backupState.value = BackupOperationState.Idle
                    emitMessage("Pushed ${cloud.fileName} to GitHub repo")
                }
                .onFailure { err ->
                    _backupState.value = BackupOperationState.Error(err.message ?: "GitHub repo backup failed")
                }
        }
    }

    fun refreshGitHubGists() {
        viewModelScope.launch {
            _githubBusy.value = true
            repository.listGitHubGists()
                .onSuccess { list -> _githubGists.value = list }
                .onFailure { err -> emitMessage(err.message ?: "Failed to list GitHub Gists") }
            _githubBusy.value = false
        }
    }

    suspend fun fetchGitHubGistRaw(gistId: String): Result<String> =
        repository.fetchGitHubGistRaw(gistId)

    suspend fun fetchGitHubRepoRaw(repoSlug: String, branch: String, filePath: String): Result<String> =
        repository.fetchGitHubRepoRaw(repoSlug, branch, filePath)

    fun restoreFromRawText(rawText: String, mode: RestoreMode, password: String?) {
        viewModelScope.launch {
            _backupState.value = BackupOperationState.InProgress(0.15f, "Validating GitHub backup...")
            runCatching {
                repository.restoreFromRawBackupText(rawText, mode, password) { progress, msg ->
                    _backupState.value = BackupOperationState.InProgress(progress, msg)
                }
            }.onSuccess { summary ->
                _backupState.value = BackupOperationState.RestoreDone(summary)
            }.onFailure { err ->
                _backupState.value = BackupOperationState.Error(err.message ?: "GitHub restore failed")
            }
        }
    }

    fun checkForUpdates(isAutomaticStartup: Boolean = false) {
        viewModelScope.launch {
            val currentSettings = repository.getSettings()
            val useGitHub = currentSettings.updateSourceType == AppSettingsEntity.UPDATE_SOURCE_GITHUB
            if (useGitHub && currentSettings.githubReleaseRepo.isBlank()) {
                if (!isAutomaticStartup) {
                    _updateState.value = UpdateCheckUiState.Error(
                        "Enter a GitHub repository (owner/repo) to check GitHub Releases."
                    )
                }
                return@launch
            }
            if (!useGitHub && currentSettings.updateEndpointUrl.isBlank()) {
                if (!isAutomaticStartup) {
                    _updateState.value = UpdateCheckUiState.Error(
                        "No HTTPS update endpoint URL configured. Enter one in Settings or Update screen."
                    )
                }
                return@launch
            }
            if (!isAutomaticStartup) {
                _updateState.value = UpdateCheckUiState.Checking
            }
            val result = if (useGitHub) {
                repository.checkForGitHubReleaseUpdate(currentSettings.githubReleaseRepo)
            } else {
                repository.checkForUpdate(currentSettings.updateEndpointUrl)
            }
            result.onSuccess { meta ->
                val now = System.currentTimeMillis()
                repository.updateSettings { it.copy(lastUpdateCheckAt = now) }
                if (meta != null && meta.isNewerThanInstalled) {
                    if (isAutomaticStartup && !meta.forceUpdate && meta.versionCode == currentSettings.dismissedUpdateVersionCode) {
                        return@onSuccess
                    }
                    _updateState.value = UpdateCheckUiState.Available(meta)
                } else if (!isAutomaticStartup) {
                    _updateState.value = UpdateCheckUiState.UpToDate(now)
                }
            }.onFailure { err ->
                if (!isAutomaticStartup) {
                    _updateState.value = UpdateCheckUiState.Error(err.message ?: "Update check failed")
                }
            }
        }
    }

    fun dismissUpdateForVersion(versionCode: Int) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(dismissedUpdateVersionCode = versionCode) }
            _updateState.value = UpdateCheckUiState.Idle
        }
    }

    fun startUpdateDownload(metadata: UpdateMetadata) {
        viewModelScope.launch {
            repository.downloadUpdateApk(metadata).collect { state ->
                _downloadState.value = state
            }
        }
    }

    fun resetDownloadState() {
        _downloadState.value = DownloadState.Idle
    }

    fun canInstallPackages(): Boolean = repository.canInstallPackages()
    fun buildInstallSettingsIntent(): Intent = repository.buildInstallSettingsIntent()
    fun buildInstallApkIntent(file: File): Intent = repository.buildInstallApkIntent(file)

    private fun sortNotes(list: List<NoteWithRelations>, sortOrder: String): List<NoteWithRelations> {
        val comparator = when (sortOrder) {
            AppSettingsEntity.SORT_CREATED_DESC ->
                compareByDescending<NoteWithRelations> { it.note.isPinned }
                    .thenByDescending { it.note.createdAt }
            AppSettingsEntity.SORT_TITLE_ASC ->
                compareByDescending<NoteWithRelations> { it.note.isPinned }
                    .thenBy { it.note.title.lowercase() }
                    .thenByDescending { it.note.updatedAt }
            AppSettingsEntity.SORT_TITLE_DESC ->
                compareByDescending<NoteWithRelations> { it.note.isPinned }
                    .thenByDescending { it.note.title.lowercase() }
                    .thenByDescending { it.note.updatedAt }
            else ->
                compareByDescending<NoteWithRelations> { it.note.isPinned }
                    .thenByDescending { it.note.updatedAt }
        }
        return list.sortedWith(comparator)
    }
}
