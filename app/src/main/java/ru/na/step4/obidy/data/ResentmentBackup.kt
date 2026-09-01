package ru.na.step4.obidy.data

import org.json.JSONArray
import org.json.JSONObject

object ResentmentBackup {
    const val FORMAT = "step4-obidy"

    data class Result(
        val resentmentCount: Int,
        val situationCount: Int,
        val skipped: Int = 0
    )

    data class TypeDraft(
        val oldId: Long,
        val item: SituationType
    )

    data class SituationDraft(
        val item: Situation,
        val oldTypeIds: List<Long>
    )

    data class ResentmentDraft(
        val categoryName: String?,
        val item: Resentment,
        val types: List<TypeDraft>,
        val situations: List<SituationDraft>
    )

    data class CategoryDraft(
        val name: String,
        val sortOrder: Int,
        val createdAt: Long
    )

    data class Parsed(
        val categories: List<CategoryDraft>,
        val resentments: List<ResentmentDraft>
    )

    fun looksLikeBackup(name: String, text: String): Boolean {
        val trimmed = text.trimStart()
        if (!trimmed.startsWith("{")) return false
        return name.contains(".json", ignoreCase = true) ||
            trimmed.contains("\"$FORMAT\"") ||
            trimmed.contains("\"resentments\"")
    }

    fun fingerprint(item: Resentment): String =
        item.target.trim().lowercase() + "\u0000" + item.createdAt

