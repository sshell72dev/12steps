# -*- coding: utf-8 -*-
from pathlib import Path

p = Path(r"d:/sites/step4obidy/app/src/main/java/ru/na/step4/obidy/data/AppDatabase.kt")
t = p.read_text(encoding="utf-8")
start = "private val MIGRATION_4_5"
idx = t.find(start)
end = t.find("fun get(context: Context)", idx)
assert idx > 0 and end > idx, (idx, end)

mig = r'''private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS situations_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        resentmentId INTEGER NOT NULL,
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
                        FOREIGN KEY(resentmentId) REFERENCES resentments(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )

                val pairs = mutableListOf<Pair<Long, Long>>()
                val cursor = db.query(
                    """
                    SELECT s.id, t.resentmentId, s.title, s.whatHappened, s.iFelt, s.iDid,
                           s.q1,s.q2,s.q3,s.q4,s.q5,s.q6,s.q7,s.q8,s.q9,s.q10,s.q11,s.q12,s.q13,
                           s.sortOrder, s.updatedAt, s.typeId
                    FROM situations s
                    INNER JOIN situation_types t ON t.id = s.typeId
                    """.trimIndent()
                )
                while (cursor.moveToNext()) {
                    val oldId = cursor.getLong(0)
                    val resentmentId = cursor.getLong(1)
                    fun col(i: Int): String = cursor.getString(i) ?: ""
                    val sortOrder = cursor.getInt(19)
                    val updatedAt = cursor.getLong(20)
                    val typeId = cursor.getLong(21)
                    db.execSQL(
                        """
                        INSERT INTO situations_new (
                            id, resentmentId, title, whatHappened, iFelt, iDid,
                            q1,q2,q3,q4,q5,q6,q7,q8,q9,q10,q11,q12,q13,
                            sortOrder, updatedAt
                        ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                        """.trimIndent(),
                        arrayOf<Any>(
                            oldId, resentmentId, col(2), col(3), col(4), col(5),
                            col(6), col(7), col(8), col(9), col(10), col(11), col(12),
                            col(13), col(14), col(15), col(16), col(17), col(18),
                            sortOrder, updatedAt
                        )
                    )
                    pairs.add(oldId to typeId)
                }
                cursor.close()

                db.execSQL("DROP TABLE situations")
                db.execSQL("ALTER TABLE situations_new RENAME TO situations")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_situations_resentmentId ON situations(resentmentId)"
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS situation_type_links (
                        situationId INTEGER NOT NULL,
                        typeId INTEGER NOT NULL,
                        PRIMARY KEY(situationId, typeId),
                        FOREIGN KEY(situationId) REFERENCES situations(id) ON DELETE CASCADE,
                        FOREIGN KEY(typeId) REFERENCES situation_types(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_situation_type_links_situationId ON situation_type_links(situationId)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_situation_type_links_typeId ON situation_type_links(typeId)"
                )
                pairs.forEach { (situationId, typeId) ->
                    db.execSQL(
                        "INSERT OR IGNORE INTO situation_type_links (situationId, typeId) VALUES (?, ?)",
                        arrayOf<Any>(situationId, typeId)
                    )
                }
            }
        }

        '''

p.write_text(t[:idx] + mig + t[end:], encoding="utf-8", newline="\n")
print("migration fixed")
