package com.pnr.tv.repository

import com.pnr.tv.db.dao.PlaybackPositionDao
import com.pnr.tv.db.entity.PlaybackPositionEntity
import timber.log.Timber
import javax.inject.Inject

/**
 * Oynatma pozisyonlarıyla ilgili tüm işlemleri yöneten repository.
 */
class PlaybackPositionRepository
    @Inject
    constructor(
        private val playbackPositionDao: PlaybackPositionDao,
    ) {
        /**
         * Oynatma pozisyonunu kaydeder veya günceller.
         * @param contentId Film veya bölüm ID'si (String)
         * @param positionMs Milisaniye cinsinden pozisyon
         * @param durationMs Toplam süre
         */
        suspend fun savePlaybackPosition(contentId: String, positionMs: Long, durationMs: Long) {
            val position = PlaybackPositionEntity(
                contentId = contentId,
                positionMs = positionMs,
                durationMs = durationMs,
                lastUpdated = System.currentTimeMillis()
            )
            playbackPositionDao.upsert(position)
            Timber.d("💾 Pozisyon kaydedildi: $contentId -> ${positionMs/1000}s / ${durationMs/1000}s")
        }

        /**
         * İçerik ID'sine göre kayıtlı pozisyonu getirir.
         * @return PlaybackPositionEntity veya null
         */
        suspend fun getPlaybackPosition(contentId: String): PlaybackPositionEntity? {
            return playbackPositionDao.getPosition(contentId)
        }

        /**
         * Belirli bir içeriğin pozisyonunu siler.
         * Video tamamlandığında veya kullanıcı baştan başlatmak istediğinde kullanılır.
         */
        suspend fun deletePlaybackPosition(contentId: String) {
            playbackPositionDao.deletePosition(contentId)
            Timber.d("🗑️ Pozisyon silindi: $contentId")
        }

        /**
         * 30 günden eski pozisyonları temizler (otomatik temizlik).
         */
        suspend fun cleanupOldPlaybackPositions() {
            val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
            playbackPositionDao.deleteOlderThan(thirtyDaysAgo)
            Timber.d("🧹 30 günden eski pozisyonlar temizlendi")
        }
    }




