package com.pnr.tv.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.pnr.tv.db.entity.LiveStreamEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LiveStreamDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(liveStreams: List<LiveStreamEntity>)

    @Query("SELECT * FROM live_streams")
    fun getAll(): Flow<List<LiveStreamEntity>>

    @Query("SELECT * FROM live_streams WHERE categoryId = :categoryId ORDER BY name ASC")
    fun getByCategoryId(categoryId: Int): Flow<List<LiveStreamEntity>>

    @Query("SELECT * FROM live_streams WHERE streamId IN (:streamIds)")
    suspend fun getByIds(streamIds: List<Int>): List<LiveStreamEntity>

    @Query("DELETE FROM live_streams")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceAll(liveStreams: List<LiveStreamEntity>) {
        clearAll()
        if (liveStreams.isNotEmpty()) {
            insertAll(liveStreams)
        }
    }
}
