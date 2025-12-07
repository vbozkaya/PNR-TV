package com.pnr.tv.di

import android.content.Context
import androidx.room.Room
import com.pnr.tv.BuildConfig
import com.pnr.tv.db.AppDatabase
import com.pnr.tv.SessionManager
import com.pnr.tv.db.dao.FavoriteDao
import com.pnr.tv.db.dao.LiveStreamCategoryDao
import com.pnr.tv.db.dao.LiveStreamDao
import com.pnr.tv.db.dao.MovieCategoryDao
import com.pnr.tv.db.dao.MovieDao
import com.pnr.tv.db.dao.RecentlyWatchedDao
import com.pnr.tv.db.dao.SeriesCategoryDao
import com.pnr.tv.db.dao.SeriesDao
import com.pnr.tv.db.dao.TmdbCacheDao
import com.pnr.tv.db.dao.UserDao
import com.pnr.tv.db.dao.ViewerDao
import com.pnr.tv.db.dao.WatchedEpisodeDao
import com.pnr.tv.db.dao.PlaybackPositionDao
import com.pnr.tv.repository.UserRepository
import com.pnr.tv.db.migration.DatabaseMigrations
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
                .addMigrations(
                    // Tüm version geçişleri için eksiksiz migration yolu
                    DatabaseMigrations.MIGRATION_1_2,
                    DatabaseMigrations.MIGRATION_2_3,
                    DatabaseMigrations.MIGRATION_3_4,
                    DatabaseMigrations.MIGRATION_4_5,
                    DatabaseMigrations.MIGRATION_5_6,
                    DatabaseMigrations.MIGRATION_6_7,
                    DatabaseMigrations.MIGRATION_7_8,
                    DatabaseMigrations.MIGRATION_8_9,
                    DatabaseMigrations.MIGRATION_9_10,
                    DatabaseMigrations.MIGRATION_10_11,
                    DatabaseMigrations.MIGRATION_11_12,
                    DatabaseMigrations.MIGRATION_12_13,
                    DatabaseMigrations.MIGRATION_13_14,
                    DatabaseMigrations.MIGRATION_14_15,
                    DatabaseMigrations.MIGRATION_15_16,
                    DatabaseMigrations.MIGRATION_16_17,
                    DatabaseMigrations.MIGRATION_17_18,
                )

        // DEBUG modda: Migration başarısız olursa veritabanını sil ve yeniden oluştur
        // RELEASE modda: Migration başarısız olursa uygulama crash olur (veri kaybı önlenir)
        if (BuildConfig.DEBUG) {
            builder.fallbackToDestructiveMigration()
            timber.log.Timber.d("⚠️ Database: Destructive migration aktif (DEBUG mod)")
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
    ): UserRepository {
        return UserRepository(userDao, sessionManager)
    }
}
