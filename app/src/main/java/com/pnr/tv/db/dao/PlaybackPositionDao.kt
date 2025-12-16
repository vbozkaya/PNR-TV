package com.pnr.tv.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.pnr.tv.db.entity.PlaybackPositionEntity

/**
 * Oynatma pozisyonları için Data Access Object.
 * Kaldığı yerden devam etme özelliği için kullanılır.
 */
@Dao
interface PlaybackPositionDao {
    /**
     * Pozisyonu ekler veya günceller (upsert).
     */
    @Upsert
    suspend fun upsert(position: PlaybackPositionEntity)

    /**
     * İçerik ID'sine göre pozisyonu getirir.
     */
    @Query("SELECT * FROM playback_positions WHERE contentId = :contentId")
    suspend fun getPosition(contentId: String): PlaybackPositionEntity?

    /**
     * Belirli bir içeriğin pozisyonunu siler.
     */
    @Query("DELETE FROM playback_positions WHERE contentId = :contentId")
    suspend fun deletePosition(contentId: String)

    /**
     * Tüm pozisyonları siler (temizlik için).
     */
    @Query("DELETE FROM playback_positions")
    suspend fun deleteAll()

    /**
     * 30 günden eski pozisyonları temizler.
     */
    @Query("DELETE FROM playback_positions WHERE lastUpdated < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)
}
