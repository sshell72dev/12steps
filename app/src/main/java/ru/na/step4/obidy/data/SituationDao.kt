package ru.na.step4.obidy.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SituationDao {
    @Query("SELECT * FROM situation_types WHERE resentmentId = :resentmentId ORDER BY name COLLATE NOCASE ASC, id ASC")
    fun observeTypes(resentmentId: Long): Flow<List<SituationType>>

    @Query("SELECT * FROM situation_types WHERE resentmentId = :resentmentId ORDER BY name COLLATE NOCASE ASC, id ASC")
    suspend fun getTypes(resentmentId: Long): List<SituationType>

    @Query("SELECT * FROM situation_types WHERE id = :id")
    suspend fun getType(id: Long): SituationType?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertType(item: SituationType): Long

    @Update
    suspend fun updateType(item: SituationType)

    @Delete
    suspend fun deleteType(item: SituationType)

    @Query("SELECT COUNT(*) FROM situation_types WHERE resentmentId = :resentmentId")
    suspend fun countTypes(resentmentId: Long): Int

    @Query("SELECT COUNT(*) FROM situation_types")
    fun observeTypeCount(): Flow<Int>

    @Query("SELECT * FROM situations WHERE resentmentId = :resentmentId ORDER BY sortOrder ASC, id ASC")
    fun observeSituationsForResentment(resentmentId: Long): Flow<List<Situation>>

    @Query("SELECT * FROM situations WHERE resentmentId = :resentmentId ORDER BY sortOrder ASC, id ASC")
    suspend fun getSituationsForResentment(resentmentId: Long): List<Situation>

    @Query("SELECT * FROM situations WHERE id = :id")
    suspend fun getSituation(id: Long): Situation?

    @Query("SELECT * FROM situations WHERE id = :id")
    fun observeSituation(id: Long): Flow<Situation?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSituation(item: Situation): Long

    @Update
    suspend fun updateSituation(item: Situation)

    @Delete
    suspend fun deleteSituation(item: Situation)

    @Query("SELECT COUNT(*) FROM situations WHERE resentmentId = :resentmentId")
    suspend fun countSituationsForResentment(resentmentId: Long): Int

    @Query("SELECT COUNT(*) FROM situations")
    fun observeSituationCount(): Flow<Int>

    @Query("SELECT COALESCE(MAX(updatedAt), 0) FROM situations")
    fun observeSituationStamp(): Flow<Long>

    @Query("SELECT * FROM situation_type_links WHERE situationId = :situationId")
    fun observeLinksForSituation(situationId: Long): Flow<List<SituationTypeLink>>

    @Query("SELECT * FROM situation_type_links WHERE situationId = :situationId")
    suspend fun getLinksForSituation(situationId: Long): List<SituationTypeLink>

    @Query(
        """
        SELECT l.* FROM situation_type_links l
        INNER JOIN situations s ON s.id = l.situationId
        WHERE s.resentmentId = :resentmentId
        """
    )
    fun observeLinksForResentment(resentmentId: Long): Flow<List<SituationTypeLink>>

    @Query(
        """
        SELECT l.* FROM situation_type_links l
        INNER JOIN situations s ON s.id = l.situationId
        WHERE s.resentmentId = :resentmentId
        """
    )
    suspend fun getLinksForResentment(resentmentId: Long): List<SituationTypeLink>

    @Query(
        """
        SELECT t.* FROM situation_types t
        INNER JOIN situation_type_links l ON l.typeId = t.id
        WHERE l.situationId = :situationId
        ORDER BY t.name COLLATE NOCASE ASC
        """
    )
    fun observeTypesForSituation(situationId: Long): Flow<List<SituationType>>

    @Query(
        """
        SELECT t.* FROM situation_types t
        INNER JOIN situation_type_links l ON l.typeId = t.id
        WHERE l.situationId = :situationId
        ORDER BY t.name COLLATE NOCASE ASC
        """
    )
    suspend fun getTypesForSituation(situationId: Long): List<SituationType>

    @Query(
        """
        SELECT s.* FROM situations s
        INNER JOIN situation_type_links l ON l.situationId = s.id
        WHERE l.typeId = :typeId
        ORDER BY s.sortOrder ASC, s.id ASC
        """
    )
    fun observeSituationsForType(typeId: Long): Flow<List<Situation>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLink(link: SituationTypeLink)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLinks(links: List<SituationTypeLink>)

    @Query("DELETE FROM situation_type_links WHERE situationId = :situationId")
    suspend fun clearLinksForSituation(situationId: Long)

    @Query("DELETE FROM situation_type_links WHERE situationId = :situationId AND typeId = :typeId")
    suspend fun deleteLink(situationId: Long, typeId: Long)

    @Query("SELECT COUNT(*) FROM situation_type_links")
    fun observeLinkCount(): Flow<Int>

    @Query("SELECT * FROM situation_types ORDER BY id ASC")
    suspend fun getAllTypes(): List<SituationType>

    @Query("SELECT * FROM situations ORDER BY id ASC")
    suspend fun getAllSituations(): List<Situation>

    @Query("SELECT * FROM situation_type_links")
    suspend fun getAllLinks(): List<SituationTypeLink>
}
