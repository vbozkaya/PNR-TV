package com.pnr.tv.repository

import com.pnr.tv.db.dao.FavoriteDao
import com.pnr.tv.db.entity.FavoriteChannelEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Favorilerle ilgili tüm işlemleri yöneten repository.
 */
class FavoriteRepository
    @Inject
    constructor(
        private val favoriteDao: FavoriteDao,
    ) {
        suspend fun addFavorite(channelId: Int, viewerId: Int) {
            favoriteDao.addFavorite(FavoriteChannelEntity(channelId, viewerId))
        }

        suspend fun removeFavorite(channelId: Int, viewerId: Int) {
            favoriteDao.removeFavorite(channelId, viewerId)
        }

        fun isFavorite(channelId: Int, viewerId: Int): Flow<Boolean> = favoriteDao.isFavorite(channelId, viewerId)

        fun getFavoriteChannelIds(viewerId: Int): Flow<List<Int>> = favoriteDao.getFavoriteChannelIds(viewerId)

        fun getAllFavoriteChannelIds(): Flow<List<Int>> = favoriteDao.getAllFavoriteChannelIds()

        fun getViewerIdsWithFavorites(): Flow<List<Int>> = favoriteDao.getViewerIdsWithFavorites()
    }

