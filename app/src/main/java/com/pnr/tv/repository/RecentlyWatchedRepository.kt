package com.pnr.tv.repository

import com.pnr.tv.DatabaseConstants
import com.pnr.tv.SessionManager
import com.pnr.tv.db.dao.RecentlyWatchedDao
import com.pnr.tv.db.entity.RecentlyWatchedEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

/**
 * Son izlenenlerle ilgili tüm işlemleri yöneten repository.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecentlyWatchedRepository
    @Inject
    constructor(
        private val recentlyWatchedDao: RecentlyWatchedDao,
        private val sessionManager: SessionManager,
    ) {
        suspend fun saveRecentlyWatched(channelId: Int) {
            val userId = sessionManager.getCurrentUserId().firstOrNull() ?: return
            recentlyWatchedDao.upsert(RecentlyWatchedEntity(channelId, userId, System.currentTimeMillis()))
            recentlyWatchedDao.trim(userId, 50)
        }

        fun getRecentlyWatchedChannelIds(limit: Int = DatabaseConstants.RECENTLY_WATCHED_DEFAULT_LIMIT): Flow<List<Int>> =
            sessionManager.getCurrentUserId().flatMapLatest { userId ->
                if (userId == null) {
                    kotlinx.coroutines.flow.flowOf(emptyList())
                } else {
                    recentlyWatchedDao.getRecentlyWatchedChannelIds(userId, limit)
                }
            }
    }
