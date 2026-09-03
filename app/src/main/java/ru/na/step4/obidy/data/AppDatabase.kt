package ru.na.step4.obidy.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import ru.na.step4.obidy.data.analysis.AnalysisDao
import ru.na.step4.obidy.data.analysis.AnalysisRecord
import ru.na.step4.obidy.data.psych.PsychAiCache
import ru.na.step4.obidy.data.psych.PsychAiUsage
import ru.na.step4.obidy.data.psych.PsychAnswer
import ru.na.step4.obidy.data.psych.PsychDao
import ru.na.step4.obidy.data.psych.PsychSession
import ru.na.step4.obidy.data.psych.PsychSituation
import ru.na.step4.obidy.data.psych.PsychSituationTopic
import ru.na.step4.obidy.data.psych.PsychTopic

@Database(
    entities = [
        Resentment::class,
        Category::class,
        SituationType::class,
        Situation::class,
        SituationTypeLink::class,
        AnalysisRecord::class,
        PsychSituation::class,
        PsychSession::class,
        PsychAnswer::class,
        PsychTopic::class,
        PsychSituationTopic::class,
        PsychAiCache::class,
        PsychAiUsage::class,
        ru.na.step4.obidy.data.activity.ActivityEvent::class
    ],
    version = 9,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun resentmentDao(): ResentmentDao
    abstract fun categoryDao(): CategoryDao
    abstract fun situationDao(): SituationDao
    abstract fun analysisDao(): AnalysisDao
    abstract fun psychDao(): PsychDao
    abstract fun activityDao(): ru.na.step4.obidy.data.activity.ActivityDao

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
                    db.execSQL("ALTER TABLE resentments ADD COLUMN $name TEXT NOT NULL DEFAULT ''")
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
                db.execSQL("CREATE INDEX IF NOT EXISTS index_situations_typeId ON situations(typeId)")

                val cursor = db.query(
                    """
                    SELECT id, whatHappened, iFelt, iDid, cause, myPart,
                           q1,q2,q3,q4,q5,q6,q7,q8,q9,q10,q11,q12,q13
                    FROM resentments
                    """.trimIndent()
                )
                val now = System.currentTimeMillis()
                val general = "\u041e\u0431\u0449\u0430\u044f"
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    fun col(i: Int): String = cursor.getString(i) ?: ""
                    val what = col(1).ifBlank { col(4) }
                    val felt = col(2)
                    val did = col(3).ifBlank { col(5) }
                    val qs = (6..18).map { col(it) }
                    val has = listOf(what, felt, did).any { it.isNotBlank() } || qs.any { it.isNotBlank() }
                    if (!has) continue
                    db.execSQL(
                        "INSERT INTO situation_types (resentmentId, name, sortOrder, createdAt) VALUES (?, ?, 0, ?)",
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

        private val MIGRATION_4_5 = object : Migration(4, 5) {
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

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS self_analysis_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        catalogId TEXT NOT NULL,
                        title TEXT NOT NULL,
                        answersJson TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS psych_situations (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        text TEXT NOT NULL,
                        summary TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        viaVoice INTEGER NOT NULL,
                        noHistory INTEGER NOT NULL,
                        topicId INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS psych_sessions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        situationId INTEGER NOT NULL,
                        sessionUid TEXT NOT NULL,
                        status INTEGER NOT NULL,
                        sequentialWork INTEGER NOT NULL,
                        questionsJson TEXT NOT NULL,
                        currentIndex INTEGER NOT NULL,
                        postponed INTEGER NOT NULL,
                        analyzeText TEXT NOT NULL,
                        analyzeSpeakable TEXT NOT NULL,
                        recommendText TEXT NOT NULL,
                        recommendSpeakable TEXT NOT NULL,
                        assistantText TEXT NOT NULL,
                        assistantSpeakable TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        completedAt INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_psych_sessions_sessionUid ON psych_sessions(sessionUid)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_psych_sessions_situationId ON psych_sessions(situationId)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS psych_answers (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sessionUid TEXT NOT NULL,
                        questionIndex INTEGER NOT NULL,
                        questionText TEXT NOT NULL,
                        answerText TEXT NOT NULL,
                        viaVoice INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_psych_answers_sessionUid_questionIndex ON psych_answers(sessionUid, questionIndex)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS psych_topics (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        summaryText TEXT NOT NULL,
                        useCount INTEGER NOT NULL,
                        lastUsedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_psych_topics_name ON psych_topics(name)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS psych_ai_cache (
                        cacheKey TEXT NOT NULL PRIMARY KEY,
                        requestType TEXT NOT NULL,
                        responseText TEXT NOT NULL,
                        promptText TEXT NOT NULL,
                        lockUntil INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS psych_ai_usage (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        requestType TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        viaVoice INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS psych_situation_topics (
                        situationId INTEGER NOT NULL,
                        topicId INTEGER NOT NULL,
                        PRIMARY KEY(situationId, topicId)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_psych_situation_topics_topicId ON psych_situation_topics(topicId)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_psych_situation_topics_situationId ON psych_situation_topics(situationId)"
                )
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO psych_situation_topics (situationId, topicId)
                    SELECT id, topicId FROM psych_situations WHERE topicId IS NOT NULL
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS activity_events (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        category TEXT NOT NULL,
                        type TEXT NOT NULL,
                        label TEXT NOT NULL,
                        detail TEXT NOT NULL,
                        startedAt INTEGER NOT NULL,
                        endedAt INTEGER,
                        sessionKey TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_activity_events_startedAt ON activity_events(startedAt)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_activity_events_sessionKey ON activity_events(sessionKey)"
                )
            }
        }

        fun get(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "step4_obidy.db"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9
                    )
                    .build()
                    .also { instance = it }
            }
        }
    }
}
