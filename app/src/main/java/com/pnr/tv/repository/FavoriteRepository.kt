package com.pnr.tv.repository

import com.pnr.tv.SessionManager
import com.pnr.tv.db.dao.FavoriteDao
import com.pnr.tv.db.entity.FavoriteChannelEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

/**
 * Favorilerle ilgili tüm işlemleri yöneten repository.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FavoriteRepository
    @Inject
    constructor(
        private val favoriteDao: FavoriteDao,
        private val sessionManager: SessionManager,
    ) {
        suspend fun addFavorite(
            channelId: Int,
            viewerId: Int,
        ) {
            val userId = sessionManager.getCurrentUserId().firstOrNull() ?: return
            favoriteDao.addFavorite(FavoriteChannelEntity(channelId, viewerId, userId))
        }

        suspend fun removeFavorite(
            channelId: Int,
            viewerId: Int,
        ) {
            val userId = sessionManager.getCurrentUserId().firstOrNull() ?: return
            favoriteDao.removeFavorite(channelId, viewerId, userId)
        }

        fun isFavorite(
            channelId: Int,
            viewerId: Int,
        ): Flow<Boolean> =
            sessionManager.getCurrentUserId().flatMapLatest { userId ->
                if (userId == null) {
                    kotlinx.coroutines.flow.flowOf(false)
                } else {
                    favoriteDao.isFavorite(channelId, viewerId, userId)
                }
            }

        fun getFavoriteChannelIds(viewerId: Int): Flow<List<Int>> =
            sessionManager.getCurrentUserId().flatMapLatest { userId ->
                if (userId == null) {
                    kotlinx.coroutines.flow.flowOf(emptyList())
                } else {
                    favoriteDao.getFavoriteChannelIds(viewerId, userId)
                }
            }

        fun getAllFavoriteChannelIds(): Flow<List<Int>> =
            sessionManager.getCurrentUserId().flatMapLatest { userId ->
                if (userId == null) {
                    kotlinx.coroutines.flow.flowOf(emptyList())
                } else {
                    favoriteDao.getAllFavoriteChannelIds(userId)
                }
            }

        fun getViewerIdsWithFavorites(): Flow<List<Int>> =
            sessionManager.getCurrentUserId().flatMapLatest { userId ->
                if (userId == null) {
                    kotlinx.coroutines.flow.flowOf(emptyList())
                } else {
                    favoriteDao.getViewerIdsWithFavorites(userId)
                }
            }
    }
