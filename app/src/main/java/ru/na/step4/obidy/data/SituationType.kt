package ru.na.step4.obidy.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "situation_types",
    foreignKeys = [
        ForeignKey(
            entity = Resentment::class,
            parentColumns = ["id"],
            childColumns = ["resentmentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("resentmentId")]
)
data class SituationType(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val resentmentId: Long,
    val name: String,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
