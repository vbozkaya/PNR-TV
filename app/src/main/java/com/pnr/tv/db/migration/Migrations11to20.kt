package com.pnr.tv.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migrations11to20 {
    /**
     * Migration from version 10 to 11:
     * - Add rating column to series table
     */
    val MIGRATION_10_11 =
        object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add rating column to series table
                database.execSQL("ALTER TABLE series ADD COLUMN rating REAL")
            }
        }

    /**
     * Migration from version 11 to 12:
     * - Create user_accounts table (unified from separate database)
     */
    val MIGRATION_11_12 =
        object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create user_accounts table
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS user_accounts (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        accountName TEXT NOT NULL,
                        username TEXT NOT NULL,
                        password TEXT NOT NULL,
                        dns TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

    /**
     * Migration from version 12 to 13:
     * - Add tmdbId column to movies table
     */
    val MIGRATION_12_13 =
        object : Migration(12, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add tmdbId column to movies table
                database.execSQL("ALTER TABLE movies ADD COLUMN tmdbId INTEGER")
            }
        }

    /**
     * Migration from version 13 to 14:
     * - Create tmdb_cache table for caching TMDB API responses
     */
    val MIGRATION_13_14 =
        object : Migration(13, 14) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create tmdb_cache table
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS tmdb_cache (
                        tmdbId INTEGER NOT NULL PRIMARY KEY,
                        title TEXT,
                        director TEXT,
                        cast TEXT,
                        overview TEXT,
                        cacheTime INTEGER NOT NULL,
                        rawJson TEXT
                    )
                    """.trimIndent(),
                )
            }
        }

    /**
     * Migration from version 14 to 15:
     * - Add tmdbId column to series table
     */
    val MIGRATION_14_15 =
        object : Migration(14, 15) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add tmdbId column to series table
                database.execSQL("ALTER TABLE series ADD COLUMN tmdbId INTEGER")
            }
        }

    /**
     * Migration from version 15 to 16:
     * - Create watched_episodes table for tracking watched episodes
     */
    val MIGRATION_15_16 =
        object : Migration(15, 16) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create watched_episodes table
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS watched_episodes (
                        episodeId TEXT NOT NULL PRIMARY KEY,
                        seriesId INTEGER NOT NULL,
                        seasonNumber INTEGER NOT NULL,
                        episodeNumber INTEGER NOT NULL,
                        watchedTimestamp INTEGER NOT NULL,
                        watchProgress INTEGER NOT NULL DEFAULT 100
                    )
                    """.trimIndent(),
                )
            }
        }

    /**
     * Migration from version 16 to 17:
     * - Add containerExtension column to movies table for proper VOD URL formatting
     */
    val MIGRATION_16_17 =
        object : Migration(16, 17) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add containerExtension column to movies table
                database.execSQL("ALTER TABLE movies ADD COLUMN containerExtension TEXT")
            }
        }

    /**
     * Migration from version 17 to 18:
     * - Create playback_positions table for resume playback feature
     */
    val MIGRATION_17_18 =
        object : Migration(17, 18) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create playback_positions table
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS playback_positions (
                        contentId TEXT NOT NULL PRIMARY KEY,
                        positionMs INTEGER NOT NULL,
                        durationMs INTEGER NOT NULL,
                        lastUpdated INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

    /**
     * Migration from version 18 to 19:
     * - Add userId column to all user-specific tables for proper data isolation
     * - Update primary keys to composite keys including userId
     * - Migrate existing data to first user (if exists) or create default user
     */
    val MIGRATION_18_19 = Migration18to19()

    /**
     * Migration from version 19 to 20:
     * - Add isAdult column to movies table
     * - Add isAdult column to series table
     */
    val MIGRATION_19_20 =
        object : Migration(19, 20) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add isAdult column to movies table (INTEGER: 0 = false, 1 = true, NULL = null)
                database.execSQL("ALTER TABLE movies ADD COLUMN isAdult INTEGER")

                // Add isAdult column to series table (INTEGER: 0 = false, 1 = true, NULL = null)
                database.execSQL("ALTER TABLE series ADD COLUMN isAdult INTEGER")
            }
        }

    val list =
        listOf(
            MIGRATION_10_11,
            MIGRATION_11_12,
            MIGRATION_12_13,
            MIGRATION_13_14,
            MIGRATION_14_15,
            MIGRATION_15_16,
            MIGRATION_16_17,
            MIGRATION_17_18,
            MIGRATION_18_19,
            MIGRATION_19_20,
        )
}
