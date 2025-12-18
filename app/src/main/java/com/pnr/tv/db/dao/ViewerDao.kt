package com.pnr.tv.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pnr.tv.db.entity.ViewerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ViewerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(viewer: ViewerEntity): Long

    @Query("DELETE FROM viewers WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("SELECT * FROM viewers WHERE userId = :userId ORDER BY isDeletable ASC, name ASC")
    fun getAllViewers(userId: Int): Flow<List<ViewerEntity>>

    @Query("SELECT * FROM viewers WHERE id = :id AND userId = :userId")
    suspend fun getViewerById(
        id: Int,
        userId: Int,
    ): ViewerEntity?

    @Query("SELECT DISTINCT viewerId FROM favorite_channels WHERE userId = :userId")
    fun getViewerIdsWithFavorites(userId: Int): Flow<List<Int>>

    /**
     * Belirli bir kullanıcıya ait tüm viewer'ları siler (kullanıcı silindiğinde kullanılır).
     */
    @Query("DELETE FROM viewers WHERE userId = :userId")
    suspend fun deleteByUserId(userId: Int)

    /**
     * Tüm viewer'ları siler (tüm kullanıcılar için - sadece clearAllData için).
     */
    @Query("DELETE FROM viewers")
    suspend fun deleteAll()
}
