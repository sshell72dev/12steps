package ru.na.step4.obidy.data.analysis

import android.content.Context
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

object AnalysisCatalog {
    const val MENU_CLEAN_DAY = "clean-day"
    const val ASSET_FILE = "self-analysis-questions.json"

    val menuIds = listOf(
        "10-step",
        "10-step-mini",
        "1-shag-na-kazhdyj-den",
        "2-shag-na-kazhdyj-den",
        "3-shag-na-kazhdyj-den",
        "12-voprosov",
        MENU_CLEAN_DAY
    )

    @Volatile
    private var defaultsCached: List<CatalogEntry>? = null

    fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(12)

    fun loadDefaults(context: Context): List<CatalogEntry> {
        defaultsCached?.let { return it }
        val json = context.applicationContext.assets
            .open(ASSET_FILE)
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        return parse(json).also { defaultsCached = it }
    }

    fun load(context: Context): List<CatalogEntry> = loadDefaults(context)

    fun parse(json: String): List<CatalogEntry> {
        val root = JSONObject(json)
        val arr = root.optJSONArray("self_analyses") ?: return emptyList()
        val out = ArrayList<CatalogEntry>(arr.length())
        for (i in 0 until arr.length()) {
            out.add(parseEntry(arr.getJSONObject(i)))
        }
        return out.sortedBy { it.menuOrder }
    }

    fun encodeCatalog(entries: List<CatalogEntry>): JSONObject =
        JSONObject().put(
            "self_analyses",
            JSONArray().also { arr ->
                entries.sortedBy { it.menuOrder }.forEach { arr.put(encodeEntry(it)) }
            }
        )

    fun standardCatalog(context: Context, settings: AnalysisSettings): List<CatalogEntry> {
        val byId = LinkedHashMap<String, CatalogEntry>()
        loadDefaults(context).forEach { byId[it.id] = it }
        settings.remoteEntries().forEach { byId[it.id] = it }
        return byId.values.sortedBy { it.menuOrder }
    }

    fun resolved(context: Context, settings: AnalysisSettings): List<CatalogEntry> {
        val byId = LinkedHashMap<String, CatalogEntry>()
        standardCatalog(context, settings).forEach { byId[it.id] = it }
        settings.overrides().forEach { (id, entry) -> byId[id] = entry }
        val extras = settings.customEntries().filter { it.id !in byId }
        return (byId.values + extras).sortedBy { it.menuOrder }
    }

    fun byId(context: Context, id: String, settings: AnalysisSettings): CatalogEntry? =
        resolved(context, settings).find { it.id == id }

    fun byId(context: Context, id: String): CatalogEntry? =
        loadDefaults(context).find { it.id == id }

    fun isBuiltin(id: String): Boolean =
        id != MENU_CLEAN_DAY && (id in menuIds || id == "clean-day-short" || id == "clean-day-long")

    fun resolveSessionId(menuId: String, settings: AnalysisSettings): String =
        if (menuId == MENU_CLEAN_DAY) settings.cleanDayId() else menuId

    fun hubItems(context: Context, settings: AnalysisSettings): List<Pair<String, String>> {
        val all = resolved(context, settings)
        val builtins = menuIds.map { id ->
            val title = if (id == MENU_CLEAN_DAY) {
                ru.na.step4.obidy.data.i18n.I18n.t("analysis.cleanDay.title", "Чистый день")
            } else {
                val source = all.find { it.id == id }?.title ?: id
                ru.na.step4.obidy.data.i18n.ContentI18n.localizedAnalysisTitle(id, source)
            }
            id to title
        }
        val extra = all.filter { entry ->
            entry.id !in menuIds &&
                entry.id != "clean-day-short" &&
                entry.id != "clean-day-long"
        }.sortedBy { it.menuOrder }
        return builtins + extra.map {
            it.id to ru.na.step4.obidy.data.i18n.ContentI18n.localizedAnalysisTitle(it.id, it.title)
        }
    }

