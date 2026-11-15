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
    version = 1,
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
         * Explicit forward-compatible migration registry.
         * Never use fallbackToDestructiveMigration() so user notes are never lost on app updates.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Reserved for future schema upgrades without losing existing user data.
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
                .addMigrations(MIGRATION_1_2)
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
