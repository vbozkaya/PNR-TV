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

    /**
     * Son eklenen dizileri getirir.
     * added değeri null ise streamId'ye göre sıralama yapar (büyük streamId = daha yeni).
     * SQLite'da null değerlerin sıralaması belirsiz olduğu için streamId fallback olarak kullanılır.
     * Flow döndürür, böylece veritabanı değişikliklerini otomatik olarak dinler.
     */
    @Query(
        "SELECT * FROM series ORDER BY (CASE WHEN added IS NULL OR added = '' THEN 0 ELSE CAST(added AS INTEGER) END) DESC, streamId DESC LIMIT :limit",
    )
    fun getRecentlyAdded(limit: Int): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM series WHERE streamId IN (:seriesIds)")
    suspend fun getByIds(seriesIds: List<Int>): List<SeriesEntity>

    /**
     * Kategori ID'ye göre dizi sayısını döndürür (performans optimizasyonu için).
     * Yetişkin içerik filtresi uygulanmaz, sadece kategori bazında sayım yapar.
     * Room Map döndüremediği için data class kullanıyoruz.
     */
    data class CategoryCount(
        val categoryId: String,
        val count: Int,
    )

    @Query("SELECT categoryId, COUNT(*) as count FROM series WHERE categoryId IS NOT NULL GROUP BY categoryId")
    suspend fun getCategoryCounts(): List<CategoryCount>

    /**
     * TMDB ID'si olan tüm dizilerin streamId'lerini döndürür
     * Delta senkronizasyonu için kullanılır
     *
     * @return TMDB verisine sahip dizi ID'leri
     */
    @Query("SELECT streamId FROM series WHERE tmdbId IS NOT NULL")
    suspend fun getSeriesIdsWithTmdb(): List<Int>

    @Query("SELECT COUNT(*) FROM series")
    suspend fun getCount(): Int

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
