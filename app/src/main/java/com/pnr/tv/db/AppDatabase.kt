package com.pnr.tv.db

import androidx.room.Database
import androidx.room.RoomDatabase
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
import com.pnr.tv.db.entity.FavoriteChannelEntity
import com.pnr.tv.db.entity.LiveStreamCategoryEntity
import com.pnr.tv.db.entity.LiveStreamEntity
import com.pnr.tv.db.entity.MovieCategoryEntity
import com.pnr.tv.db.entity.MovieEntity
import com.pnr.tv.db.entity.PlaybackPositionEntity
import com.pnr.tv.db.entity.RecentlyWatchedEntity
import com.pnr.tv.db.entity.SeriesCategoryEntity
import com.pnr.tv.db.entity.SeriesEntity
import com.pnr.tv.db.entity.TmdbCacheEntity
import com.pnr.tv.db.entity.UserAccountEntity
import com.pnr.tv.db.entity.ViewerEntity
import com.pnr.tv.db.entity.WatchedEpisodeEntity

@Database(
    entities = [
        LiveStreamEntity::class,
        LiveStreamCategoryEntity::class,
        MovieEntity::class,
        MovieCategoryEntity::class,
        SeriesEntity::class,
        SeriesCategoryEntity::class,
        FavoriteChannelEntity::class,
        RecentlyWatchedEntity::class,
        ViewerEntity::class,
        UserAccountEntity::class,
        TmdbCacheEntity::class,
        WatchedEpisodeEntity::class,
        PlaybackPositionEntity::class,
    ],
    version = 22, // Incremented version from 21 to 22 - Added categoryId indices to movies, series, and live_streams tables
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun liveStreamDao(): LiveStreamDao

    abstract fun liveStreamCategoryDao(): LiveStreamCategoryDao

    abstract fun movieDao(): MovieDao

    abstract fun movieCategoryDao(): MovieCategoryDao

    abstract fun seriesDao(): SeriesDao

    abstract fun seriesCategoryDao(): SeriesCategoryDao

    abstract fun favoriteDao(): FavoriteDao

    abstract fun recentlyWatchedDao(): RecentlyWatchedDao

    abstract fun viewerDao(): ViewerDao

    abstract fun userDao(): UserDao

    abstract fun tmdbCacheDao(): TmdbCacheDao

    abstract fun watchedEpisodeDao(): WatchedEpisodeDao

    abstract fun playbackPositionDao(): PlaybackPositionDao
}
