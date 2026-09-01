package ru.na.step4.obidy.data.journal

import ru.na.step4.obidy.data.i18n.ContentI18n
import ru.na.step4.obidy.data.i18n.I18n

enum class NodeType {
    STEP,
    CHAPTER,
    POINT
}

data class TreeNode(
    val id: Int,
    val type: NodeType,
    val name: String,
    val slug: String,
    val description: String,
    val botLabel: String,
    val stepNumber: Int,
    val parentId: Int?,
    val children: List<TreeNode>
) {
    val hasChildren: Boolean get() = children.isNotEmpty()
    val isLeaf: Boolean get() = children.isEmpty()
    val canWrite: Boolean get() = isLeaf

    fun displayTitle(): String {
        val localized = ContentI18n.localizedName(this)
        return when (type) {
            NodeType.STEP -> "${stepNumber}${I18n.t("ui.stepWord", "Шаг")} $localized"
            NodeType.CHAPTER, NodeType.POINT -> localized
        }
    }

    fun shortTitle(): String {
        val localized = ContentI18n.localizedName(this)
        return when (type) {
            NodeType.STEP -> "${stepNumber}${I18n.t("ui.stepWord", "Шаг")}"
            NodeType.CHAPTER -> localized.replace(Regex("""^\d+\.\s*"""), "")
            NodeType.POINT -> localized
        }
    }

    fun localizedDescription(): String = ContentI18n.localizedDescription(this)
}

data class TreePath(
    val step: TreeNode,
    val chapter: TreeNode? = null,
    val point: TreeNode? = null
) {
    val current: TreeNode
        get() = point ?: chapter ?: step

    fun line(): String = buildString {
        append(step.displayTitle())
        if (chapter != null) {
            append(" → ")
            append(ContentI18n.localizedName(chapter))
        }
        if (point != null) {
            append(" → ")
            append(ContentI18n.localizedName(point))
        }
    }
}

data class JournalEntry(
    val id: String,
    val nodeId: Int,
    val text: String,
    val createdAt: Long,
    val updatedAt: Long = createdAt
)

typealias ProblemOption = ru.na.step4.obidy.data.profile.ProblemOption
typealias QuestionnaireQuestion = ru.na.step4.obidy.data.profile.QuestionnaireQuestion

object JournalProblems {
    val all get() = ru.na.step4.obidy.data.profile.ProfileProblems.all
}

object JournalQuestionnaire {
    val questions get() = ru.na.step4.obidy.data.profile.ProfileQuestionnaire.questions
}
