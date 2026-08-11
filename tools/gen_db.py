# -*- coding: utf-8 -*-
from pathlib import Path

ROOT = Path(r"d:/sites/step4obidy/app/src/main/java/ru/na/step4/obidy")
MOD = chr(77) + "odifier"


def write(rel: str, content: str):
    path = ROOT / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    content = content.replace("UI_MODIFIER", MOD)
    path.write_text(content.replace("\n", "\r\n"), encoding="utf-8")
    print("wrote", rel)


GENERAL = "\\u041e\\u0431\\u0449\\u0430\\u044f"  # Общая

write(
    "data/AppDatabase.kt",
    f'''package ru.na.step4.obidy.data

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
abstract class AppDatabase : RoomDatabase() {{
    abstract fun resentmentDao(): ResentmentDao
    abstract fun categoryDao(): CategoryDao
    abstract fun situationDao(): SituationDao

    companion object {{
        @Volatile
        private var instance: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {{
            override fun migrate(db: SupportSQLiteDatabase) {{
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
            }}
        }}

        private val MIGRATION_2_3 = object : Migration(2, 3) {{
            override fun migrate(db: SupportSQLiteDatabase) {{
                val cols = listOf(
                    "whatHappened", "iFelt", "iDid",
                    "q1", "q2", "q3", "q4", "q5", "q6", "q7",
                    "q8", "q9", "q10", "q11", "q12", "q13"
                )
                cols.forEach {{ name ->
                    db.execSQL(
                        "ALTER TABLE resentments ADD COLUMN $name TEXT NOT NULL DEFAULT ''"
                    )
                }}
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
            }}
        }}

        private val MIGRATION_3_4 = object : Migration(3, 4) {{
            override fun migrate(db: SupportSQLiteDatabase) {{
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
                val general = "{GENERAL}"
                while (cursor.moveToNext()) {{
                    val id = cursor.getLong(0)
                    fun col(i: Int): String = cursor.getString(i) ?: ""
                    val what = col(1).ifBlank {{ col(4) }}
                    val felt = col(2)
                    val did = col(3).ifBlank {{ col(5) }}
                    val qs = (6..18).map {{ col(it) }}
                    val has = listOf(what, felt, did).any {{ it.isNotBlank() }} ||
                        qs.any {{ it.isNotBlank() }}
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
                }}
                cursor.close()
            }}
        }}

        fun get(context: Context): AppDatabase {{
            return instance ?: synchronized(this) {{
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "step4_obidy.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also {{ instance = it }}
            }}
        }}
    }}
}}
''',
)

print("AppDatabase ok")
