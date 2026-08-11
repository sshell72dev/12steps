# -*- coding: utf-8 -*-
"""Generate DB v5 entities, migration, DAO, TypeSuggest, repository."""
from pathlib import Path

ROOT = Path(r"d:/sites/step4obidy/app/src/main/java/ru/na/step4/obidy")
MOD = chr(77) + "odifier"


def w(rel: str, content: str):
    content = content.replace("UI_MODIFIER", MOD)
    (ROOT / rel).write_text(content, encoding="utf-8", newline="\n")
    print("wrote", rel)


w(
    "data/SituationTypeLink.kt",
    r'''package ru.na.step4.obidy.data

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
''',
)

w(
    "data/Situation.kt",
    r'''package ru.na.step4.obidy.data

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
''',
)

w(
    "data/TypeSuggestEngine.kt",
    r'''package ru.na.step4.obidy.data

/**
 * Local type suggester for a situation description.
 * Ranks existing types and proposes new labels from presets + keyword cues.
 */
object TypeSuggestEngine {

    data class Result(
        /** Existing types for this resentment, A–Z. Matching ones listed first within the block. */
        val existing: List<SituationType>,
        /** New type names not yet on the resentment (AI / preset suggestions). */
        val proposed: List<String>
    )

    private val cueMap: List<Pair<List<String>, String>> = listOf(
        listOf("критик", "униж", "оскорб", "высме", "стыд", "обесцен") to "Критика / унижение",
        listOf("предат", "измен", "обману", "врёт", "врал", "лгал") to "Предательство",
        listOf("игнор", "молчит", "не отвечает", "отверг", "брос") to "Игнор",
        listOf("контрол", "запрещ", "давлени", "команд", "диктов") to "Контроль",
        listOf("обещан", "не сдержал", "нарушил слово", "кинул") to "Невыполненные обещания",
        listOf("удар", "угроз", "насили", "кричал", "бил", "страшн") to "Насилие / угроза",
        listOf("ожидан", "разочаров", "должен был", "хотел чтобы") to "Разочарование в ожиданиях",
        listOf("ревност", "ревну") to "Ревность",
        listOf("отвержен", "не любят", "не любят меня") to "Отвержение",
        listOf("несправед", "нечестн", "двойные стандарт") to "Несправедливость",
        listOf("отказ", "не дал", "отказал") to "Отказ / отказ в помощи",
        listOf("сравнен", "лучше чем я", "хуже меня") to "Сравнение",
        listOf("граница", "вторг", "личн") to "Нарушение границ",
        listOf("деньг", "долг", "финанс", "зарплат") to "Деньги / долги",
        listOf("работ", "начальник", "коллег", "увольн") to "Работа / коллеги",
        listOf("семь", "мама", "папа", "родител", "брат", "сестр") to "Семья",
    )

    fun suggest(text: String, existing: List<SituationType>): Result {
        val hay = text.lowercase()
        val existingSorted = existing.sortedWith(
            compareByDescending<SituationType> { type -> scoreName(hay, type.name) > 0 }
                .thenBy { it.name.lowercase() }
        )

        val existingNames = existing.map { it.name.lowercase() }.toSet()
        val scored = linkedMapOf<String, Int>()

        fun addCandidate(name: String, bonus: Int) {
            val key = name.trim()
            if (key.isEmpty()) return
            if (key.lowercase() in existingNames) return
            scored[key] = maxOf(scored[key] ?: 0, bonus)
        }

        InventoryStructure.suggestedSituationTypes.forEach { preset ->
            val s = scoreName(hay, preset)
            if (s > 0 || hay.isBlank()) addCandidate(preset, s + 1)
        }

        cueMap.forEach { (cues, label) ->
            if (cues.any { hay.contains(it) }) addCandidate(label, 10)
        }

        // Phrase-derived short labels from significant words
        tokenize(hay).filter { it.length >= 5 }.take(4).forEach { token ->
            val titled = token.replaceFirstChar { it.uppercase() }
            addCandidate(titled, 2)
        }

        val proposed = scored.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key.lowercase() })
            .map { it.key }
            .distinct()
            .take(12)

        return Result(existing = existingSorted, proposed = proposed)
    }

    fun filterCatalog(query: String, names: List<String>): List<String> {
        val sorted = names.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            .sortedBy { it.lowercase() }
        if (query.isBlank()) return sorted
        val q = query.trim().lowercase()
        return sorted.filter { it.lowercase().contains(q) }
    }

    fun catalogNames(existing: List<SituationType>): List<String> {
        val fromDb = existing.map { it.name }
        val presets = InventoryStructure.suggestedSituationTypes
        return (fromDb + presets).distinct()
    }

    private fun scoreName(hay: String, name: String): Int {
        if (hay.isBlank()) return 0
        val n = name.lowercase()
        var score = 0
        if (hay.contains(n)) score += 8
        tokenize(n).forEach { part ->
            if (part.length >= 3 && hay.contains(part)) score += 3
        }
        tokenize(hay).forEach { part ->
            if (part.length >= 4 && n.contains(part)) score += 2
        }
        return score
    }

    private fun tokenize(text: String): List<String> =
        text.lowercase()
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 3 }
}
''',
)

print("entities+suggest ok")
