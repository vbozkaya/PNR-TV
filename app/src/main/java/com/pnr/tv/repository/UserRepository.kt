package com.pnr.tv.repository

import com.pnr.tv.SessionManager
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
import timber.log.Timber
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
            
            // Kullanıcıya ait tüm verileri sil
            Timber.d("🗑️ Kullanıcı siliniyor: ${user.accountName} (ID: ${user.id})")
            Timber.d("   • Favoriler siliniyor...")
            favoriteDao.deleteAll()
            
            Timber.d("   • Son izlenenler siliniyor...")
            recentlyWatchedDao.deleteAll()
            
            Timber.d("   • Oynatma pozisyonları siliniyor...")
            playbackPositionDao.deleteAll()
            
            Timber.d("   • İzlenen bölümler siliniyor...")
            watchedEpisodeDao.clearAll()
            
            Timber.d("   • Viewer'lar siliniyor...")
            viewerDao.deleteAll()
            
            // Kullanıcıyı sil
            Timber.d("   • Kullanıcı hesabı siliniyor...")
            userDao.deleteUser(user)
            
            // Eğer silinen kullanıcı seçili kullanıcıysa, seçili kullanıcıyı temizle
            if (currentUserId == user.id) {
                Timber.d("   • Seçili kullanıcı temizleniyor (silinen kullanıcı seçiliydi)...")
                sessionManager.clearCurrentUser()
            }
            
            Timber.d("✅ Kullanıcı ve tüm verileri başarıyla silindi")
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
            Timber.d("🗑️ TÜM VERİLER TEMİZLENİYOR...")
            
            // Kullanıcı verileri
            Timber.d("   • Kullanıcılar siliniyor...")
            val allUsers = userDao.getAllUsers().firstOrNull() ?: emptyList()
            allUsers.forEach { userDao.deleteUser(it) }
            
            // Kullanıcı tercihleri
            Timber.d("   • Favoriler siliniyor...")
            favoriteDao.deleteAll()
            
            Timber.d("   • Son izlenenler siliniyor...")
            recentlyWatchedDao.deleteAll()
            
            Timber.d("   • Oynatma pozisyonları siliniyor...")
            playbackPositionDao.deleteAll()
            
            Timber.d("   • İzlenen bölümler siliniyor...")
            watchedEpisodeDao.clearAll()
            
            Timber.d("   • Viewer'lar siliniyor...")
            viewerDao.deleteAll()
            
            // İçerik verileri
            Timber.d("   • Filmler siliniyor...")
            movieDao.clearAll()
            
            Timber.d("   • Diziler siliniyor...")
            seriesDao.clearAll()
            
            Timber.d("   • Canlı yayınlar siliniyor...")
            liveStreamDao.clearAll()
            
            // Kategoriler
            Timber.d("   • Film kategorileri siliniyor...")
            movieCategoryDao.clearAll()
            
            Timber.d("   • Dizi kategorileri siliniyor...")
            seriesCategoryDao.clearAll()
            
            Timber.d("   • Canlı yayın kategorileri siliniyor...")
            liveStreamCategoryDao.clearAll()
            
            // Cache
            Timber.d("   • TMDB cache siliniyor...")
            tmdbCacheDao.clearAllCache()
            
            // Session
            Timber.d("   • Seçili kullanıcı temizleniyor...")
            sessionManager.clearCurrentUser()
            
            Timber.d("✅ TÜM VERİLER BAŞARIYLA TEMİZLENDİ")
        }
    }




