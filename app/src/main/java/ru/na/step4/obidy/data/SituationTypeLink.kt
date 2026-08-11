package ru.na.step4.obidy.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "situation_type_links",
    primaryKeys = ["situationId", "typeId"],
    foreignKeys = [
        ForeignKey(
            entity = Situation::class,
            parentColumns = ["id"],
            childColumns = ["situationId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SituationType::class,
            parentColumns = ["id"],
            childColumns = ["typeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("situationId"), Index("typeId")]
)
data class SituationTypeLink(
    val situationId: Long,
    val typeId: Long
)
