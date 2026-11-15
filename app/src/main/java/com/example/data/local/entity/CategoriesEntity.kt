package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "categories",
    indices = [
        Index(value = ["name"], unique = true),
        Index(value = ["syncKey"], unique = true)
    ]
)
data class CategoriesEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val syncKey: String = UUID.randomUUID().toString(),
    val name: String,
    val colorHex: String = "#4F1ED8",
    val iconName: String = "folder",
    val createdAt: Long = System.currentTimeMillis()
)
