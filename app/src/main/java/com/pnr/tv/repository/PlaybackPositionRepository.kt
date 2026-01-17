package com.pnr.tv.repository

import com.pnr.tv.ui.main.SessionManager
import com.pnr.tv.core.constants.TimeConstants
import com.pnr.tv.db.dao.PlaybackPositionDao
import com.pnr.tv.db.entity.PlaybackPositionEntity
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

/**
 * Oynatma pozisyonlarıyla ilgili tüm işlemleri yöneten repository.
 */
class PlaybackPositionRepository
    @Inject
    constructor(
        private val playbackPositionDao: PlaybackPositionDao,
        private val sessionManager: SessionManager,
    ) {
        /**
         * Oynatma pozisyonunu kaydeder veya günceller.
         * @param contentId Film veya bölüm ID'si (String)
         * @param positionMs Milisaniye cinsinden pozisyon
         * @param durationMs Toplam süre
         */
        suspend fun savePlaybackPosition(
            contentId: String,
            positionMs: Long,
            durationMs: Long,
        ) {
            val userId = sessionManager.getCurrentUserId().firstOrNull() ?: return
            val position =
                PlaybackPositionEntity(
                    contentId = contentId,
                    userId = userId,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    lastUpdated = System.currentTimeMillis(),
                )
            playbackPositionDao.upsert(position)
        }

        /**
         * İçerik ID'sine göre kayıtlı pozisyonu getirir.
         * @return PlaybackPositionEntity veya null
         */
        suspend fun getPlaybackPosition(contentId: String): PlaybackPositionEntity? {
            val userId = sessionManager.getCurrentUserId().firstOrNull() ?: return null
            return playbackPositionDao.getPosition(contentId, userId)
        }

        /**
         * Belirli bir içeriğin pozisyonunu siler.
         * Video tamamlandığında veya kullanıcı baştan başlatmak istediğinde kullanılır.
         */
        suspend fun deletePlaybackPosition(contentId: String) {
            val userId = sessionManager.getCurrentUserId().firstOrNull() ?: return
            playbackPositionDao.deletePosition(contentId, userId)
        }

        /**
         * 30 günden eski pozisyonları temizler (otomatik temizlik).
         */
        suspend fun cleanupOldPlaybackPositions() {
            val thirtyDaysAgo = System.currentTimeMillis() - TimeConstants.Intervals.THIRTY_DAYS_MS
            playbackPositionDao.deleteOlderThan(thirtyDaysAgo)
        }
    }
