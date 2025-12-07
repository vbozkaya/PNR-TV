package com.pnr.tv.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.pnr.tv.db.entity.SeriesEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SeriesDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(seriesList: List<SeriesEntity>)

    @Query("SELECT * FROM series ORDER BY added DESC")
    fun getAll(): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM series WHERE categoryId = :categoryId ORDER BY added DESC")
    fun getByCategoryId(categoryId: String): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM series ORDER BY added DESC LIMIT :limit")
    suspend fun getRecentlyAdded(limit: Int): List<SeriesEntity>

    @Query("SELECT * FROM series WHERE streamId IN (:seriesIds)")
    suspend fun getByIds(seriesIds: List<Int>): List<SeriesEntity>

    /**
     * TMDB ID'si olan tüm dizilerin streamId'lerini döndürür
     * Delta senkronizasyonu için kullanılır
     * 
     * @return TMDB verisine sahip dizi ID'leri
     */
    @Query("SELECT streamId FROM series WHERE tmdbId IS NOT NULL")
    suspend fun getSeriesIdsWithTmdb(): List<Int>

    @Query("DELETE FROM series")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceAll(seriesList: List<SeriesEntity>) {
        clearAll()
        if (seriesList.isNotEmpty()) {
            insertAll(seriesList)
        }
    }
}
