package com.example.data.local.model

import androidx.room.Embedded
import androidx.room.Relation
import com.example.data.local.entity.CategoriesEntity
import com.example.data.local.entity.NotesEntity
import com.example.data.local.entity.RemindersEntity

data class NoteWithRelations(
    @Embedded val note: NotesEntity,
    @Relation(
        parentColumn = "categoryId",
        entityColumn = "id"
    )
    val category: CategoriesEntity?,
    @Relation(
        parentColumn = "id",
        entityColumn = "noteId"
    )
    val reminders: List<RemindersEntity>
) {
    val activeReminder: RemindersEntity?
        get() = reminders.firstOrNull { it.isActive }
}

data class CategoryWithCount(
    @Embedded val category: CategoriesEntity,
    val noteCount: Int
)
