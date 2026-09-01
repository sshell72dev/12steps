package ru.na.step4.obidy.data.journal

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object TreeCatalog {
    const val ASSET_FILE = "categories-tree.json"
    const val RESENTMENT_CHAPTER_SLUG = "7-obidy"

    @Volatile
    private var cached: Catalog? = null

    data class Catalog(
        val steps: List<TreeNode>,
        val byId: Map<Int, TreeNode>
    ) {
        fun node(id: Int): TreeNode? = byId[id]

        fun pathOf(id: Int): TreePath? {
            val node = byId[id] ?: return null
            return when (node.type) {
                NodeType.STEP -> TreePath(step = node)
                NodeType.CHAPTER -> {
                    val step = node.parentId?.let { byId[it] } ?: return null
                    TreePath(step = step, chapter = node)
                }
                NodeType.POINT -> {
                    val chapter = node.parentId?.let { byId[it] } ?: return null
                    val step = chapter.parentId?.let { byId[it] } ?: return null
                    TreePath(step = step, chapter = chapter, point = node)
                }
            }
        }

        fun nextPoint(currentId: Int): TreeNode? {
            val current = byId[currentId] ?: return null
            if (current.type != NodeType.POINT) return null
            val chapter = current.parentId?.let { byId[it] } ?: return null
            val index = chapter.children.indexOfFirst { it.id == current.id }
            if (index >= 0 && index + 1 < chapter.children.size) {
                return chapter.children[index + 1]
            }
            val step = chapter.parentId?.let { byId[it] } ?: return null
            val chapterIndex = step.children.indexOfFirst { it.id == chapter.id }
            if (chapterIndex < 0) return null
            for (i in chapterIndex + 1 until step.children.size) {
                val first = step.children[i].children.firstOrNull()
                if (first != null) return first
            }
            return null
        }

        fun isResentmentChapter(node: TreeNode?): Boolean =
            node?.type == NodeType.CHAPTER && node.slug == RESENTMENT_CHAPTER_SLUG

        fun isResentmentPlace(path: TreePath?): Boolean =
            isResentmentChapter(path?.chapter)

        fun resentmentChapter(): TreeNode? =
            byId.values.find { isResentmentChapter(it) }

        fun countInSubtree(nodeId: Int, entries: List<JournalEntry>): Int {
            val ids = descendantIds(nodeId)
            return entries.count { it.nodeId in ids }
        }

        fun descendantIds(nodeId: Int): Set<Int> {
            val root = byId[nodeId] ?: return emptySet()
            val out = LinkedHashSet<Int>()
            fun walk(node: TreeNode) {
                out += node.id
                node.children.forEach(::walk)
            }
            walk(root)
            return out
        }

        fun nodesWithEntries(entries: List<JournalEntry>): List<TreeNode> {
            val ids = entries.map { it.nodeId }.toSet()
            return ids.mapNotNull { byId[it] }
        }
    }

    fun load(context: Context): Catalog {
        cached?.let { return it }
        val json = context.applicationContext.assets
            .open(ASSET_FILE)
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        return parse(json).also { cached = it }
    }

    fun parse(json: String): Catalog {
        val root = JSONObject(json)
        val arr = root.getJSONArray("steps")
        val steps = ArrayList<TreeNode>(arr.length())
        val index = LinkedHashMap<Int, TreeNode>()
        for (i in 0 until arr.length()) {
            val step = parseStep(arr.getJSONObject(i), i + 1)
            steps += step
            indexNode(step, index)
        }
        return Catalog(steps, index)
    }

    private fun indexNode(node: TreeNode, into: MutableMap<Int, TreeNode>) {
        into[node.id] = node
        node.children.forEach { indexNode(it, into) }
    }

    private fun parseStep(obj: JSONObject, number: Int): TreeNode {
        val id = obj.getInt("id")
        val chaptersArr = obj.optJSONArray("chapters") ?: JSONArray()
        val chapters = ArrayList<TreeNode>(chaptersArr.length())
        for (i in 0 until chaptersArr.length()) {
            chapters += parseChapter(chaptersArr.getJSONObject(i), id, number)
        }
        return TreeNode(
            id = id,
            type = NodeType.STEP,
            name = obj.optString("name"),
            slug = obj.optString("slug"),
            description = obj.optString("description").trim(),
            botLabel = obj.optString("bot_label"),
            stepNumber = number,
            parentId = null,
            children = chapters
        )
    }

    private fun parseChapter(obj: JSONObject, stepId: Int, stepNumber: Int): TreeNode {
        val id = obj.getInt("id")
        val pointsArr = obj.optJSONArray("points") ?: JSONArray()
        val points = ArrayList<TreeNode>(pointsArr.length())
        for (i in 0 until pointsArr.length()) {
            points += parsePoint(pointsArr.getJSONObject(i), id, stepNumber)
        }
        return TreeNode(
            id = id,
            type = NodeType.CHAPTER,
            name = obj.optString("name"),
            slug = obj.optString("slug"),
            description = obj.optString("description").trim(),
            botLabel = obj.optString("bot_label"),
            stepNumber = stepNumber,
            parentId = stepId,
            children = points
        )
    }

    private fun parsePoint(obj: JSONObject, chapterId: Int, stepNumber: Int): TreeNode {
        return TreeNode(
            id = obj.getInt("id"),
            type = NodeType.POINT,
            name = obj.optString("name"),
            slug = obj.optString("slug"),
            description = obj.optString("description").trim(),
            botLabel = obj.optString("bot_label"),
            stepNumber = stepNumber,
            parentId = chapterId,
            children = emptyList()
        )
    }
}
