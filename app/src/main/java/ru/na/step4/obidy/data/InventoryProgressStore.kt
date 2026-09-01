package ru.na.step4.obidy.data

import android.content.Context

/** Last place in resentment inventory (4 step · Обиды по IP). */
class InventoryProgressStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var lastResentmentId: Long
        get() = prefs.getLong(KEY_RESENTMENT, 0L)
        private set(value) {
            prefs.edit().putLong(KEY_RESENTMENT, value).apply()
        }

    var lastSituationId: Long
        get() = prefs.getLong(KEY_SITUATION, 0L)
        private set(value) {
            prefs.edit().putLong(KEY_SITUATION, value).apply()
        }

    fun markResentment(id: Long) {
        if (id <= 0L) return
        lastResentmentId = id
        lastSituationId = 0L
    }

    fun markSituation(situationId: Long, resentmentId: Long) {
        if (situationId <= 0L || resentmentId <= 0L) return
        lastSituationId = situationId
        lastResentmentId = resentmentId
    }

    suspend fun resumeRoute(repository: ResentmentRepository): String? {
        val situationId = lastSituationId
        if (situationId > 0L) {
            repository.getSituation(situationId)?.let { return "situation/${it.id}" }
        }
        val resentmentId = lastResentmentId
        if (resentmentId > 0L) {
            repository.getById(resentmentId)?.let { return "edit/${it.id}" }
        }
        return null
    }

    companion object {
        private const val PREFS = "inventory_progress"
        private const val KEY_RESENTMENT = "last_resentment"
        private const val KEY_SITUATION = "last_situation"
    }
}
