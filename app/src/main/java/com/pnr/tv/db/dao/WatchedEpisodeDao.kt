package com.pnr.tv.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pnr.tv.db.entity.WatchedEpisodeEntity
import kotlinx.coroutines.flow.Flow

/**
 * İzlenen bölümler için DAO.
 */
@Dao
interface WatchedEpisodeDao {
    /**
     * Bir bölümü izlendi olarak işaretle veya güncelle.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markAsWatched(episode: WatchedEpisodeEntity)

    /**
     * Belirli bir dizinin tüm izlenmiş bölümlerini getir.
     */
    @Query("SELECT * FROM watched_episodes WHERE seriesId = :seriesId ORDER BY seasonNumber, episodeNumber")
    fun getWatchedEpisodesForSeries(seriesId: Int): Flow<List<WatchedEpisodeEntity>>

    /**
     * Belirli bir bölümün izlenip izlenmediğini kontrol et.
     */
    @Query("SELECT * FROM watched_episodes WHERE episodeId = :episodeId LIMIT 1")
    suspend fun getWatchedEpisode(episodeId: String): WatchedEpisodeEntity?

    /**
     * Belirli bir bölümün izlenme durumunu sil.
     */
    @Query("DELETE FROM watched_episodes WHERE episodeId = :episodeId")
    suspend fun removeWatchedEpisode(episodeId: String)

    /**
     * Belirli bir dizinin en son izlenen bölümünü getir.
     */
    @Query("SELECT * FROM watched_episodes WHERE seriesId = :seriesId ORDER BY watchedTimestamp DESC LIMIT 1")
    suspend fun getLastWatchedEpisode(seriesId: Int): WatchedEpisodeEntity?

    /**
     * Tüm izlenme verilerini temizle (test için).
     */
    @Query("DELETE FROM watched_episodes")
    suspend fun clearAll()
}