    fun parseEntry(obj: JSONObject): CatalogEntry {
        val id = obj.getString("id")
        val questions = parseQuestions(obj.optJSONArray("questions"), id)
        val branches = parseBranches(obj.optJSONArray("branches"))
        val items = parseItems(obj.optJSONArray("items"))
        val custom = obj.optBoolean("custom", false)
        val flow = when {
            id == "10-step" -> AnalysisFlow.STEP10
            id == "10-step-mini" -> AnalysisFlow.MINI
            id == "1-shag-na-kazhdyj-den" -> AnalysisFlow.LINEAR_PREVIEW
            id == "2-shag-na-kazhdyj-den" || id == "3-shag-na-kazhdyj-den" -> AnalysisFlow.BRANCHED
            id == "12-voprosov" -> AnalysisFlow.LINEAR_NOW
            id == "clean-day-short" || id == "clean-day-long" -> AnalysisFlow.CLEAN_DAY
            items.isNotEmpty() -> AnalysisFlow.CLEAN_DAY
            branches.isNotEmpty() -> AnalysisFlow.BRANCHED
            else -> AnalysisFlow.LINEAR_NOW
        }
        return CatalogEntry(
            id = id,
            title = obj.getString("title"),
            menuOrder = obj.optInt("menu_order", 99),
            flow = flow,
            questions = questions,
            branches = branches,
            items = items,
            custom = custom
        )
    }

    fun encodeEntry(entry: CatalogEntry): JSONObject {
        val obj = JSONObject()
            .put("id", entry.id)
            .put("title", entry.title)
            .put("menu_order", entry.menuOrder)
            .put("custom", entry.custom)
        if (entry.questions.isNotEmpty()) {
            obj.put("questions", JSONArray().also { arr ->
                entry.questions.forEach { arr.put(encodeQuestion(it)) }
            })
        }
        if (entry.branches.isNotEmpty()) {
            obj.put("branches", JSONArray().also { arr ->
                entry.branches.forEach { branch ->
                    arr.put(
                        JSONObject()
                            .put("id", branch.id)
                            .put("title", branch.title)
                            .put("questions", stringArray(branch.questions))
                    )
                }
            })
        }
        if (entry.items.isNotEmpty()) {
            obj.put("items", JSONArray().also { arr ->
                entry.items.forEach { item ->
                    arr.put(
                        JSONObject()
                            .put("title", item.title)
                            .put("question", item.question)
                            .put("if_yes", encodeSide(item.ifYes))
                            .put("if_no", encodeSide(item.ifNo))
                    )
                }
            })
        }
        return obj
    }

    fun blankCustom(title: String, menuOrder: Int): CatalogEntry {
        val id = "custom-${newId()}"
        return CatalogEntry(
            id = id,
            title = title.ifBlank { "Мой самоанализ" },
            menuOrder = menuOrder,
            flow = AnalysisFlow.LINEAR_NOW,
            questions = listOf(
                LinearQuestion(
                    id = "$id-q1",
                    text = "Новый вопрос",
                    buttons = QuestionButtons.NONE
                )
            ),
            custom = true
        )
    }

    fun blankQuestion(prefix: String = "q"): LinearQuestion =
        LinearQuestion(
            id = "$prefix-${newId()}",
            text = "Новый вопрос",
            buttons = QuestionButtons.NONE
        )

    fun yesNoChoices(): List<Choice> = listOf(
        Choice("yes", "Да"),
        Choice("no", "Нет")
    )

    private fun parseQuestions(arr: JSONArray?, entryId: String): List<LinearQuestion> {
        if (arr == null) return emptyList()
        val out = ArrayList<LinearQuestion>(arr.length())
        for (i in 0 until arr.length()) {
            out.add(parseQuestion(arr.get(i), "$entryId-$i"))
        }
        return out
    }

