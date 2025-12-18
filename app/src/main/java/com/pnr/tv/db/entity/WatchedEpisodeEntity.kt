package com.pnr.tv.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * İzlenen bölümleri takip eden entity.
 * Kullanıcının hangi bölümleri izlediğini ve ne kadarını izlediğini saklar.
 */
@Entity(
    tableName = "watched_episodes",
    foreignKeys = [
        ForeignKey(
            entity = UserAccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["userId"])],
    primaryKeys = ["episodeId", "userId"],
)
data class WatchedEpisodeEntity(
    val episodeId: String, // Bölüm ID'si
    val userId: Int,
    val seriesId: Int, // Dizi ID'si
    val seasonNumber: Int, // Sezon numarası
    val episodeNumber: Int, // Bölüm numarası
    val watchedTimestamp: Long, // İzlenme zamanı (milisaniye)
    val watchProgress: Int = 100, // İzlenme yüzdesi (0-100), varsayılan tam izlendi
)
