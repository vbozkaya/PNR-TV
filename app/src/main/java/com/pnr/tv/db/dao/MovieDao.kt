package com.pnr.tv.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.pnr.tv.db.entity.MovieEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MovieDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(movies: List<MovieEntity>)

    @Query("SELECT * FROM movies ORDER BY added DESC")
    fun getAll(): Flow<List<MovieEntity>>

    @Query("SELECT * FROM movies WHERE categoryId = :categoryId ORDER BY added DESC")
    fun getByCategoryId(categoryId: String): Flow<List<MovieEntity>>

    @Query("SELECT * FROM movies ORDER BY added DESC LIMIT :limit")
    fun getRecentlyAdded(limit: Int): Flow<List<MovieEntity>>

    @Query("SELECT * FROM movies WHERE streamId IN (:movieIds)")
    suspend fun getByIds(movieIds: List<Int>): List<MovieEntity>

    /**
     * Kategori ID'ye göre film sayısını döndürür (performans optimizasyonu için).
     * Yetişkin içerik filtresi uygulanmaz, sadece kategori bazında sayım yapar.
     * Room Map döndüremediği için data class kullanıyoruz.
     */
    data class CategoryCount(
        val categoryId: String,
        val count: Int,
    )

    @Query("SELECT categoryId, COUNT(*) as count FROM movies WHERE categoryId IS NOT NULL GROUP BY categoryId")
    suspend fun getCategoryCounts(): List<CategoryCount>

    /**
     * TMDB ID'si olan tüm filmlerin streamId'lerini döndürür
     * Delta senkronizasyonu için kullanılır
     *
     * @return TMDB verisine sahip film ID'leri
     */
    @Query("SELECT streamId FROM movies WHERE tmdbId IS NOT NULL")
    suspend fun getMovieIdsWithTmdb(): List<Int>

    @Query("SELECT COUNT(*) FROM movies")
    suspend fun getCount(): Int

    @Query("DELETE FROM movies")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceAll(movies: List<MovieEntity>) {
        clearAll()
        if (movies.isNotEmpty()) {
            insertAll(movies)
        }
    }
}
