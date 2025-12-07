package com.pnr.tv.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * İzlenen bölümleri takip eden entity.
 * Kullanıcının hangi bölümleri izlediğini ve ne kadarını izlediğini saklar.
 */
@Entity(tableName = "watched_episodes")
data class WatchedEpisodeEntity(
    @PrimaryKey
    val episodeId: String,           // Bölüm ID'si
    val seriesId: Int,                // Dizi ID'si
    val seasonNumber: Int,            // Sezon numarası
    val episodeNumber: Int,           // Bölüm numarası
    val watchedTimestamp: Long,       // İzlenme zamanı (milisaniye)
    val watchProgress: Int = 100      // İzlenme yüzdesi (0-100), varsayılan tam izlendi
)



