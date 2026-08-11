package ru.na.step4.obidy.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ResentmentDao {
    @Query("SELECT * FROM resentments ORDER BY isCompleted ASC, updatedAt DESC")
    fun observeAll(): Flow<List<Resentment>>

    @Query(
        """
        SELECT * FROM resentments
        WHERE categoryId = :categoryId
        ORDER BY isCompleted ASC, updatedAt DESC
        """
    )
    fun observeByCategory(categoryId: Long): Flow<List<Resentment>>

    @Query(
        """
        SELECT * FROM resentments
        WHERE categoryId IS NULL
        ORDER BY isCompleted ASC, updatedAt DESC
        """
    )
    fun observeUncategorized(): Flow<List<Resentment>>

    @Query("SELECT * FROM resentments WHERE id = :id")
    fun observeById(id: Long): Flow<Resentment?>

    @Query("SELECT * FROM resentments WHERE id = :id")
    suspend fun getById(id: Long): Resentment?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: Resentment): Long

    @Update
    suspend fun update(item: Resentment)

    @Delete
    suspend fun delete(item: Resentment)

    @Query("UPDATE resentments SET categoryId = NULL WHERE categoryId = :categoryId")
    suspend fun clearCategory(categoryId: Long)

    @Query("SELECT COUNT(*) FROM resentments")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM resentments WHERE isCompleted = 1")
    fun observeCompletedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM resentments WHERE categoryId = :categoryId")
    fun observeCountByCategory(categoryId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM resentments WHERE categoryId = :categoryId AND isCompleted = 1")
    fun observeCompletedCountByCategory(categoryId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM resentments WHERE categoryId IS NULL")
    fun observeUncategorizedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM resentments WHERE categoryId IS NULL AND isCompleted = 1")
    fun observeUncategorizedCompletedCount(): Flow<Int>
}
