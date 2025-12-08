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

    @Query("SELECT * FROM viewers ORDER BY isDeletable ASC, name ASC")
    fun getAllViewers(): Flow<List<ViewerEntity>>

    @Query("SELECT * FROM viewers WHERE id = :id")
    suspend fun getViewerById(id: Int): ViewerEntity?

    @Query("SELECT DISTINCT viewerId FROM favorite_channels")
    fun getViewerIdsWithFavorites(): Flow<List<Int>>

    /**
     * Tüm viewer'ları siler (kullanıcı silindiğinde kullanılır).
     */
    @Query("DELETE FROM viewers")
    suspend fun deleteAll()
}



