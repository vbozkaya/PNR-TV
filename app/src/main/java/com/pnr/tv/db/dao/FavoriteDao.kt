package com.pnr.tv.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pnr.tv.db.entity.FavoriteChannelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: FavoriteChannelEntity)

    @Query("DELETE FROM favorite_channels WHERE channelId = :channelId AND viewerId = :viewerId AND userId = :userId")
    suspend fun removeFavorite(
        channelId: Int,
        viewerId: Int,
        userId: Int,
    )

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_channels WHERE channelId = :channelId AND viewerId = :viewerId AND userId = :userId)")
    fun isFavorite(
        channelId: Int,
        viewerId: Int,
        userId: Int,
    ): Flow<Boolean>

    @Query("SELECT channelId FROM favorite_channels WHERE viewerId = :viewerId AND userId = :userId")
    fun getFavoriteChannelIds(
        viewerId: Int,
        userId: Int,
    ): Flow<List<Int>>

    @Query("SELECT channelId FROM favorite_channels WHERE userId = :userId")
    fun getAllFavoriteChannelIds(userId: Int): Flow<List<Int>>

    @Query("SELECT DISTINCT viewerId FROM favorite_channels WHERE userId = :userId")
    fun getViewerIdsWithFavorites(userId: Int): Flow<List<Int>>

    /**
     * Belirli bir kullanıcıya ait tüm favorileri siler (kullanıcı silindiğinde kullanılır).
     */
    @Query("DELETE FROM favorite_channels WHERE userId = :userId")
    suspend fun deleteByUserId(userId: Int)

    /**
     * Tüm favorileri siler (tüm kullanıcılar için - sadece clearAllData için).
     */
    @Query("DELETE FROM favorite_channels")
    suspend fun deleteAll()
}
