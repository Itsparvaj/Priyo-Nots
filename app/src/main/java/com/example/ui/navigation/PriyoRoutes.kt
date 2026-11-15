package com.example.ui.navigation

object PriyoRoutes {
    const val ROUTE_SPLASH = "splash"
    const val ROUTE_HOME = "home"
    const val ROUTE_CREATE_NOTE = "create_note"
    const val ROUTE_EDIT_NOTE = "edit_note/{noteId}"
    const val ROUTE_NOTE_DETAILS = "note_details/{noteId}"
    const val ROUTE_SEARCH = "search?categoryId={categoryId}"
    const val ROUTE_CATEGORIES = "categories"
    const val ROUTE_PINNED = "pinned_notes"
    const val ROUTE_ARCHIVED = "archived_notes"
    const val ROUTE_TRASH = "trash"
    const val ROUTE_BACKUP_RESTORE = "backup_restore"
    const val ROUTE_BACKUP_HISTORY = "backup_history"
    const val ROUTE_SETTINGS = "settings"
    const val ROUTE_REMINDER_SETTINGS = "reminder_settings"
    const val ROUTE_UPDATE_AVAILABLE = "update_available"
    const val ROUTE_ABOUT = "about"
    const val ROUTE_PRIVACY_POLICY = "privacy_policy"
    const val ROUTE_TERMS = "terms_conditions"
    const val ROUTE_CONTACT_US = "contact_us"

    fun editNote(noteId: Long): String = "edit_note/$noteId"
    fun noteDetails(noteId: Long): String = "note_details/$noteId"
    fun search(categoryId: Long? = null): String =
        if (categoryId != null) "search?categoryId=$categoryId" else "search"
}
