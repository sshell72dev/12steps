# -*- coding: utf-8 -*-
"""Wire branched situation structure into the app."""
from pathlib import Path

ROOT = Path(r"d:/sites/step4obidy/app/src/main/java/ru/na/step4/obidy")
M = "Modifier"  # will fix via chr
MOD = chr(77) + "odifier"


def u(s: str) -> str:
    out = []
    for c in s:
        if ord(c) < 128 and c not in '"\\':
            out.append(c)
        else:
            out.append(f"\\u{ord(c):04x}")
    return "".join(out)


def write(rel: str, content: str):
    path = ROOT / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    # ensure Modifier spelling
    content = content.replace("UI_MODIFIER", MOD)
    path.write_text(content.replace("\n", "\r\n"), encoding="utf-8")
    print("wrote", path)


# --- AppDatabase ---
write(
    "data/AppDatabase.kt",
    r'''package ru.na.step4.obidy.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Resentment::class, Category::class, SituationType::class, Situation::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun resentmentDao(): ResentmentDao
    abstract fun categoryDao(): CategoryDao
    abstract fun situationDao(): SituationDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS categories (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        sortOrder INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("ALTER TABLE resentments ADD COLUMN categoryId INTEGER")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val cols = listOf(
                    "whatHappened", "iFelt", "iDid",
                    "q1", "q2", "q3", "q4", "q5", "q6", "q7",
                    "q8", "q9", "q10", "q11", "q12", "q13"
                )
                cols.forEach { name ->
                    db.execSQL(
                        "ALTER TABLE resentments ADD COLUMN $name TEXT NOT NULL DEFAULT ''"
                    )
                }
                db.execSQL(
                    """
                    UPDATE resentments SET whatHappened = cause
                    WHERE (whatHappened IS NULL OR whatHappened = '')
                      AND cause IS NOT NULL AND cause != ''
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    UPDATE resentments SET iDid = myPart
                    WHERE (iDid IS NULL OR iDid = '')
                      AND myPart IS NOT NULL AND myPart != ''
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS situation_types (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        resentmentId INTEGER NOT NULL,
                        name TEXT NOT NULL,
                        sortOrder INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY(resentmentId) REFERENCES resentments(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_situation_types_resentmentId ON situation_types(resentmentId)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS situations (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        typeId INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        whatHappened TEXT NOT NULL,
                        iFelt TEXT NOT NULL,
                        iDid TEXT NOT NULL,
                        q1 TEXT NOT NULL, q2 TEXT NOT NULL, q3 TEXT NOT NULL, q4 TEXT NOT NULL,
                        q5 TEXT NOT NULL, q6 TEXT NOT NULL, q7 TEXT NOT NULL, q8 TEXT NOT NULL,
                        q9 TEXT NOT NULL, q10 TEXT NOT NULL, q11 TEXT NOT NULL, q12 TEXT NOT NULL,
                        q13 TEXT NOT NULL,
                        sortOrder INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(typeId) REFERENCES situation_types(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_situations_typeId ON situations(typeId)"
                )

                val cursor = db.query(
                    """
                    SELECT id, whatHappened, iFelt, iDid, cause, myPart,
                           q1,q2,q3,q4,q5,q6,q7,q8,q9,q10,q11,q12,q13
                    FROM resentments
                    """.trimIndent()
                )
                val now = System.currentTimeMillis()
                val general = "'''
    + u("Общая")
    + r'''"
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    fun col(i: Int) = cursor.getString(i) ?: ""
                    val what = col(1).ifBlank { col(4) }
                    val felt = col(2)
                    val did = col(3).ifBlank { col(5) }
                    val qs = (6..18).map { col(it) }
                    val has = listOf(what, felt, did).any { it.isNotBlank() } || qs.any { it.isNotBlank() }
                    if (!has) continue
                    db.execSQL(
                        """
                        INSERT INTO situation_types (resentmentId, name, sortOrder, createdAt)
                        VALUES (?, ?, 0, ?)
                        """.trimIndent(),
                        arrayOf<Any>(id, general, now)
                    )
                    val typeCursor = db.query("SELECT last_insert_rowid()")
                    typeCursor.moveToFirst()
                    val typeId = typeCursor.getLong(0)
                    typeCursor.close()
                    db.execSQL(
                        """
                        INSERT INTO situations (
                            typeId, title, whatHappened, iFelt, iDid,
                            q1,q2,q3,q4,q5,q6,q7,q8,q9,q10,q11,q12,q13,
                            sortOrder, updatedAt
                        ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0,?)
                        """.trimIndent(),
                        arrayOf<Any>(
                            typeId, "", what, felt, did,
                            qs[0], qs[1], qs[2], qs[3], qs[4], qs[5], qs[6],
                            qs[7], qs[8], qs[9], qs[10], qs[11], qs[12],
                            now
                        )
                    )
                }
                cursor.close()
            }
        }

        fun get(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "step4_obidy.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
''',
)

# --- Repository ---
write(
    "data/ResentmentRepository.kt",
    r'''package ru.na.step4.obidy.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

data class TypeWithSituations(
    val type: SituationType,
    val situations: List<Situation>
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

    fun observeSituations(typeId: Long): Flow<List<Situation>> =
        situationDao.observeSituations(typeId)

    fun observeSituationsForResentment(resentmentId: Long): Flow<List<Situation>> =
        situationDao.observeSituationsForResentment(resentmentId)

    fun observeTree(resentmentId: Long): Flow<List<TypeWithSituations>> =
        combine(
            situationDao.observeTypes(resentmentId),
            situationDao.observeSituationsForResentment(resentmentId)
        ) { types, situations ->
            types.map { type ->
                TypeWithSituations(
                    type = type,
                    situations = situations.filter { it.typeId == type.id }
                )
            }
        }

    suspend fun getSituation(id: Long): Situation? = situationDao.getSituation(id)

    suspend fun addType(resentmentId: Long, name: String): Long {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return 0L
        val existing = situationDao.getTypes(resentmentId).firstOrNull { it.name == trimmed }
        if (existing != null) return existing.id
        val order = situationDao.countTypes(resentmentId)
        return situationDao.insertType(
            SituationType(
                resentmentId = resentmentId,
                name = trimmed,
                sortOrder = order
            )
        )
    }

    suspend fun deleteType(type: SituationType) = situationDao.deleteType(type)

    suspend fun addSituation(typeId: Long): Long {
        val order = situationDao.countSituations(typeId)
        return situationDao.insertSituation(
            Situation(typeId = typeId, sortOrder = order)
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
            categoryDao.insert(
                Category(name = name, sortOrder = index, createdAt = now)
            )
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

write(
    "Step4App.kt",
    r'''package ru.na.step4.obidy

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.na.step4.obidy.data.AppDatabase
import ru.na.step4.obidy.data.ResentmentRepository

class Step4App : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var repository: ResentmentRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val db = AppDatabase.get(this)
        repository = ResentmentRepository(
            db.resentmentDao(),
            db.categoryDao(),
            db.situationDao()
        )
        appScope.launch {
            repository.ensureDefaultCategories()
        }
    }
}
''',
)

print("core done")
