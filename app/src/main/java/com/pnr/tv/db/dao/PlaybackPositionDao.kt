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
    @Query("SELECT * FROM playback_positions WHERE contentId = :contentId AND userId = :userId")
    suspend fun getPosition(
        contentId: String,
        userId: Int,
    ): PlaybackPositionEntity?

    /**
     * Belirli bir içeriğin pozisyonunu siler.
     */
    @Query("DELETE FROM playback_positions WHERE contentId = :contentId AND userId = :userId")
    suspend fun deletePosition(
        contentId: String,
        userId: Int,
    )

    /**
     * Belirli bir kullanıcıya ait tüm pozisyonları siler (kullanıcı silindiğinde kullanılır).
     */
    @Query("DELETE FROM playback_positions WHERE userId = :userId")
    suspend fun deleteByUserId(userId: Int)

    /**
     * Tüm pozisyonları siler (tüm kullanıcılar için - sadece clearAllData için).
     */
    @Query("DELETE FROM playback_positions")
    suspend fun deleteAll()

    /**
     * 30 günden eski pozisyonları temizler.
     */
    @Query("DELETE FROM playback_positions WHERE lastUpdated < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)
}