    private fun parseQuestion(item: Any, fallbackId: String): LinearQuestion {
        if (item is String) {
            return LinearQuestion(id = fallbackId, text = item)
        }
        val obj = item as JSONObject
        val id = obj.optString("id").ifBlank { fallbackId }
        val prayerObj = obj.optJSONObject("prayer")
        val choicesArr = obj.optJSONArray("choices")
        val buttons = when (obj.optString("buttons")) {
            "none" -> QuestionButtons.NONE
            "list" -> QuestionButtons.LIST
            "auto" -> QuestionButtons.AUTO
            else -> when {
                choicesArr != null -> QuestionButtons.LIST
                obj.has("allow_text") && choicesArr == null && obj.optBoolean("no_buttons") ->
                    QuestionButtons.NONE
                else -> QuestionButtons.AUTO
            }
        }
        val choices = parseChoices(choicesArr)
        val skipMap = mutableMapOf<String, Int>()
        obj.optJSONObject("skip_next_on")?.let { skipObj ->
            skipObj.keys().forEach { key -> skipMap[key] = skipObj.optInt(key, 0) }
        }
        val endOn = mutableSetOf<String>()
        obj.optJSONArray("end_on")?.let { arr ->
            for (i in 0 until arr.length()) endOn += arr.getString(i)
        }
        val follow = mutableMapOf<String, List<LinearQuestion>>()
        obj.optJSONObject("follow_ups")?.let { followObj ->
            followObj.keys().forEach { key ->
                val arr = followObj.optJSONArray(key) ?: return@forEach
                follow[key] = (0 until arr.length()).map { i ->
                    parseQuestion(arr.get(i), "$id-$key-$i")
                }
            }
        }
        return LinearQuestion(
            id = id,
            text = obj.getString("text"),
            prayer = prayerObj?.let { p ->
                Prayer(title = p.optString("title"), text = p.getString("text"))
            },
            skipNextOnNo = obj.optInt("skip_next_questions_on_no", 0),
            buttons = buttons,
            choices = choices,
            allowText = obj.optBoolean("allow_text", true),
            skipNextByChoiceId = skipMap,
            endOnChoiceIds = endOn,
            followUps = follow
        )
    }

    private fun parseChoices(arr: JSONArray?): List<Choice> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { i ->
            when (val item = arr.get(i)) {
                is String -> Choice(newId(), item)
                is JSONObject -> Choice(
                    id = item.optString("id").ifBlank { newId() },
                    label = item.getString("label")
                )
                else -> Choice(newId(), item.toString())
            }
        }
    }

    private fun encodeQuestion(q: LinearQuestion): JSONObject {
        val obj = JSONObject()
            .put("id", q.id)
            .put("text", q.text)
            .put("allow_text", q.allowText)
            .put(
                "buttons",
                when (q.buttons) {
                    QuestionButtons.AUTO -> "auto"
                    QuestionButtons.NONE -> "none"
                    QuestionButtons.LIST -> "list"
                }
            )
        if (q.skipNextOnNo > 0) obj.put("skip_next_questions_on_no", q.skipNextOnNo)
        q.prayer?.let { p ->
            obj.put(
                "prayer",
                JSONObject().put("title", p.title).put("text", p.text)
            )
        }
        if (q.choices.isNotEmpty()) {
            obj.put("choices", JSONArray().also { arr ->
                q.choices.forEach { c ->
                    arr.put(JSONObject().put("id", c.id).put("label", c.label))
                }
            })
        }
        if (q.skipNextByChoiceId.isNotEmpty()) {
            obj.put("skip_next_on", JSONObject().also { skip ->
                q.skipNextByChoiceId.forEach { (k, v) -> skip.put(k, v) }
            })
        }
        if (q.endOnChoiceIds.isNotEmpty()) {
            obj.put("end_on", JSONArray().also { arr ->
                q.endOnChoiceIds.forEach { arr.put(it) }
            })
        }
        if (q.followUps.isNotEmpty()) {
            obj.put("follow_ups", JSONObject().also { follow ->
                q.followUps.forEach { (key, list) ->
                    follow.put(key, JSONArray().also { arr ->
                        list.forEach { arr.put(encodeQuestion(it)) }
                    })
                }
            })
        }
        return obj
    }

    private fun parseBranches(arr: JSONArray?): List<AnalysisBranch> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            AnalysisBranch(
                id = o.getString("id"),
                title = o.getString("title"),
                questions = stringList(o.optJSONArray("questions"))
            )
        }
    }

    private fun parseItems(arr: JSONArray?): List<CleanDayItem> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            CleanDayItem(
                title = o.getString("title"),
                question = o.getString("question"),
                ifYes = parseSide(o.getJSONObject("if_yes")),
                ifNo = parseSide(o.getJSONObject("if_no"))
            )
        }
    }

    private fun parseSide(o: JSONObject): CleanDaySide =
        CleanDaySide(
            label = o.getString("label"),
            questions = stringList(o.optJSONArray("questions"))
        )

    private fun encodeSide(side: CleanDaySide): JSONObject =
        JSONObject()
            .put("label", side.label)
            .put("questions", stringArray(side.questions))

    private fun stringList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { arr.getString(it) }
    }

    private fun stringArray(items: List<String>): JSONArray =
        JSONArray().also { arr -> items.forEach { arr.put(it) } }
}
