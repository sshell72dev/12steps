package ru.na.step4.obidy.data.analysis

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "self_analysis_records")
data class AnalysisRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val catalogId: String,
    val title: String,
    val answersJson: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface AnalysisDao {
    @Query("SELECT * FROM self_analysis_records ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<AnalysisRecord>>

    @Query("SELECT * FROM self_analysis_records WHERE id = :id")
    fun observeById(id: Long): Flow<AnalysisRecord?>

    @Query("SELECT * FROM self_analysis_records WHERE id = :id")
    suspend fun getById(id: Long): AnalysisRecord?

    @Insert
    suspend fun insert(record: AnalysisRecord): Long

    @Query("UPDATE self_analysis_records SET answersJson = :answersJson WHERE id = :id")
    suspend fun updateAnswers(id: Long, answersJson: String)

    @Query("DELETE FROM self_analysis_records WHERE id = :id")
    suspend fun delete(id: Long)
}

class AnalysisRepository(private val dao: AnalysisDao) {
    fun observeAll(): Flow<List<AnalysisRecord>> = dao.observeAll()

    fun observeById(id: Long): Flow<AnalysisRecord?> = dao.observeById(id)

    suspend fun getById(id: Long): AnalysisRecord? = dao.getById(id)

    suspend fun save(catalogId: String, title: String, answers: List<QaPair>): Long {
        return dao.insert(
            AnalysisRecord(
                catalogId = catalogId,
                title = title,
                answersJson = AnalysisAnswers.encode(answers)
            )
        )
    }

    suspend fun replaceAnswers(id: Long, answers: List<QaPair>) {
        dao.updateAnswers(id, AnalysisAnswers.encode(answers))
    }

    suspend fun delete(id: Long) {
        dao.delete(id)
    }
}
