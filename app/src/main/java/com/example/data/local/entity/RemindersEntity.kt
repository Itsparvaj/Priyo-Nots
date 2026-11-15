package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(
            entity = NotesEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["noteId"]),
        Index(value = ["triggerAtMillis"]),
        Index(value = ["isActive"]),
        Index(value = ["syncKey"], unique = true)
    ]
)
data class RemindersEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val syncKey: String = UUID.randomUUID().toString(),
    val noteId: Long,
    val triggerAtMillis: Long,
    val repeatType: String = REPEAT_NONE,
    val isActive: Boolean = true,
    val requestCode: Int = (System.currentTimeMillis() and 0x7FFFFFFF).toInt(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val REPEAT_NONE = "NONE"
        const val REPEAT_DAILY = "DAILY"
        const val REPEAT_WEEKLY = "WEEKLY"
    }
}
