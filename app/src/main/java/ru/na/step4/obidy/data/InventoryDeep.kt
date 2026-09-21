package ru.na.step4.obidy.data

import org.json.JSONArray
import org.json.JSONObject

/** Один вопрос углублённой проработки обиды и ответ пользователя на него. */
data class InventoryDeepItem(
    val round: Int,
    val question: String,
    val answer: String = ""
)

/** Разбор с проработкой, сделанный по итогам одного круга вопросов. */
data class InventoryDeepAnalysis(
    val round: Int,
    val text: String
)

/**
 * Вся углублённая проработка одной ситуации: вопросы с ответами по кругам
 * и разборы с проработкой после них.
 */
data class InventoryDeepState(
    val round: Int = 1,
    val items: List<InventoryDeepItem> = emptyList(),
    val analyses: List<InventoryDeepAnalysis> = emptyList()
) {
    val isEmpty: Boolean get() = items.isEmpty() && analyses.isEmpty()

    val answered: List<InventoryDeepItem> get() = items.filter { it.answer.isNotBlank() }

    fun itemsOf(round: Int): List<InventoryDeepItem> = items.filter { it.round == round }

    fun analysisOf(round: Int): String =
        analyses.firstOrNull { it.round == round }?.text.orEmpty()

    fun rounds(): List<Int> {
        val last = maxOf(round, items.maxOfOrNull { it.round } ?: 1, analyses.maxOfOrNull { it.round } ?: 1)
        return (1..last).toList()
    }

    companion object {
        fun fromJson(obj: JSONObject?): InventoryDeepState {
            if (obj == null) return InventoryDeepState()
            val items = ArrayList<InventoryDeepItem>()
            obj.optJSONArray("items")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val question = item.optString("question").trim()
                    if (question.isBlank()) continue
                    items += InventoryDeepItem(
                        round = item.optInt("round", 1).coerceAtLeast(1),
                        question = question,
                        answer = item.optString("answer").trim()
                    )
                }
            }
            val analyses = ArrayList<InventoryDeepAnalysis>()
            obj.optJSONArray("analyses")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val text = item.optString("text").trim()
                    if (text.isBlank()) continue
                    analyses += InventoryDeepAnalysis(item.optInt("round", 1).coerceAtLeast(1), text)
                }
            }
            return InventoryDeepState(
                round = obj.optInt("round", 1).coerceAtLeast(1),
                items = items,
                analyses = analyses
            )
        }

        fun toJson(state: InventoryDeepState): JSONObject {
            val items = JSONArray()
            state.items.forEach { item ->
                items.put(
                    JSONObject()
                        .put("round", item.round)
                        .put("question", item.question)
                        .put("answer", item.answer)
                )
            }
            val analyses = JSONArray()
            state.analyses.forEach { analysis ->
                analyses.put(JSONObject().put("round", analysis.round).put("text", analysis.text))
            }
            return JSONObject()
                .put("round", state.round)
                .put("items", items)
                .put("analyses", analyses)
        }
    }
}
