package com.pnr.tv.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * TMDB film detaylarını önbelleğe almak için Entity
 *
 * Her film için TMDB'den çekilen yönetmen, oyuncu ve açıklama bilgilerini
 * yerel veritabanında saklar. Böylece aynı filme tekrar girildiğinde
 * API'ye yeni istek atmadan veriler gösterilebilir.
 */
@Entity(tableName = "tmdb_cache")
data class TmdbCacheEntity(
    /**
     * TMDB film ID'si (Primary Key)
     */
    @PrimaryKey
    val tmdbId: Int,
    /**
     * Film başlığı
     */
    val title: String?,
    /**
     * Yönetmen adı
     */
    val director: String?,
    /**
     * Oyuncular listesi (virgülle ayrılmış)
     * Örnek: "Actor1, Actor2, Actor3, Actor4, Actor5"
     */
    val cast: String?,
    /**
     * Film açıklaması
     */
    val overview: String?,
    /**
     * Cache'e eklenme zamanı (Unix timestamp - milisaniye)
     * Bu değer ile cache'in ne kadar eski olduğu kontrol edilir
     */
    val cacheTime: Long,
    /**
     * TMDB API'den çekilen tam JSON verisi (opsiyonel, debug için)
     * İleride daha fazla veri kullanmak istersek bu field'den alabiliriz
     */
    val rawJson: String? = null,
)
