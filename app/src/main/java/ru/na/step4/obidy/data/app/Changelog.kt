package ru.na.step4.obidy.data.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ReleaseNote(
    val version: String,
    val versionCode: Int,
    val date: String,
    val items: List<String>
)

object Changelog {
    private const val ASSET = "changelog.json"

    fun load(context: Context): List<ReleaseNote> = runCatching {
        context.assets.open(ASSET).bufferedReader().use { reader ->
            parse(JSONObject(reader.readText()))
        }
    }.getOrElse { emptyList() }

    private fun parse(root: JSONObject): List<ReleaseNote> {
        val releases = root.optJSONArray("releases") ?: JSONArray()
        return buildList {
            for (i in 0 until releases.length()) {
                val obj = releases.getJSONObject(i)
                val itemsArr = obj.optJSONArray("items") ?: JSONArray()
                val items = buildList {
                    for (j in 0 until itemsArr.length()) {
                        add(itemsArr.getString(j))
                    }
                }
                add(
                    ReleaseNote(
                        version = obj.optString("version"),
                        versionCode = obj.optInt("versionCode"),
                        date = obj.optString("date"),
                        items = items
                    )
                )
            }
        }
    }
}
