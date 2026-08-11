# -*- coding: utf-8 -*-
from pathlib import Path

ROOT = Path(r"d:/sites/step4obidy/app/src/main/java/ru/na/step4/obidy")


def w(rel, content):
    (ROOT / rel).write_text(content, encoding="utf-8", newline="\n")
    print("wrote", rel)


# Fix migration: create links AFTER rename
db = (ROOT / "data/AppDatabase.kt").read_text(encoding="utf-8")
# Will rewrite migration block after gen_m2m_data2 runs

w(
    "data/ResentmentRepository.kt",
    r'''package ru.na.step4.obidy.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

data class TypeWithSituations(
    val type: SituationType,
    val situations: List<Situation>
)

data class SituationWithTypes(
    val situation: Situation,
    val types: List<SituationType>
)

data class ResentmentListItem(
    val resentment: Resentment,
    val preview: String,
    val progress: Int,
    val totalSteps: Int,
    val typeCount: Int,
    val situationCount: Int
)

class ResentmentRepository(
    private val resentmentDao: ResentmentDao,
    private val categoryDao: CategoryDao,
    private val situationDao: SituationDao
) {
    fun observeAll(): Flow<List<Resentment>> = resentmentDao.observeAll()

    fun observeByCategory(categoryId: Long): Flow<List<Resentment>> =
        resentmentDao.observeByCategory(categoryId)

    fun observeUncategorized(): Flow<List<Resentment>> = resentmentDao.observeUncategorized()
    fun observeById(id: Long): Flow<Resentment?> = resentmentDao.observeById(id)
    fun observeCount(): Flow<Int> = resentmentDao.observeCount()
    fun observeCompletedCount(): Flow<Int> = resentmentDao.observeCompletedCount()

    fun observeCountByCategory(categoryId: Long): Flow<Int> =
        resentmentDao.observeCountByCategory(categoryId)

    fun observeCompletedCountByCategory(categoryId: Long): Flow<Int> =
        resentmentDao.observeCompletedCountByCategory(categoryId)

    fun observeUncategorizedCount(): Flow<Int> = resentmentDao.observeUncategorizedCount()
    fun observeUncategorizedCompletedCount(): Flow<Int> =
        resentmentDao.observeUncategorizedCompletedCount()

    fun observeCategories(): Flow<List<Category>> = categoryDao.observeAll()

    suspend fun getById(id: Long): Resentment? = resentmentDao.getById(id)
    suspend fun getCategories(): List<Category> = categoryDao.getAll()

    suspend fun save(item: Resentment): Long {
        val now = System.currentTimeMillis()
        return if (item.id == 0L) {
            resentmentDao.insert(item.copy(createdAt = now, updatedAt = now))
        } else {
            resentmentDao.update(item.copy(updatedAt = now))
            item.id
        }
    }

    suspend fun delete(item: Resentment) = resentmentDao.delete(item)

    fun observeTypes(resentmentId: Long): Flow<List<SituationType>> =
        situationDao.observeTypes(resentmentId)

    fun observeSituationsForResentment(resentmentId: Long): Flow<List<Situation>> =
        situationDao.observeSituationsForResentment(resentmentId)

    fun observeTypesForSituation(situationId: Long): Flow<List<SituationType>> =
        situationDao.observeTypesForSituation(situationId)

    fun observeTree(resentmentId: Long): Flow<List<TypeWithSituations>> =
        combine(
            situationDao.observeTypes(resentmentId),
            situationDao.observeSituationsForResentment(resentmentId),
            situationDao.observeLinksForResentment(resentmentId)
        ) { types, situations, links ->
            val byType = links.groupBy({ it.typeId }, { it.situationId })
            types.map { type ->
                val ids = byType[type.id].orEmpty().toSet()
                TypeWithSituations(
                    type = type,
                    situations = situations.filter { it.id in ids }
                )
            }
        }

    fun observeSituationsWithTypes(resentmentId: Long): Flow<List<SituationWithTypes>> =
        combine(
            situationDao.observeSituationsForResentment(resentmentId),
            situationDao.observeTypes(resentmentId),
            situationDao.observeLinksForResentment(resentmentId)
        ) { situations, types, links ->
            val typeById = types.associateBy { it.id }
            val bySit = links.groupBy({ it.situationId }, { it.typeId })
            situations.map { sit ->
                SituationWithTypes(
                    situation = sit,
                    types = bySit[sit.id].orEmpty().mapNotNull { typeById[it] }
                        .sortedBy { it.name.lowercase() }
                )
            }
        }

    fun observeTreeRevision(): Flow<Long> =
        combine(
            situationDao.observeTypeCount(),
            situationDao.observeSituationCount(),
            situationDao.observeSituationStamp(),
            situationDao.observeLinkCount()
        ) { types, situations, stamp, links ->
            types + situations + stamp + links
        }

    suspend fun getSituation(id: Long): Situation? = situationDao.getSituation(id)

    suspend fun getTypesForSituation(situationId: Long): List<SituationType> =
        situationDao.getTypesForSituation(situationId)

    suspend fun addType(resentmentId: Long, name: String): Long {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return 0L
        val existing = situationDao.getTypes(resentmentId)
            .firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
        if (existing != null) return existing.id
        val order = situationDao.countTypes(resentmentId)
        return situationDao.insertType(
            SituationType(resentmentId = resentmentId, name = trimmed, sortOrder = order)
        )
    }

    suspend fun deleteType(type: SituationType) = situationDao.deleteType(type)

    suspend fun addSituation(resentmentId: Long, whatHappened: String = "", title: String = ""): Long {
        val order = situationDao.countSituationsForResentment(resentmentId)
        return situationDao.insertSituation(
            Situation(
                resentmentId = resentmentId,
                title = title.trim(),
                whatHappened = whatHappened.trim(),
                sortOrder = order
            )
        )
    }

    suspend fun saveSituation(item: Situation): Long {
        val now = System.currentTimeMillis()
        return if (item.id == 0L) {
            situationDao.insertSituation(item.copy(updatedAt = now))
        } else {
            situationDao.updateSituation(item.copy(updatedAt = now))
            item.id
        }
    }

    suspend fun deleteSituation(item: Situation) = situationDao.deleteSituation(item)

    suspend fun setSituationTypes(situationId: Long, typeIds: Collection<Long>) {
        situationDao.clearLinksForSituation(situationId)
        val unique = typeIds.distinct().filter { it > 0 }
        if (unique.isNotEmpty()) {
            situationDao.insertLinks(unique.map { SituationTypeLink(situationId, it) })
        }
        // bump updatedAt so list revision refreshes
        situationDao.getSituation(situationId)?.let { saveSituation(it) }
    }

    suspend fun linkSituationToType(situationId: Long, typeId: Long) {
        if (situationId > 0 && typeId > 0) {
            situationDao.insertLink(SituationTypeLink(situationId, typeId))
            situationDao.getSituation(situationId)?.let { saveSituation(it) }
        }
    }

    suspend fun applyTypeSelection(
        resentmentId: Long,
        situationId: Long,
        selectedExistingIds: Set<Long>,
        selectedProposedNames: Set<String>
    ) {
        val typeIds = selectedExistingIds.toMutableSet()
        selectedProposedNames.forEach { name ->
            val id = addType(resentmentId, name)
            if (id > 0) typeIds.add(id)
        }
        setSituationTypes(situationId, typeIds)
    }

    fun suggestTypes(text: String, existing: List<SituationType>): TypeSuggestEngine.Result =
        TypeSuggestEngine.suggest(text, existing)

    suspend fun saveCategory(item: Category): Long {
        return if (item.id == 0L) {
            val order = categoryDao.count()
            categoryDao.insert(item.copy(sortOrder = order, createdAt = System.currentTimeMillis()))
        } else {
            categoryDao.update(item)
            item.id
        }
    }

    suspend fun deleteCategory(item: Category) {
        resentmentDao.clearCategory(item.id)
        categoryDao.delete(item)
    }

    suspend fun ensureDefaultCategories() {
        if (categoryDao.count() > 0) return
        val now = System.currentTimeMillis()
        DefaultCategories.names.forEachIndexed { index, name ->
            categoryDao.insert(Category(name = name, sortOrder = index, createdAt = now))
        }
    }

    suspend fun listPreview(item: Resentment): ResentmentListItem {
        val types = situationDao.getTypes(item.id)
        val situations = situationDao.getSituationsForResentment(item.id)
        val preview = situations.firstOrNull()?.preview
            ?: item.whatHappened.ifBlank { item.cause }.ifBlank {
                if (types.isEmpty()) "" else types.joinToString(" · ") { it.name }
            }
        val progress = (if (item.target.isNotBlank()) 1 else 0) +
            situations.sumOf { it.progressSteps }
        val total = 1 + situations.size.coerceAtLeast(1) * Situation.TOTAL_STEPS
        return ResentmentListItem(
            resentment = item,
            preview = preview,
            progress = progress,
            totalSteps = total,
            typeCount = types.size,
            situationCount = situations.size
        )
    }
}

object DefaultCategories {
    val names = InventoryStructure.defaultCategoryNames
}
''',
)

print("repo ok")
