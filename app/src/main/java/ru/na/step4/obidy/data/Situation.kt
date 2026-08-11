package ru.na.step4.obidy.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "situations",
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
data class Situation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val resentmentId: Long,
    val title: String = "",
    val whatHappened: String = "",
    val iFelt: String = "",
    val iDid: String = "",
    val q1: String = "",
    val q2: String = "",
    val q3: String = "",
    val q4: String = "",
    val q5: String = "",
    val q6: String = "",
    val q7: String = "",
    val q8: String = "",
    val q9: String = "",
    val q10: String = "",
    val q11: String = "",
    val q12: String = "",
    val q13: String = "",
    val sortOrder: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
) {
    val progressSteps: Int
        get() {
            var n = 0
            if (title.isNotBlank() || whatHappened.isNotBlank()) n++
            if (whatHappened.isNotBlank()) n++
            if (iFelt.isNotBlank()) n++
            if (iDid.isNotBlank()) n++
            if (q1.isNotBlank()) n++
            if (q2.isNotBlank()) n++
            if (q3.isNotBlank()) n++
            if (q4.isNotBlank()) n++
            if (q5.isNotBlank()) n++
            if (q6.isNotBlank()) n++
            if (q7.isNotBlank()) n++
            if (q8.isNotBlank()) n++
            if (q9.isNotBlank()) n++
            if (q10.isNotBlank()) n++
            if (q11.isNotBlank()) n++
            if (q12.isNotBlank()) n++
            if (q13.isNotBlank()) n++
            return n
        }

    fun answerFor(number: Int): String = when (number) {
        1 -> q1
        2 -> q2
        3 -> q3
        4 -> q4
        5 -> q5
        6 -> q6
        7 -> q7
        8 -> q8
        9 -> q9
        10 -> q10
        11 -> q11
        12 -> q12
        13 -> q13
        else -> ""
    }

    fun withAnswer(number: Int, value: String): Situation = when (number) {
        1 -> copy(q1 = value)
        2 -> copy(q2 = value)
        3 -> copy(q3 = value)
        4 -> copy(q4 = value)
        5 -> copy(q5 = value)
        6 -> copy(q6 = value)
        7 -> copy(q7 = value)
        8 -> copy(q8 = value)
        9 -> copy(q9 = value)
        10 -> copy(q10 = value)
        11 -> copy(q11 = value)
        12 -> copy(q12 = value)
        13 -> copy(q13 = value)
        else -> this
    }

    val preview: String
        get() = title.ifBlank { whatHappened }.ifBlank { "\u041d\u043e\u0432\u0430\u044f \u0441\u0438\u0442\u0443\u0430\u0446\u0438\u044f" }

    companion object {
        const val TOTAL_STEPS = 17
    }
}
