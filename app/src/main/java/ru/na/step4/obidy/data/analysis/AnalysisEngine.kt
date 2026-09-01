package ru.na.step4.obidy.data.analysis

import org.json.JSONArray
import org.json.JSONObject

class AnalysisEngine(
    private val entry: CatalogEntry,
    miniCount: Int = 0
) {
    private var miniN = allowedMini(miniCount)

    private var miniQuestions: List<LinearQuestion> = emptyList()
    private var index = 0
    private var phase = initialPhase()
    private val answers = mutableListOf<QaPair>()
    private val checkpoints = mutableListOf<JSONObject>()
    private var selectedBranch: AnalysisBranch? = null
    private var branchIndex = 0
    private var itemIndex = 0
    private var cleanFollows: List<String> = emptyList()
    private var followIndex = 0
    private val followStack = ArrayDeque<Frame>()

    val title: String
        get() = if (entry.flow == AnalysisFlow.CLEAN_DAY) CLEAN_DAY_TITLE else entry.title

    val hasProgress: Boolean
        get() = answers.isNotEmpty()

    val answerCount: Int
        get() = answers.size

    val isDone: Boolean
        get() = phase == Phase.DONE

    val canReviewAnswers: Boolean
        get() = answers.isNotEmpty() && checkpoints.size == answers.size

    fun answerAt(index: Int): QaPair? = answers.getOrNull(index)

    fun answersSnapshot(): List<QaPair> = answers.toList()

    init {
        if (entry.flow == AnalysisFlow.MINI && miniN > 0) reshuffle()
    }

    fun restart() {
        answers.clear()
        checkpoints.clear()
        index = 0
        selectedBranch = null
        branchIndex = 0
        itemIndex = 0
        cleanFollows = emptyList()
        followIndex = 0
        followStack.clear()
        phase = initialPhase()
        if (entry.flow == AnalysisFlow.MINI) {
            miniN = 0
            miniQuestions = emptyList()
        }
    }

    fun capture(): JSONObject {
        val follow = JSONArray()
        followStack.forEach { frame ->
            follow.put(
                JSONObject()
                    .put("ids", JSONArray().also { ids ->
                        frame.questions.forEach { q -> ids.put(q.id) }
                    })
                    .put("index", frame.index)
            )
        }
        return JSONObject()
            .put("phase", phase.name)
            .put("index", index)
            .put("answers", JSONArray(AnalysisAnswers.encode(answers)))
            .put("mini_n", miniN)
            .put("mini_ids", JSONArray().also { arr ->
                miniQuestions.forEach { arr.put(it.id) }
            })
            .put("branch_id", selectedBranch?.id.orEmpty())
            .put("branch_index", branchIndex)
            .put("item_index", itemIndex)
            .put("clean_follows", JSONArray().also { arr ->
                cleanFollows.forEach { arr.put(it) }
            })
            .put("follow_index", followIndex)
            .put("follow_stack", follow)
            .put("checkpoints", JSONArray().also { arr ->
                checkpoints.forEach { arr.put(JSONObject(it.toString())) }
            })
    }

    fun restore(obj: JSONObject): Boolean {
        return runCatching {
            val restoredPhase = Phase.valueOf(obj.getString("phase"))
            val restoredMiniN = obj.optInt("mini_n", 0)
            val miniIds = obj.stringList("mini_ids")
            val restoredMini = if (entry.flow == AnalysisFlow.MINI) {
                if (restoredMiniN > 0 && miniIds.isEmpty()) return false
                miniIds.map { id ->
                    entry.questions.find { it.id == id } ?: return false
                }
            } else {
                emptyList()
            }
            val branchId = obj.optString("branch_id")
            val restoredBranch = if (branchId.isBlank()) {
                null
            } else {
                entry.branches.find { it.id == branchId } ?: return false
            }
            miniN = restoredMiniN
            miniQuestions = restoredMini
            index = obj.optInt("index")
            answers.clear()
            answers += AnalysisAnswers.decode(obj.optJSONArray("answers")?.toString().orEmpty())
            selectedBranch = restoredBranch
            branchIndex = obj.optInt("branch_index")
            itemIndex = obj.optInt("item_index")
            cleanFollows = obj.stringList("clean_follows")
            followIndex = obj.optInt("follow_index")
            followStack.clear()
            obj.optJSONArray("follow_stack")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val frame = arr.getJSONObject(i)
                    val ids = frame.stringList("ids")
                    val qs = ids.map { id -> questionById(id) ?: return false }
                    followStack.addLast(Frame(qs, frame.optInt("index")))
                }
            }
            checkpoints.clear()
            obj.optJSONArray("checkpoints")?.let { arr ->
                for (i in 0 until arr.length()) {
                    checkpoints += arr.getJSONObject(i)
                }
            }
            if (checkpoints.size != answers.size) {
                rebuildCheckpointsFallback()
            }
            phase = restoredPhase

            when (phase) {
                Phase.PREVIEW -> true
                Phase.ASK -> if (entry.flow == AnalysisFlow.CLEAN_DAY) {
                    itemIndex in entry.items.indices
                } else {
                    currentAsk() != null
                }
                Phase.BRANCH -> {
                    val branch = selectedBranch ?: return false
                    branchIndex in branch.questions.indices
                }
                Phase.CLEAN_FOLLOW -> followIndex in cleanFollows.indices
                Phase.DONE -> answers.isNotEmpty()
            }
        }.getOrDefault(false)
    }

    fun peekQuestionScreen(answerIndex: Int): SessionScreen.Question? {
        if (answerIndex !in answers.indices || answerIndex !in checkpoints.indices) return null
        val live = captureWithoutCheckpoints()
        val savedCheckpoints = checkpoints.map { JSONObject(it.toString()) }
        return try {
            if (!restoreStateOnly(checkpoints[answerIndex])) return null
            screen() as? SessionScreen.Question
        } finally {
            restoreStateOnly(live)
            checkpoints.clear()
            checkpoints += savedCheckpoints
        }
    }

    fun replaceAnswerText(answerIndex: Int, text: String): Boolean {
        if (answerIndex !in answers.indices || answerIndex !in checkpoints.indices) return false
        val t = text.trim()
        if (t.isEmpty()) return false
        if (!rewindTo(answerIndex)) return false
        submit(t)
        return true
    }

    fun replaceAnswerChoice(answerIndex: Int, id: String, extraText: String = ""): Boolean {
        if (answerIndex !in answers.indices || answerIndex !in checkpoints.indices) return false
        if (!rewindTo(answerIndex)) return false
        choose(id, extraText)
        return true
    }

    fun screen(): SessionScreen = when (phase) {
        Phase.PREVIEW -> previewScreen()
        Phase.ASK -> askScreen()
        Phase.BRANCH -> branchScreen()
        Phase.CLEAN_FOLLOW -> followScreen()
        Phase.DONE -> SessionScreen.Done(title, answers.toList())
    }

    fun begin() {
        if (phase != Phase.PREVIEW) return
        if (entry.flow == AnalysisFlow.MINI && miniQuestions.isEmpty()) return
        phase = Phase.ASK
        index = 0
        itemIndex = 0
        followStack.clear()
    }

    fun pickMiniCount(count: Int) {
        if (entry.flow != AnalysisFlow.MINI) return
        if (answers.isNotEmpty() || phase != Phase.PREVIEW) return
        val n = allowedMini(count)
        if (n <= 0) return
        miniN = n
        reshuffle()
    }

    fun reroll() {
        if (entry.flow != AnalysisFlow.MINI) return
        if (answers.isNotEmpty() || phase != Phase.PREVIEW) return
        if (miniN <= 0) return
        reshuffle()
    }

    fun submit(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        when (phase) {
            Phase.ASK -> onAskText(t)
            Phase.BRANCH -> onBranchText(t)
            Phase.CLEAN_FOLLOW -> onFollowText(t)
            else -> Unit
        }
    }

    fun choose(id: String, extraText: String = "") {
        val extra = extraText.trim()
        when (phase) {
            Phase.ASK -> onAskChoice(id, extra)
            else -> Unit
        }
    }

    private fun initialPhase(): Phase = when (entry.flow) {
        AnalysisFlow.STEP10, AnalysisFlow.LINEAR_NOW -> Phase.ASK
        else -> Phase.PREVIEW
    }

    private fun reshuffle() {
        if (miniN <= 0) {
            miniQuestions = emptyList()
            return
        }
        miniQuestions = entry.questions.shuffled().take(miniN.coerceAtMost(entry.questions.size))
    }

    private fun activeLinear(): List<LinearQuestion> =
        if (entry.flow == AnalysisFlow.MINI) miniQuestions else entry.questions

    private fun previewScreen(): SessionScreen {
        val blocks = when (entry.flow) {
            AnalysisFlow.MINI -> if (miniQuestions.isEmpty()) {
                emptyList()
            } else {
                listOf(PreviewBlock(null, numberedTree(miniQuestions)))
            }
            AnalysisFlow.LINEAR_PREVIEW, AnalysisFlow.LINEAR_NOW -> listOf(
                PreviewBlock(entry.title, numberedTree(entry.questions))
            )
            AnalysisFlow.BRANCHED -> buildList {
                add(PreviewBlock(COMMON_HEADING, numberedTree(entry.questions)))
                entry.branches.forEach { branch ->
                    add(PreviewBlock(branch.title, numbered(branch.questions)))
                }
            }
            AnalysisFlow.CLEAN_DAY -> entry.items.mapIndexed { i, item ->
                PreviewBlock(
                    "${i + 1}. ${item.title}",
                    listOf(item.question, "• ${item.ifYes.label}", "• ${item.ifNo.label}")
                )
            }
            else -> emptyList()
        }
        val primary = if (entry.flow == AnalysisFlow.MINI) ANSWER_CTA else START_CTA
        return SessionScreen.Preview(
            title = title,
            blocks = blocks,
            primaryLabel = primary,
            showReroll = false,
            countOptions = if (entry.flow == AnalysisFlow.MINI) MINI_COUNTS else emptyList(),
            selectedCount = if (entry.flow == AnalysisFlow.MINI && miniN > 0) miniN else null,
            canBegin = entry.flow != AnalysisFlow.MINI || miniQuestions.isNotEmpty()
        )
    }

    private fun askScreen(): SessionScreen {
        if (entry.flow == AnalysisFlow.CLEAN_DAY) {
            val item = entry.items[itemIndex]
            val total = entry.items.size * 3
            val current = itemIndex * 3 + 1
            return SessionScreen.Question(
                title = title,
                prayer = null,
                question = item.question,
                choices = listOf(
                    Choice(YES_ID, item.ifYes.label),
                    Choice(NO_ID, item.ifNo.label)
                ),
                allowText = true,
                hideSend = false,
                progressIndex = current,
                progressTotal = total
            )
        }
        val q = currentAsk() ?: return finishScreen()
        val lastBranched = isBranchPicker()
        val choices = if (lastBranched) {
            entry.branches.map { Choice(it.id, it.title) }
        } else {
            resolvedChoices(q)
        }
        return SessionScreen.Question(
            title = title,
            prayer = q.prayer,
            question = q.text,
            choices = choices,
            allowText = if (lastBranched) true else q.allowText,
            hideSend = lastBranched || (choices.isNotEmpty() && !q.allowText),
            progressIndex = progressIndex(),
            progressTotal = progressTotal()
        )
    }

    private fun branchScreen(): SessionScreen {
        val branch = selectedBranch ?: return finishScreen()
        val q = branch.questions[branchIndex]
        return SessionScreen.Question(
            title = title,
            prayer = null,
            question = q,
            choices = emptyList(),
            allowText = true,
            hideSend = false,
            progressIndex = progressIndex(),
            progressTotal = progressTotal()
        )
    }

    private fun followScreen(): SessionScreen {
        val q = cleanFollows[followIndex]
        return SessionScreen.Question(
            title = title,
            prayer = null,
            question = q,
            choices = emptyList(),
            allowText = true,
            hideSend = false,
            progressIndex = itemIndex * 3 + 2 + followIndex,
            progressTotal = entry.items.size * 3
        )
    }

    private fun onAskText(text: String) {
        if (entry.flow == AnalysisFlow.CLEAN_DAY) {
            val item = entry.items[itemIndex]
            when {
                isYes(text) || text.equals(item.ifYes.label, ignoreCase = true) ->
                    pickClean(yes = true)
                isNo(text) || text.equals(item.ifNo.label, ignoreCase = true) ->
                    pickClean(yes = false)
                else -> Unit
            }
            return
        }
        if (isBranchPicker()) return
        val q = currentAsk() ?: return
        val matched = resolvedChoices(q).find { it.label.equals(text, ignoreCase = true) }
        applyAnswer(q, text, matched?.id)
    }

    private fun onAskChoice(id: String, extra: String) {
        if (entry.flow == AnalysisFlow.CLEAN_DAY) {
            if (id == YES_ID) pickClean(true) else if (id == NO_ID) pickClean(false)
            return
        }
        if (isBranchPicker()) {
            val branch = entry.branches.find { it.id == id } ?: return
            val q = currentAsk() ?: return
            record(q.text, extra.ifBlank { branch.title })
            selectedBranch = branch
            branchIndex = 0
            phase = if (branch.questions.isEmpty()) Phase.DONE else Phase.BRANCH
            return
        }
        val q = currentAsk() ?: return
        val choice = resolvedChoices(q).find { it.id == id } ?: return
        applyAnswer(q, extra.ifBlank { choice.label }, id)
    }

    private fun applyAnswer(q: LinearQuestion, answer: String, choiceId: String?) {
        record(q.text, answer)
        if (choiceId != null && choiceId in q.endOnChoiceIds) {
            followStack.clear()
            phase = Phase.DONE
            return
        }
        val skip = when {
            choiceId != null && q.skipNextByChoiceId.containsKey(choiceId) ->
                q.skipNextByChoiceId.getValue(choiceId).coerceAtLeast(0)
            isNo(answer) && q.skipNextOnNo > 0 -> q.skipNextOnNo
            else -> 0
        }
        val follow = choiceId?.let { q.followUps[it] }.orEmpty()
        consumeCurrent(skip, follow)
    }

    private fun pickClean(yes: Boolean) {
        val item = entry.items[itemIndex]
        val side = if (yes) item.ifYes else item.ifNo
        record(item.question, side.label)
        cleanFollows = side.questions
        followIndex = 0
        if (cleanFollows.isEmpty()) nextCleanItem() else phase = Phase.CLEAN_FOLLOW
    }

    private fun onBranchText(text: String) {
        val branch = selectedBranch ?: return
        record(branch.questions[branchIndex], text)
        branchIndex++
        if (branchIndex >= branch.questions.size) phase = Phase.DONE
    }

    private fun onFollowText(text: String) {
        record(cleanFollows[followIndex], text)
        followIndex++
        if (followIndex >= cleanFollows.size) nextCleanItem()
    }

    private fun nextCleanItem() {
        itemIndex++
        cleanFollows = emptyList()
        followIndex = 0
        phase = if (itemIndex >= entry.items.size) Phase.DONE else Phase.ASK
    }

    private fun record(question: String, answer: String) {
        checkpoints += captureWithoutCheckpoints()
        answers += QaPair(question, answer)
    }

    private fun rewindTo(answerIndex: Int): Boolean {
        if (answerIndex !in checkpoints.indices) return false
        if (!restoreStateOnly(checkpoints[answerIndex])) return false
        while (checkpoints.size > answerIndex) checkpoints.removeLast()
        return true
    }

    private fun captureWithoutCheckpoints(): JSONObject {
        val follow = JSONArray()
        followStack.forEach { frame ->
            follow.put(
                JSONObject()
                    .put("ids", JSONArray().also { ids ->
                        frame.questions.forEach { q -> ids.put(q.id) }
                    })
                    .put("index", frame.index)
            )
        }
        return JSONObject()
            .put("phase", phase.name)
            .put("index", index)
            .put("answers", JSONArray(AnalysisAnswers.encode(answers)))
            .put("mini_n", miniN)
            .put("mini_ids", JSONArray().also { arr ->
                miniQuestions.forEach { arr.put(it.id) }
            })
            .put("branch_id", selectedBranch?.id.orEmpty())
            .put("branch_index", branchIndex)
            .put("item_index", itemIndex)
            .put("clean_follows", JSONArray().also { arr ->
                cleanFollows.forEach { arr.put(it) }
            })
            .put("follow_index", followIndex)
            .put("follow_stack", follow)
    }

    private fun restoreStateOnly(obj: JSONObject): Boolean {
        return runCatching {
            val restoredPhase = Phase.valueOf(obj.getString("phase"))
            val restoredMiniN = obj.optInt("mini_n", 0)
            val miniIds = obj.stringList("mini_ids")
            val restoredMini = if (entry.flow == AnalysisFlow.MINI) {
                if (restoredMiniN > 0 && miniIds.isEmpty()) return false
                miniIds.map { id ->
                    entry.questions.find { it.id == id } ?: return false
                }
            } else {
                emptyList()
            }
            val branchId = obj.optString("branch_id")
            val restoredBranch = if (branchId.isBlank()) {
                null
            } else {
                entry.branches.find { it.id == branchId } ?: return false
            }
            miniN = restoredMiniN
            miniQuestions = restoredMini
            index = obj.optInt("index")
            answers.clear()
            answers += AnalysisAnswers.decode(obj.optJSONArray("answers")?.toString().orEmpty())
            selectedBranch = restoredBranch
            branchIndex = obj.optInt("branch_index")
            itemIndex = obj.optInt("item_index")
            cleanFollows = obj.stringList("clean_follows")
            followIndex = obj.optInt("follow_index")
            followStack.clear()
            obj.optJSONArray("follow_stack")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val frame = arr.getJSONObject(i)
                    val ids = frame.stringList("ids")
                    val qs = ids.map { id -> questionById(id) ?: return false }
                    followStack.addLast(Frame(qs, frame.optInt("index")))
                }
            }
            phase = restoredPhase
            true
        }.getOrDefault(false)
    }

    /** Old sessions without checkpoints: editing disabled until new answers are given. */
    private fun rebuildCheckpointsFallback() {
        checkpoints.clear()
    }

    private fun finishScreen(): SessionScreen.Done {
        phase = Phase.DONE
        return SessionScreen.Done(title, answers.toList())
    }

    private fun currentAsk(): LinearQuestion? {
        pruneFollow()
        if (followStack.isNotEmpty()) {
            val f = followStack.last()
            return f.questions.getOrNull(f.index)
        }
        return activeLinear().getOrNull(index)
    }

    private fun questionById(id: String): LinearQuestion? {
        fun walk(list: List<LinearQuestion>): LinearQuestion? {
            for (q in list) {
                if (q.id == id) return q
                for (kids in q.followUps.values) {
                    walk(kids)?.let { return it }
                }
            }
            return null
        }
        return walk(miniQuestions) ?: walk(entry.questions)
    }

    private fun isBranchPicker(): Boolean {
        if (entry.flow != AnalysisFlow.BRANCHED) return false
        if (followStack.isNotEmpty()) return false
        val list = activeLinear()
        return list.isNotEmpty() && index == list.lastIndex
    }

    private fun consumeCurrent(skip: Int, follow: List<LinearQuestion>) {
        if (followStack.isNotEmpty()) {
            followStack.last().index += 1 + skip
        } else {
            index += 1 + skip
        }
        if (follow.isNotEmpty()) followStack.addLast(Frame(follow, 0))
        pruneFollow()
        if (currentAsk() == null) phase = Phase.DONE
    }

    private fun pruneFollow() {
        while (followStack.isNotEmpty()) {
            val f = followStack.last()
            if (f.index in f.questions.indices) return
            followStack.removeLast()
        }
    }

    private fun resolvedChoices(q: LinearQuestion): List<Choice> = when (q.buttons) {
        QuestionButtons.NONE -> emptyList()
        QuestionButtons.LIST -> q.choices
        QuestionButtons.AUTO -> if (showYesNo(q)) {
            listOf(Choice(YES_ID, YES_LABEL), Choice(NO_ID, NO_LABEL))
        } else {
            emptyList()
        }
    }

    private fun showYesNo(q: LinearQuestion): Boolean {
        if (entry.flow == AnalysisFlow.LINEAR_PREVIEW) return false
        if (entry.flow == AnalysisFlow.CLEAN_DAY) return false
        if (entry.flow == AnalysisFlow.BRANCHED) return false
        if (q.skipNextOnNo > 0) return true
        return LI_PARTICLE.containsMatchIn(q.text)
    }

    private fun progressIndex(): Int = answers.size + 1

    private fun progressTotal(): Int {
        return when (entry.flow) {
            AnalysisFlow.MINI -> countTree(miniQuestions).coerceAtLeast(miniQuestions.size)
            AnalysisFlow.STEP10, AnalysisFlow.LINEAR_NOW, AnalysisFlow.LINEAR_PREVIEW ->
                countTree(activeLinear()).coerceAtLeast(activeLinear().size)
            AnalysisFlow.BRANCHED -> {
                val extra = selectedBranch?.questions?.size
                    ?: entry.branches.maxOfOrNull { it.questions.size }.orZero()
                countTree(entry.questions) + extra
            }
            AnalysisFlow.CLEAN_DAY -> entry.items.size * 3
        }
    }

    private fun numbered(items: List<String>): List<String> =
        items.mapIndexed { i, text -> "${i + 1}. $text" }

    private fun numberedTree(qs: List<LinearQuestion>, prefix: String = ""): List<String> {
        val out = mutableListOf<String>()
        qs.forEachIndexed { i, q ->
            val n = "$prefix${i + 1}"
            out += "$n. ${q.text}"
            q.choices.forEach { choice ->
                val kids = q.followUps[choice.id].orEmpty()
                if (kids.isNotEmpty()) {
                    out += "    • ${choice.label}:"
                    out += numberedTree(kids, "$n.")
                }
            }
        }
        return out
    }

    private fun countTree(qs: List<LinearQuestion>): Int =
        qs.sumOf { 1 + it.followUps.values.sumOf { kids -> countTree(kids) } }

    private class Frame(val questions: List<LinearQuestion>, var index: Int)

    private enum class Phase { PREVIEW, ASK, BRANCH, CLEAN_FOLLOW, DONE }

    companion object {
        const val YES_ID = "yes"
        const val NO_ID = "no"
        const val YES_LABEL = "\u0414\u0430"
        const val NO_LABEL = "\u041d\u0435\u0442"
        const val START_CTA = "\u041d\u0430\u0447\u0430\u0442\u044c"
        const val ANSWER_CTA = "\u041e\u0442\u0432\u0435\u0442\u0438\u0442\u044c"
        const val COMMON_HEADING = "\u041e\u0431\u0449\u0438\u0435 \u0432\u043e\u043f\u0440\u043e\u0441\u044b"
        const val CLEAN_DAY_TITLE = "\u0427\u0438\u0441\u0442\u044b\u0439 \u0434\u0435\u043d\u044c"
        val MINI_COUNTS = listOf(1, 3, 5, 7)

        private val LI_PARTICLE = Regex("(?iu)(^|[^\\p{L}])\u043b\u0438([^\\p{L}]|$)")

        fun allowedMini(count: Int): Int =
            if (count in MINI_COUNTS) count else 0

        fun isNo(text: String): Boolean =
            text.trim().equals(NO_LABEL, ignoreCase = true)

        fun isYes(text: String): Boolean =
            text.trim().equals(YES_LABEL, ignoreCase = true)
    }
}

private fun Int?.orZero(): Int = this ?: 0

private fun JSONObject.stringList(key: String): List<String> =
    optJSONArray(key).stringList()

private fun JSONArray?.stringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).map { optString(it) }
}
