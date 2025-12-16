package com.pnr.tv.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Kaldığı yerden devam etme için oynatma pozisyonlarını saklayan entity.
 * Hem filmler hem de dizi bölümleri için kullanılır.
 */
@Entity(tableName = "playback_positions")
data class PlaybackPositionEntity(
    @PrimaryKey val contentId: String, // Film ID veya Bölüm ID (String olarak)
    val positionMs: Long, // Milisaniye cinsinden pozisyon
    val durationMs: Long, // Toplam süre (opsiyonel kontrol için)
    val lastUpdated: Long, // Son güncellenme zamanı
)
