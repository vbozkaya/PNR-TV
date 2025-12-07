package com.pnr.tv.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.pnr.tv.db.entity.LiveStreamCategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LiveStreamCategoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<LiveStreamCategoryEntity>)

    @Query("SELECT * FROM live_stream_categories ORDER BY sortOrder ASC")
    fun getAll(): Flow<List<LiveStreamCategoryEntity>>

    @Query("SELECT * FROM live_stream_categories WHERE categoryId = :categoryId")
    suspend fun getById(categoryId: Int): LiveStreamCategoryEntity?

    @Query("DELETE FROM live_stream_categories")
    suspend fun clearAll()

    /**
     * clearAll ve insertAll'ı tek bir transaction içinde yapar.
     * Bu sayede Room Flow sadece bir kez emit eder (önce boş, sonra dolu değil).
     */
    @Transaction
    suspend fun replaceAll(categories: List<LiveStreamCategoryEntity>) {
        clearAll()
        if (categories.isNotEmpty()) {
            insertAll(categories)
        }
    }
}
