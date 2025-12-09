package com.pnr.tv.repository

import com.pnr.tv.Constants
import com.pnr.tv.db.dao.RecentlyWatchedDao
import com.pnr.tv.db.entity.RecentlyWatchedEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Son izlenenlerle ilgili tüm işlemleri yöneten repository.
 */
class RecentlyWatchedRepository
    @Inject
    constructor(
        private val recentlyWatchedDao: RecentlyWatchedDao,
    ) {
        suspend fun saveRecentlyWatched(channelId: Int) {
            recentlyWatchedDao.upsert(RecentlyWatchedEntity(channelId, System.currentTimeMillis()))
            recentlyWatchedDao.trim(50)
        }

        fun getRecentlyWatchedChannelIds(limit: Int = Constants.RECENTLY_WATCHED_DEFAULT_LIMIT): Flow<List<Int>> =
            recentlyWatchedDao.getRecentlyWatchedChannelIds(limit)
    }





