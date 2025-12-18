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

    @Query("SELECT channelId FROM recently_watched_channels WHERE userId = :userId ORDER BY watchedAt DESC LIMIT :limit")
    fun getRecentlyWatchedChannelIds(
        userId: Int,
        limit: Int,
    ): Flow<List<Int>>

    @Query(
        "DELETE FROM recently_watched_channels WHERE userId = :userId AND channelId NOT IN (SELECT channelId FROM recently_watched_channels WHERE userId = :userId ORDER BY watchedAt DESC LIMIT :limit)",
    )
    suspend fun trim(
        userId: Int,
        limit: Int,
    )

    /**
     * Belirli bir kullanıcıya ait tüm son izlenenleri siler (kullanıcı silindiğinde kullanılır).
     */
    @Query("DELETE FROM recently_watched_channels WHERE userId = :userId")
    suspend fun deleteByUserId(userId: Int)

    /**
     * Tüm son izlenenleri siler (tüm kullanıcılar için - sadece clearAllData için).
     */
    @Query("DELETE FROM recently_watched_channels")
    suspend fun deleteAll()
}
