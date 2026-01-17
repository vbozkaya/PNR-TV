package com.pnr.tv.repository

import com.pnr.tv.ui.main.SessionManager
import com.pnr.tv.db.dao.FavoriteDao
import com.pnr.tv.db.entity.FavoriteChannelEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import timber.log.Timber
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
        private val userRepository: UserRepository,
    ) {
        /**
         * Mevcut kullanıcı ID'sini alır. Eğer null ise ilk kullanıcıyı bulur ve kullanır.
         * Default kullanıcı senaryosu için gerekli.
         */
        private suspend fun getUserIdOrFirst(): Int? {
            val userId = sessionManager.getCurrentUserId().firstOrNull()
            if (userId != null) {
                return userId
            }
            // SessionManager'da kullanıcı yoksa, ilk kullanıcıyı bul ve kullan
            val firstUser = userRepository.allUsers.firstOrNull()?.firstOrNull()
            if (firstUser != null) {
                Timber.d("SessionManager'da kullanıcı yok, ilk kullanıcı kullanılıyor: ${firstUser.id}")
                return firstUser.id
            }
            Timber.w("Hiç kullanıcı bulunamadı, favori işlemi yapılamıyor")
            return null
        }

        suspend fun addFavorite(
            channelId: Int,
            viewerId: Int,
        ) {
            val userId = getUserIdOrFirst() ?: return
            favoriteDao.addFavorite(FavoriteChannelEntity(channelId, viewerId, userId))
        }

        suspend fun removeFavorite(
            channelId: Int,
            viewerId: Int,
        ) {
            val userId = getUserIdOrFirst() ?: return
            favoriteDao.removeFavorite(channelId, viewerId, userId)
        }

        /**
         * Belirli bir içeriği (channelId) tüm izleyicilerden favorilerden çıkarır.
         * Toggle favori işlemi için kullanılır.
         */
        suspend fun removeFavoriteForAllViewers(channelId: Int) {
            val userId = getUserIdOrFirst() ?: return
            favoriteDao.removeFavoriteForAllViewers(channelId, userId)
        }

        fun isFavorite(
            channelId: Int,
            viewerId: Int,
        ): Flow<Boolean> =
            sessionManager.getCurrentUserId().flatMapLatest { userId ->
                if (userId != null) {
                    favoriteDao.isFavorite(channelId, viewerId, userId)
                } else {
                    // SessionManager'da kullanıcı yoksa, ilk kullanıcıyı bul
                    userRepository.allUsers.flatMapLatest { users ->
                        val effectiveUserId = users.firstOrNull()?.id
                        if (effectiveUserId == null) {
                            kotlinx.coroutines.flow.flowOf(false)
                        } else {
                            favoriteDao.isFavorite(channelId, viewerId, effectiveUserId)
                        }
                    }
                }
            }

        fun getFavoriteChannelIds(viewerId: Int): Flow<List<Int>> =
            sessionManager.getCurrentUserId().flatMapLatest { userId ->
                if (userId != null) {
                    favoriteDao.getFavoriteChannelIds(viewerId, userId)
                } else {
                    // SessionManager'da kullanıcı yoksa, ilk kullanıcıyı bul
                    userRepository.allUsers.flatMapLatest { users ->
                        val effectiveUserId = users.firstOrNull()?.id
                        if (effectiveUserId == null) {
                            kotlinx.coroutines.flow.flowOf(emptyList())
                        } else {
                            favoriteDao.getFavoriteChannelIds(viewerId, effectiveUserId)
                        }
                    }
                }
            }

        fun getAllFavoriteChannelIds(): Flow<List<Int>> =
            sessionManager.getCurrentUserId().flatMapLatest { userId ->
                if (userId != null) {
                    favoriteDao.getAllFavoriteChannelIds(userId)
                } else {
                    // SessionManager'da kullanıcı yoksa, ilk kullanıcıyı bul
                    userRepository.allUsers.flatMapLatest { users ->
                        val effectiveUserId = users.firstOrNull()?.id
                        if (effectiveUserId == null) {
                            kotlinx.coroutines.flow.flowOf(emptyList())
                        } else {
                            favoriteDao.getAllFavoriteChannelIds(effectiveUserId)
                        }
                    }
                }
            }

        fun getViewerIdsWithFavorites(): Flow<List<Int>> =
            sessionManager.getCurrentUserId().flatMapLatest { userId ->
                if (userId != null) {
                    favoriteDao.getViewerIdsWithFavorites(userId)
                } else {
                    // SessionManager'da kullanıcı yoksa, ilk kullanıcıyı bul
                    userRepository.allUsers.flatMapLatest { users ->
                        val effectiveUserId = users.firstOrNull()?.id
                        if (effectiveUserId == null) {
                            kotlinx.coroutines.flow.flowOf(emptyList())
                        } else {
                            favoriteDao.getViewerIdsWithFavorites(effectiveUserId)
                        }
                    }
                }
            }
    }
