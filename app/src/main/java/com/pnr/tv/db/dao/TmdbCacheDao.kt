package com.pnr.tv.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pnr.tv.db.entity.TmdbCacheEntity

/**
 * TMDB önbellek verileri için Data Access Object
 */
@Dao
interface TmdbCacheDao {
    /**
     * TMDB ID'ye göre önbellekteki film detaylarını getirir
     * @param tmdbId TMDB film ID'si
     * @return Cache'deki film detayı veya null
     */
    @Query("SELECT * FROM tmdb_cache WHERE tmdbId = :tmdbId LIMIT 1")
    suspend fun getCacheByTmdbId(tmdbId: Int): TmdbCacheEntity?

    /**
     * Birden fazla TMDB ID için önbellekteki verileri toplu olarak getirir
     * N+1 sorgu problemini önlemek için kullanılır
     * @param tmdbIds TMDB film ID'leri listesi
     * @return Cache'deki film detayları listesi
     */
    @Query("SELECT * FROM tmdb_cache WHERE tmdbId IN (:tmdbIds)")
    suspend fun getCacheByTmdbIds(tmdbIds: List<Int>): List<TmdbCacheEntity>

    /**
     * Film detaylarını önbelleğe ekler veya günceller
     * Aynı tmdbId varsa üzerine yazar (REPLACE stratejisi)
     * @param cache Kaydedilecek TMDB cache verisi
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCache(cache: TmdbCacheEntity)

    /**
     * Belirli bir tarihten eski tüm cache kayıtlarını siler
     * Periyodik temizlik için kullanılabilir
     * @param timestamp Bu tarihten eski kayıtlar silinir
     * @return Silinen kayıt sayısı
     */
    @Query("DELETE FROM tmdb_cache WHERE cacheTime < :timestamp")
    suspend fun deleteOldCache(timestamp: Long): Int

    /**
     * Tüm cache'i temizler
     * @return Silinen kayıt sayısı
     */
    @Query("DELETE FROM tmdb_cache")
    suspend fun clearAllCache(): Int

    /**
     * Cache'deki toplam kayıt sayısını döndürür
     * @return Toplam cache sayısı
     */
    @Query("SELECT COUNT(*) FROM tmdb_cache")
    suspend fun getCacheCount(): Int

    /**
     * Belirli bir TMDB ID'yi cache'den siler
     * @param tmdbId Silinecek TMDB film ID'si
     * @return Silinen kayıt sayısı
     */
    @Query("DELETE FROM tmdb_cache WHERE tmdbId = :tmdbId")
    suspend fun deleteCacheById(tmdbId: Int): Int
}

