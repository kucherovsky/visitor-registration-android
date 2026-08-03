package ru.atol.visitorregistration.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PrinterDao {
    @Query("SELECT * FROM printers ORDER BY isDefault DESC, name COLLATE NOCASE")
    fun observeAll(): Flow<List<PrinterEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(printer: PrinterEntity)

    @Query("SELECT COUNT(*) FROM printers")
    suspend fun count(): Int

    @Query("SELECT * FROM printers WHERE id = :id")
    suspend fun getById(id: String): PrinterEntity?

    @Query("SELECT * FROM printers ORDER BY name COLLATE NOCASE LIMIT 1")
    suspend fun firstOrNull(): PrinterEntity?

    @Query("UPDATE printers SET isDefault = 0")
    suspend fun clearDefault()

    @Query("UPDATE printers SET isDefault = 1 WHERE id = :id")
    suspend fun markDefault(id: String)

    @Query("DELETE FROM printers WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM printers")
    suspend fun deleteAll()
}
