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
    @Query("SELECT * FROM watched_episodes WHERE seriesId = :seriesId AND userId = :userId ORDER BY seasonNumber, episodeNumber")
    fun getWatchedEpisodesForSeries(
        seriesId: Int,
        userId: Int,
    ): Flow<List<WatchedEpisodeEntity>>

    /**
     * Belirli bir bölümün izlenip izlenmediğini kontrol et.
     */
    @Query("SELECT * FROM watched_episodes WHERE episodeId = :episodeId AND userId = :userId LIMIT 1")
    suspend fun getWatchedEpisode(
        episodeId: String,
        userId: Int,
    ): WatchedEpisodeEntity?

    /**
     * Belirli bir bölüm listesinin izlenme durumlarını tek seferde getir.
     */
    @Query("SELECT * FROM watched_episodes WHERE episodeId IN (:episodeIds) AND userId = :userId")
    suspend fun getWatchedStatusForEpisodes(
        episodeIds: List<String>,
        userId: Int,
    ): List<WatchedEpisodeEntity>

    /**
     * Belirli bir bölümün izlenme durumunu sil.
     */
    @Query("DELETE FROM watched_episodes WHERE episodeId = :episodeId AND userId = :userId")
    suspend fun removeWatchedEpisode(
        episodeId: String,
        userId: Int,
    )

    /**
     * Belirli bir dizinin en son izlenen bölümünü getir.
     */
    @Query("SELECT * FROM watched_episodes WHERE seriesId = :seriesId AND userId = :userId ORDER BY watchedTimestamp DESC LIMIT 1")
    suspend fun getLastWatchedEpisode(
        seriesId: Int,
        userId: Int,
    ): WatchedEpisodeEntity?

    /**
     * Belirli bir kullanıcıya ait tüm izlenme verilerini temizle (kullanıcı silindiğinde kullanılır).
     */
    @Query("DELETE FROM watched_episodes WHERE userId = :userId")
    suspend fun deleteByUserId(userId: Int)

    /**
     * Tüm izlenme verilerini temizle (tüm kullanıcılar için - sadece clearAllData için).
     */
    @Query("DELETE FROM watched_episodes")
    suspend fun clearAll()
}
