package com.pnr.tv.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pnr.tv.db.entity.RecentlyWatchedEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentlyWatchedDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(recent: RecentlyWatchedEntity)

    @Query("SELECT channelId FROM recently_watched_channels ORDER BY watchedAt DESC LIMIT :limit")
    fun getRecentlyWatchedChannelIds(limit: Int): Flow<List<Int>>

    @Query(
        "DELETE FROM recently_watched_channels WHERE channelId NOT IN (SELECT channelId FROM recently_watched_channels ORDER BY watchedAt DESC LIMIT :limit)",
    )
    suspend fun trim(limit: Int)

    /**
     * Tüm son izlenenleri siler (kullanıcı silindiğinde kullanılır).
     */
    @Query("DELETE FROM recently_watched_channels")
    suspend fun deleteAll()
}



