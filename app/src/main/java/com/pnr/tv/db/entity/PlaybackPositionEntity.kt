package com.pnr.tv.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Kaldığı yerden devam etme için oynatma pozisyonlarını saklayan entity.
 * Hem filmler hem de dizi bölümleri için kullanılır.
 */
@Entity(
    tableName = "playback_positions",
    foreignKeys = [
        ForeignKey(
            entity = UserAccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["userId"])],
    primaryKeys = ["contentId", "userId"],
)
data class PlaybackPositionEntity(
    val contentId: String, // Film ID veya Bölüm ID (String olarak)
    val userId: Int,
    val positionMs: Long, // Milisaniye cinsinden pozisyon
    val durationMs: Long, // Toplam süre (opsiyonel kontrol için)
    val lastUpdated: Long, // Son güncellenme zamanı
)