    fun export(
        categories: List<Category>,
        resentments: List<Resentment>,
        types: List<SituationType>,
        situations: List<Situation>,
        links: List<SituationTypeLink>
    ): String {
        val catById = categories.associateBy { it.id }
        val typesByRes = types.groupBy { it.resentmentId }
        val sitsByRes = situations.groupBy { it.resentmentId }
        val typeIdsBySit = links.groupBy({ it.situationId }, { it.typeId })
        val root = JSONObject()
        root.put("format", FORMAT)
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())
        val cats = JSONArray()
        categories.forEach { category ->
            cats.put(
                JSONObject().apply {
                    put("id", category.id)
                    put("name", category.name)
                    put("sortOrder", category.sortOrder)
                    put("createdAt", category.createdAt)
                }
            )
        }
        root.put("categories", cats)
        val arr = JSONArray()
        resentments.forEach { resentment ->
            val resTypes = typesByRes[resentment.id].orEmpty()
            val resSits = sitsByRes[resentment.id].orEmpty()
            if (resentment.isDraft && resTypes.isEmpty() && resSits.isEmpty()) return@forEach
            arr.put(
                resentmentToJson(
                    resentment,
                    catById[resentment.categoryId]?.name,
                    resTypes,
                    resSits,
                    typeIdsBySit
                )
            )
        }
        root.put("resentments", arr)
        return root.toString(2)
    }

    fun parse(text: String): Parsed {
        val root = JSONObject(text)
        val format = root.optString("format")
        if (format.isNotEmpty() && format != FORMAT) error("format")
        val categories = mutableListOf<CategoryDraft>()
        val cats = root.optJSONArray("categories")
        if (cats != null) {
            for (i in 0 until cats.length()) {
                val obj = cats.optJSONObject(i) ?: continue
                val name = obj.optString("name").trim()
                if (name.isEmpty()) continue
                categories += CategoryDraft(
                    name = name,
                    sortOrder = obj.optInt("sortOrder"),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                )
            }
        }
        val resentments = mutableListOf<ResentmentDraft>()
        val arr = root.optJSONArray("resentments") ?: error("resentments")
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            resentments += parseResentment(obj)
        }
        return Parsed(categories, resentments)
    }

    private fun resentmentToJson(
        item: Resentment,
        categoryName: String?,
        types: List<SituationType>,
        situations: List<Situation>,
        typeIdsBySit: Map<Long, List<Long>>
    ): JSONObject {
        val obj = JSONObject()
        putResentmentFields(obj, item)
        if (categoryName != null) obj.put("categoryName", categoryName)
        val typeArr = JSONArray()
        types.forEach { type ->
            typeArr.put(
                JSONObject().apply {
                    put("id", type.id)
                    put("name", type.name)
                    put("sortOrder", type.sortOrder)
                    put("createdAt", type.createdAt)
                }
            )
        }
        obj.put("types", typeArr)
        val sitArr = JSONArray()
        situations.forEach { situation ->
            sitArr.put(
                JSONObject().apply {
                    putSituationFields(this, situation)
                    val ids = JSONArray()
                    typeIdsBySit[situation.id].orEmpty().forEach { ids.put(it) }
                    put("typeIds", ids)
                }
            )
        }
        obj.put("situations", sitArr)
        return obj
    }

    private fun parseResentment(obj: JSONObject): ResentmentDraft {
        val categoryName = obj.optString("categoryName").trim().ifBlank { null }
        val item = Resentment(
            id = obj.optLong("id"),
            categoryId = if (obj.has("categoryId") && !obj.isNull("categoryId")) {
                obj.optLong("categoryId")
            } else {
                null
            },
            target = obj.optString("target"),
            whatHappened = obj.optString("whatHappened"),
            iFelt = obj.optString("iFelt"),
            iDid = obj.optString("iDid"),
            q1 = obj.optString("q1"),
            q2 = obj.optString("q2"),
            q3 = obj.optString("q3"),
            q4 = obj.optString("q4"),
            q5 = obj.optString("q5"),
            q6 = obj.optString("q6"),
            q7 = obj.optString("q7"),
            q8 = obj.optString("q8"),
            q9 = obj.optString("q9"),
            q10 = obj.optString("q10"),
            q11 = obj.optString("q11"),
            q12 = obj.optString("q12"),
            q13 = obj.optString("q13"),
            notes = obj.optString("notes"),
            isCompleted = obj.optBoolean("isCompleted"),
            cause = obj.optString("cause"),
            affectedAreas = obj.optString("affectedAreas"),
            myPart = obj.optString("myPart"),
            defects = obj.optString("defects"),
            higherPowerWish = obj.optString("higherPowerWish"),
            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
        )
        val types = mutableListOf<TypeDraft>()
        val typeArr = obj.optJSONArray("types")
        if (typeArr != null) {
            for (i in 0 until typeArr.length()) {
                val t = typeArr.optJSONObject(i) ?: continue
                val oldId = t.optLong("id")
                types += TypeDraft(
                    oldId = oldId,
                    item = SituationType(
                        id = oldId,
                        resentmentId = item.id,
                        name = t.optString("name"),
                        sortOrder = t.optInt("sortOrder"),
                        createdAt = t.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        }
        val situations = mutableListOf<SituationDraft>()
        val sitArr = obj.optJSONArray("situations")
        if (sitArr != null) {
            for (i in 0 until sitArr.length()) {
                val s = sitArr.optJSONObject(i) ?: continue
                val typeIds = mutableListOf<Long>()
                val ids = s.optJSONArray("typeIds")
                if (ids != null) {
                    for (j in 0 until ids.length()) typeIds += ids.optLong(j)
                }
                situations += SituationDraft(
                    item = Situation(
                        id = s.optLong("id"),
                        resentmentId = item.id,
                        title = s.optString("title"),
                        whatHappened = s.optString("whatHappened"),
                        iFelt = s.optString("iFelt"),
                        iDid = s.optString("iDid"),
                        q1 = s.optString("q1"),
                        q2 = s.optString("q2"),
                        q3 = s.optString("q3"),
                        q4 = s.optString("q4"),
                        q5 = s.optString("q5"),
                        q6 = s.optString("q6"),
                        q7 = s.optString("q7"),
                        q8 = s.optString("q8"),
                        q9 = s.optString("q9"),
                        q10 = s.optString("q10"),
                        q11 = s.optString("q11"),
                        q12 = s.optString("q12"),
                        q13 = s.optString("q13"),
                        sortOrder = s.optInt("sortOrder"),
                        updatedAt = s.optLong("updatedAt", System.currentTimeMillis())
                    ),
                    oldTypeIds = typeIds
                )
            }
        }
        return ResentmentDraft(categoryName, item, types, situations)
    }

    private fun putResentmentFields(obj: JSONObject, item: Resentment) {
        obj.put("id", item.id)
        if (item.categoryId != null) obj.put("categoryId", item.categoryId)
        obj.put("target", item.target)
        obj.put("whatHappened", item.whatHappened)
        obj.put("iFelt", item.iFelt)
        obj.put("iDid", item.iDid)
        obj.put("q1", item.q1)
        obj.put("q2", item.q2)
        obj.put("q3", item.q3)
        obj.put("q4", item.q4)
        obj.put("q5", item.q5)
        obj.put("q6", item.q6)
        obj.put("q7", item.q7)
        obj.put("q8", item.q8)
        obj.put("q9", item.q9)
        obj.put("q10", item.q10)
        obj.put("q11", item.q11)
        obj.put("q12", item.q12)
        obj.put("q13", item.q13)
        obj.put("notes", item.notes)
        obj.put("isCompleted", item.isCompleted)
        obj.put("cause", item.cause)
        obj.put("affectedAreas", item.affectedAreas)
        obj.put("myPart", item.myPart)
        obj.put("defects", item.defects)
        obj.put("higherPowerWish", item.higherPowerWish)
        obj.put("createdAt", item.createdAt)
        obj.put("updatedAt", item.updatedAt)
    }

    private fun putSituationFields(obj: JSONObject, item: Situation) {
        obj.put("id", item.id)
        obj.put("title", item.title)
        obj.put("whatHappened", item.whatHappened)
        obj.put("iFelt", item.iFelt)
        obj.put("iDid", item.iDid)
        obj.put("q1", item.q1)
        obj.put("q2", item.q2)
        obj.put("q3", item.q3)
        obj.put("q4", item.q4)
        obj.put("q5", item.q5)
        obj.put("q6", item.q6)
        obj.put("q7", item.q7)
        obj.put("q8", item.q8)
        obj.put("q9", item.q9)
        obj.put("q10", item.q10)
        obj.put("q11", item.q11)
        obj.put("q12", item.q12)
        obj.put("q13", item.q13)
        obj.put("sortOrder", item.sortOrder)
        obj.put("updatedAt", item.updatedAt)
    }
}
