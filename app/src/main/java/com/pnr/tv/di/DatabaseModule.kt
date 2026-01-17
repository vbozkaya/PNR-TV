package com.pnr.tv.di

import android.content.Context
import androidx.room.Room
import com.pnr.tv.BuildConfig
import com.pnr.tv.ui.main.SessionManager
import com.pnr.tv.db.AppDatabase
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
import com.pnr.tv.db.migration.DatabaseMigrations
import com.pnr.tv.repository.UserRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase {
        val builder =
            Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                "pnr-tv-database",
            )
                .addMigrations(*DatabaseMigrations.ALL_MIGRATIONS.toTypedArray())

        // DEBUG modda: Migration başarısız olursa veritabanını sil ve yeniden oluştur
        // RELEASE modda: Migration başarısız olursa uygulama crash olur (veri kaybı önlenir)
        if (BuildConfig.DEBUG) {
            builder.fallbackToDestructiveMigration()
        } else {
            // Production'da migration başarısız olursa crash et
            // Bu sayede veri kaybı önlenir ve hatayı görebiliriz
            timber.log.Timber.w("⚠️ Database: Destructive migration KAPALI (RELEASE mod)")
        }

        return builder.build()
    }

    @Provides
    fun provideLiveStreamDao(database: AppDatabase): LiveStreamDao = database.liveStreamDao()

    @Provides
    fun provideLiveStreamCategoryDao(database: AppDatabase): LiveStreamCategoryDao = database.liveStreamCategoryDao()

    @Provides
    fun provideMovieDao(database: AppDatabase): MovieDao = database.movieDao()

    @Provides
    fun provideMovieCategoryDao(database: AppDatabase): MovieCategoryDao = database.movieCategoryDao()

    @Provides
    fun provideSeriesDao(database: AppDatabase): SeriesDao = database.seriesDao()

    @Provides
    fun provideSeriesCategoryDao(database: AppDatabase): SeriesCategoryDao = database.seriesCategoryDao()

    @Provides
    fun provideFavoriteDao(database: AppDatabase): FavoriteDao = database.favoriteDao()

    @Provides
    fun provideRecentlyWatchedDao(database: AppDatabase): RecentlyWatchedDao = database.recentlyWatchedDao()

    @Provides
    fun provideViewerDao(database: AppDatabase): ViewerDao = database.viewerDao()

    @Provides
    fun provideUserDao(database: AppDatabase): UserDao = database.userDao()

    @Provides
    fun provideTmdbCacheDao(database: AppDatabase): TmdbCacheDao = database.tmdbCacheDao()

    @Provides
    fun provideWatchedEpisodeDao(database: AppDatabase): WatchedEpisodeDao = database.watchedEpisodeDao()

    @Provides
    fun providePlaybackPositionDao(database: AppDatabase): PlaybackPositionDao = database.playbackPositionDao()

    @Provides
    @Singleton
    fun provideUserRepository(
        userDao: UserDao,
        sessionManager: SessionManager,
        favoriteDao: FavoriteDao,
        recentlyWatchedDao: RecentlyWatchedDao,
        playbackPositionDao: PlaybackPositionDao,
        watchedEpisodeDao: WatchedEpisodeDao,
        viewerDao: ViewerDao,
        movieDao: MovieDao,
        seriesDao: SeriesDao,
        liveStreamDao: LiveStreamDao,
        movieCategoryDao: MovieCategoryDao,
        seriesCategoryDao: SeriesCategoryDao,
        liveStreamCategoryDao: LiveStreamCategoryDao,
        tmdbCacheDao: TmdbCacheDao,
    ): UserRepository {
        return UserRepository(
            userDao,
            sessionManager,
            favoriteDao,
            recentlyWatchedDao,
            playbackPositionDao,
            watchedEpisodeDao,
            viewerDao,
            movieDao,
            seriesDao,
            liveStreamDao,
            movieCategoryDao,
            seriesCategoryDao,
            liveStreamCategoryDao,
            tmdbCacheDao,
        )
    }
}
