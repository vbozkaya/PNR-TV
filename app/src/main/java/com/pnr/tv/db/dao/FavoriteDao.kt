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

    @Query("DELETE FROM favorite_channels WHERE channelId = :channelId AND viewerId = :viewerId")
    suspend fun removeFavorite(
        channelId: Int,
        viewerId: Int,
    )

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_channels WHERE channelId = :channelId AND viewerId = :viewerId)")
    fun isFavorite(
        channelId: Int,
        viewerId: Int,
    ): Flow<Boolean>

    @Query("SELECT channelId FROM favorite_channels WHERE viewerId = :viewerId")
    fun getFavoriteChannelIds(viewerId: Int): Flow<List<Int>>

    @Query("SELECT channelId FROM favorite_channels")
    fun getAllFavoriteChannelIds(): Flow<List<Int>>

    @Query("SELECT DISTINCT viewerId FROM favorite_channels")
    fun getViewerIdsWithFavorites(): Flow<List<Int>>

    /**
     * Tüm favorileri siler (kullanıcı silindiğinde kullanılır).
     */
    @Query("DELETE FROM favorite_channels")
    suspend fun deleteAll()
}
