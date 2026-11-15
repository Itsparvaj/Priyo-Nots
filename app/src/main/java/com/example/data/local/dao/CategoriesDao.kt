package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.CategoriesEntity
import com.example.data.local.model.CategoryWithCount
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoriesDao {

    @Query(
        """
        SELECT c.*, COUNT(n.id) AS noteCount
        FROM categories c
        LEFT JOIN notes n ON n.categoryId = c.id AND n.isTrashed = 0 AND n.isArchived = 0
        GROUP BY c.id
        ORDER BY c.name COLLATE NOCASE ASC
        """
    )
    fun observeCategoriesWithCount(): Flow<List<CategoryWithCount>>

    @Query("SELECT * FROM categories ORDER BY name COLLATE NOCASE ASC")
    fun observeAllCategories(): Flow<List<CategoriesEntity>>

    @Query("SELECT * FROM categories ORDER BY id ASC")
    suspend fun getAllCategoriesRaw(): List<CategoriesEntity>

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    suspend fun getCategoryById(id: Long): CategoriesEntity?

    @Query("SELECT * FROM categories WHERE syncKey = :syncKey LIMIT 1")
    suspend fun getBySyncKey(syncKey: String): CategoriesEntity?

    @Query("SELECT * FROM categories WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun getByName(name: String): CategoriesEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCategory(category: CategoriesEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<CategoriesEntity>): List<Long>

    @Update
    suspend fun updateCategory(category: CategoriesEntity)

    @Query("DELETE FROM categories WHERE id = :categoryId")
    suspend fun deleteCategoryById(categoryId: Long)

    @Query("DELETE FROM categories")
    suspend fun deleteAllCategories()
}
