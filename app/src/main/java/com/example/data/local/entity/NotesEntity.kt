package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(
            entity = CategoriesEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["categoryId"]),
        Index(value = ["isPinned"]),
        Index(value = ["isArchived"]),
        Index(value = ["isTrashed"]),
        Index(value = ["updatedAt"]),
        Index(value = ["title"]),
        Index(value = ["syncKey"], unique = true)
    ]
)
data class NotesEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val syncKey: String = UUID.randomUUID().toString(),
    val title: String = "",
    val contentHtml: String = "",
    val plainText: String = "",
    val colorKey: String = "default",
    val categoryId: Long? = null,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val archivedAt: Long? = null,
    val isTrashed: Boolean = false,
    val trashedAt: Long? = null,
    val wordCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
