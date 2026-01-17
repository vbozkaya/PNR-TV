package com.pnr.tv.repository

import com.pnr.tv.ui.main.SessionManager
import com.pnr.tv.db.dao.FavoriteDao
import com.pnr.tv.db.dao.LiveStreamCategoryDao
import com.pnr.tv.db.dao.LiveStreamDao
import com.pnr.tv.db.dao.MovieCategoryDao
import com.pnr.tv.db.dao.MovieDao
import com.pnr.tv.db.dao.PlaybackPositionDao
import com.pnr.tv.db.dao.RecentlyWatchedDao
import com.pnr.tv.db.dao.SeriesCategoryDao
import com.pnr.tv.db.dao.SeriesDao
import com.pnr.tv.db.dao.TmdbCacheDao
import com.pnr.tv.db.dao.UserDao
import com.pnr.tv.db.dao.ViewerDao
import com.pnr.tv.db.dao.WatchedEpisodeDao
import com.pnr.tv.db.entity.UserAccountEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

class UserRepository
    @Inject
    constructor(
        private val userDao: UserDao,
        private val sessionManager: SessionManager,
        private val favoriteDao: FavoriteDao,
        private val recentlyWatchedDao: RecentlyWatchedDao,
        private val playbackPositionDao: PlaybackPositionDao,
        private val watchedEpisodeDao: WatchedEpisodeDao,
        private val viewerDao: ViewerDao,
        private val movieDao: MovieDao,
        private val seriesDao: SeriesDao,
        private val liveStreamDao: LiveStreamDao,
        private val movieCategoryDao: MovieCategoryDao,
        private val seriesCategoryDao: SeriesCategoryDao,
        private val liveStreamCategoryDao: LiveStreamCategoryDao,
        private val tmdbCacheDao: TmdbCacheDao,
    ) {
        val allUsers: Flow<List<UserAccountEntity>> = userDao.getAllUsers()

        @OptIn(ExperimentalCoroutinesApi::class)
        val currentUser: Flow<UserAccountEntity?> =
            sessionManager.getCurrentUserId().flatMapLatest { userId ->
                if (userId == null) {
                    kotlinx.coroutines.flow.flowOf(null)
                } else {
                    userDao.getUserById(userId)
                }
            }

        suspend fun addUser(user: UserAccountEntity): Long {
            return userDao.insertUser(user)
        }

        suspend fun updateUser(user: UserAccountEntity) {
            userDao.updateUser(user)
        }

        /**
         * Kullanıcıyı ve kullanıcıya ait tüm verileri siler:
         * - Favoriler
         * - Son izlenenler
         * - Oynatma pozisyonları
         * - İzlenen bölümler
         * - Viewer'lar (ve viewer'lara bağlı favoriler CASCADE ile silinir)
         *
         * Eğer silinen kullanıcı seçili kullanıcıysa, seçili kullanıcıyı da temizler.
         */
        suspend fun deleteUser(user: UserAccountEntity) {
            val currentUserId = sessionManager.getCurrentUserId().firstOrNull()
            val userId = user.id

            // Kullanıcıya ait tüm verileri sil
            favoriteDao.deleteByUserId(userId)
            recentlyWatchedDao.deleteByUserId(userId)
            playbackPositionDao.deleteByUserId(userId)
            watchedEpisodeDao.deleteByUserId(userId)
            viewerDao.deleteByUserId(userId)

            // Kullanıcıyı sil
            userDao.deleteUser(user)

            // Eğer silinen kullanıcı seçili kullanıcıysa, seçili kullanıcıyı temizle
            if (currentUserId == userId) {
                sessionManager.clearCurrentUser()
            }
        }

        suspend fun setCurrentUser(user: UserAccountEntity) {
            sessionManager.saveCurrentUser(user.id)
        }

        /**
         * Tüm verileri temizler (cache ve tüm içerikler dahil).
         * Uygulama yeni kurulmuş gibi açılır.
         *
         * Silinen veriler:
         * - Tüm kullanıcılar
         * - Tüm favoriler
         * - Tüm son izlenenler
         * - Tüm oynatma pozisyonları
         * - Tüm izlenen bölümler
         * - Tüm viewer'lar
         * - Tüm filmler
         * - Tüm diziler
         * - Tüm canlı yayınlar
         * - Tüm kategoriler (film, dizi, canlı yayın)
         * - TMDB cache
         * - Seçili kullanıcı (SessionManager)
         */
        suspend fun clearAllData() {
            // Kullanıcı verileri
            val allUsers = userDao.getAllUsers().firstOrNull() ?: emptyList()
            allUsers.forEach { userDao.deleteUser(it) }

            // Kullanıcı tercihleri
            favoriteDao.deleteAll()
            recentlyWatchedDao.deleteAll()
            playbackPositionDao.deleteAll()

            watchedEpisodeDao.clearAll()
            viewerDao.deleteAll()

            // İçerik verileri
            movieDao.clearAll()
            seriesDao.clearAll()
            liveStreamDao.clearAll()

            // Kategoriler
            movieCategoryDao.clearAll()
            seriesCategoryDao.clearAll()
            liveStreamCategoryDao.clearAll()

            // Cache
            tmdbCacheDao.clearAllCache()

            // Session
            sessionManager.clearCurrentUser()
        }
    }
