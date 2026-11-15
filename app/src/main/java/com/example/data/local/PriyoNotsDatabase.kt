package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.AppSettingsDao
import com.example.data.local.dao.BackupHistoryDao
import com.example.data.local.dao.CategoriesDao
import com.example.data.local.dao.NotesDao
import com.example.data.local.dao.RemindersDao
import com.example.data.local.entity.AppSettingsEntity
import com.example.data.local.entity.BackupHistoryEntity
import com.example.data.local.entity.CategoriesEntity
import com.example.data.local.entity.NotesEntity
import com.example.data.local.entity.RemindersEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        NotesEntity::class,
        CategoriesEntity::class,
        RemindersEntity::class,
        AppSettingsEntity::class,
        BackupHistoryEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class PriyoNotsDatabase : RoomDatabase() {

    abstract fun notesDao(): NotesDao
    abstract fun categoriesDao(): CategoriesDao
    abstract fun remindersDao(): RemindersDao
    abstract fun appSettingsDao(): AppSettingsDao
    abstract fun backupHistoryDao(): BackupHistoryDao

    companion object {
        private const val DB_NAME = "priyo_nots.db"

        @Volatile
        private var INSTANCE: PriyoNotsDatabase? = null

        /**
         * Safe non-destructive migration from v1 to v2 adding GitHub Releases and Gist/Repo backup columns.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN updateSourceType TEXT NOT NULL DEFAULT 'GITHUB_RELEASES'")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN githubReleaseRepo TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN githubUsername TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN githubBackupMode TEXT NOT NULL DEFAULT 'GIST'")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN githubGistId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN githubRepoSlug TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN githubRepoBranch TEXT NOT NULL DEFAULT 'main'")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN githubRepoFilePath TEXT NOT NULL DEFAULT 'backups/priyo_nots_backup.json'")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN githubLastBackupAt INTEGER")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN githubLastStatus TEXT NOT NULL DEFAULT 'Not connected'")
            }
        }

        /**
         * Safe non-destructive migration from v2 to v3 adding Google Drive backup settings columns.
         */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN googleDriveEmail TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN googleDriveFolderId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN googleDriveLastBackupAt INTEGER")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN googleDriveLastStatus TEXT NOT NULL DEFAULT 'Not connected'")
            }
        }

        fun getInstance(context: Context): PriyoNotsDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(appContext: Context): PriyoNotsDatabase {
            return Room.databaseBuilder(
                appContext,
                PriyoNotsDatabase::class.java,
                DB_NAME
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        CoroutineScope(Dispatchers.IO).launch {
                            seedInitialDefaults(getInstance(appContext))
                        }
                    }
                })
                .build()
        }

        private suspend fun seedInitialDefaults(database: PriyoNotsDatabase) {
            val settingsDao = database.appSettingsDao()
            if (settingsDao.getSettings() == null) {
                settingsDao.upsertSettings(AppSettingsEntity())
            }
            val categoriesDao = database.categoriesDao()
            if (categoriesDao.getAllCategoriesRaw().isEmpty()) {
                val now = System.currentTimeMillis()
                val defaults = listOf(
                    CategoriesEntity(
                        syncKey = "default-cat-personal",
                        name = "Personal",
                        colorHex = "#4F1ED8",
                        iconName = "person",
                        createdAt = now
                    ),
                    CategoriesEntity(
                        syncKey = "default-cat-work",
                        name = "Work",
                        colorHex = "#0EA5E9",
                        iconName = "work",
                        createdAt = now
                    ),
                    CategoriesEntity(
                        syncKey = "default-cat-ideas",
                        name = "Ideas",
                        colorHex = "#F59E0B",
                        iconName = "lightbulb",
                        createdAt = now
                    ),
                    CategoriesEntity(
                        syncKey = "default-cat-study",
                        name = "Study",
                        colorHex = "#10B981",
                        iconName = "school",
                        createdAt = now
                    )
                )
                defaults.forEach { cat ->
                    runCatching { categoriesDao.insertCategory(cat) }
                }
            }
        }
    }
}
