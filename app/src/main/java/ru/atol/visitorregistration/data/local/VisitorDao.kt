package ru.atol.visitorregistration.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VisitorDao {
    @Query("SELECT * FROM visitors ORDER BY lastName COLLATE NOCASE, firstName COLLATE NOCASE")
    fun observeAll(): Flow<List<VisitorEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(visitor: VisitorEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(visitors: List<VisitorEntity>)

    @Query("SELECT * FROM visitors WHERE id = :id")
    suspend fun getById(id: String): VisitorEntity?

    @Query("UPDATE visitors SET checkedInAt = COALESCE(checkedInAt, :timestamp) WHERE id = :id")
    suspend fun checkIn(id: String, timestamp: Long)

    @Query("UPDATE visitors SET printCount = printCount + 1 WHERE id = :id")
    suspend fun markPrinted(id: String)

    @Query("DELETE FROM visitors WHERE source = :source")
    suspend fun deleteBySource(source: String)

    @Query("DELETE FROM visitors")
    suspend fun deleteAll()
}
