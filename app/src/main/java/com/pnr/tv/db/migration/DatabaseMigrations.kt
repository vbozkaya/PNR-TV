package com.pnr.tv.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {
    /**
     * Migration from version 1 to 2:
     * - Add categoryId column to live_streams table
     * - Create live_stream_categories table
     */
    val MIGRATION_1_2 =
        object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add categoryId column to live_streams table
                database.execSQL("ALTER TABLE live_streams ADD COLUMN categoryId INTEGER")

                // Create live_stream_categories table
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS live_stream_categories (
                        categoryId INTEGER NOT NULL PRIMARY KEY,
                        categoryName TEXT
                    )
                    """.trimIndent(),
                )
            }
        }

    /**
     * Migration from version 2 to 3:
     * - Add sortOrder column to live_stream_categories table
     */
    val MIGRATION_2_3 =
        object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add sortOrder column to live_stream_categories table
                database.execSQL("ALTER TABLE live_stream_categories ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
            }
        }

    /**
     * Migration from version 3 to 4:
     * - Create favorite_channels table
     * - Create recently_watched_channels table
     */
    val MIGRATION_3_4 =
        object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create favorite_channels table
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS favorite_channels (
                        channelId INTEGER NOT NULL PRIMARY KEY
                    )
                    """.trimIndent(),
                )

                // Create recently_watched_channels table
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS recently_watched_channels (
                        channelId INTEGER NOT NULL PRIMARY KEY,
                        watchedAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

    /**
     * Migration from version 4 to 5:
     * - Create movies and series tables
     * Note: This version was skipped in development, added for migration path completeness
     */
    val MIGRATION_4_5 =
        object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create movies table
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS movies (
                        streamId INTEGER NOT NULL PRIMARY KEY,
                        num INTEGER NOT NULL,
                        name TEXT,
                        title TEXT,
                        year TEXT,
                        streamType TEXT,
                        streamIcon TEXT,
                        rating TEXT,
                        rating5based REAL,
                        added INTEGER,
                        categoryId TEXT,
                        containerExtension TEXT,
                        customSid TEXT,
                        directSource TEXT
                    )
                    """.trimIndent(),
                )

                // Create series table
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS series (
                        seriesId INTEGER NOT NULL PRIMARY KEY,
                        name TEXT,
                        title TEXT,
                        year TEXT,
                        cover TEXT,
                        plot TEXT,
                        cast TEXT,
                        director TEXT,
                        genre TEXT,
                        releaseDate TEXT,
                        lastModified INTEGER,
                        rating5based REAL,
                        backdropPath TEXT,
                        youtubeTrailer TEXT,
                        episodeRunTime INTEGER,
                        categoryId TEXT
                    )
                    """.trimIndent(),
                )
            }
        }

    /**
     * Migration from version 5 to 6:
     * - Create movie_categories table
     */
    val MIGRATION_5_6 =
        object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create movie_categories table
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS movie_categories (
                        categoryId INTEGER NOT NULL PRIMARY KEY,
                        categoryName TEXT,
                        sortOrder INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent(),
                )
            }
        }

    /**
     * Migration from version 6 to 7:
     * - Create series_categories table
     */
    val MIGRATION_6_7 =
        object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create series_categories table
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS series_categories (
                        categoryId INTEGER NOT NULL PRIMARY KEY,
                        categoryName TEXT,
                        sortOrder INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent(),
                )
            }
        }

    /**
     * Migration from version 7 to 8:
     * - Schema refinements and index additions
     * Note: This version was skipped in development, added for migration path completeness
     */
    val MIGRATION_7_8 =
        object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create indexes for better query performance
                database.execSQL("CREATE INDEX IF NOT EXISTS index_movies_categoryId ON movies(categoryId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_series_categoryId ON series(categoryId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_live_streams_categoryId ON live_streams(categoryId)")
            }
        }

    /**
     * Migration from version 8 to 9:
     * - Prepare for viewer system
     * Note: This version was skipped in development, added for migration path completeness
     */
    val MIGRATION_8_9 =
        object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // No schema changes in this version
                // This migration exists to maintain version continuity
            }
        }

    /**
     * Migration from version 9 to 10:
     * - Create viewers table
     * - Add viewerId column to favorite_channels table with foreign key CASCADE
     */
    val MIGRATION_9_10 =
        object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create viewers table
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS viewers (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL,
                        isDeletable INTEGER NOT NULL DEFAULT 1
                    )
                    """.trimIndent(),
                )

                // Add viewerId column to favorite_channels table
                // First, create a temporary table with the new structure
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS favorite_channels_new (
                        channelId INTEGER NOT NULL PRIMARY KEY,
                        viewerId INTEGER NOT NULL,
                        FOREIGN KEY(viewerId) REFERENCES viewers(id) ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )

                // Copy existing data to new table (assign all to viewerId = 1, which will be created if needed)
                // First, create a default viewer if it doesn't exist
                database.execSQL(
                    """
                    INSERT OR IGNORE INTO viewers (id, name, isDeletable) 
                    VALUES (1, 'Varsayılan', 0)
                    """.trimIndent(),
                )

                // Copy existing favorites to new table with viewerId = 1
                database.execSQL(
                    """
                    INSERT INTO favorite_channels_new (channelId, viewerId)
                    SELECT channelId, 1 FROM favorite_channels
                    """.trimIndent(),
                )

                // Drop old table
                database.execSQL("DROP TABLE favorite_channels")

                // Rename new table
                database.execSQL("ALTER TABLE favorite_channels_new RENAME TO favorite_channels")

                // Create index on viewerId
                database.execSQL("CREATE INDEX IF NOT EXISTS index_favorite_channels_viewerId ON favorite_channels(viewerId)")
            }
        }

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
    val MIGRATION_18_19 =
        object : Migration(18, 19) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Get first user ID or create default user
                val cursor = database.query("SELECT id FROM user_accounts ORDER BY id ASC LIMIT 1")
                val defaultUserId: Int
                if (cursor.moveToFirst()) {
                    defaultUserId = cursor.getInt(0)
                } else {
                    // Create default user if no users exist
                    database.execSQL(
                        """
                        INSERT INTO user_accounts (accountName, username, password, dns)
                        VALUES ('Varsayılan Kullanıcı', 'default', '', '')
                        """.trimIndent(),
                    )
                    val newUserCursor = database.query("SELECT id FROM user_accounts ORDER BY id DESC LIMIT 1")
                    newUserCursor.moveToFirst()
                    defaultUserId = newUserCursor.getInt(0)
                    newUserCursor.close()
                }
                cursor.close()

                // 1. Update favorite_channels table
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS favorite_channels_new (
                        channelId INTEGER NOT NULL,
                        viewerId INTEGER NOT NULL,
                        userId INTEGER NOT NULL,
                        PRIMARY KEY (channelId, viewerId, userId),
                        FOREIGN KEY(viewerId) REFERENCES viewers(id) ON DELETE CASCADE,
                        FOREIGN KEY(userId) REFERENCES user_accounts(id) ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    """
                    INSERT INTO favorite_channels_new (channelId, viewerId, userId)
                    SELECT channelId, viewerId, ? FROM favorite_channels
                    """.trimIndent(),
                    arrayOf(defaultUserId),
                )
                database.execSQL("DROP TABLE favorite_channels")
                database.execSQL("ALTER TABLE favorite_channels_new RENAME TO favorite_channels")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_favorite_channels_viewerId ON favorite_channels(viewerId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_favorite_channels_userId ON favorite_channels(userId)")

                // 2. Update recently_watched_channels table
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS recently_watched_channels_new (
                        channelId INTEGER NOT NULL,
                        userId INTEGER NOT NULL,
                        watchedAt INTEGER NOT NULL,
                        PRIMARY KEY (channelId, userId),
                        FOREIGN KEY(userId) REFERENCES user_accounts(id) ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    """
                    INSERT INTO recently_watched_channels_new (channelId, userId, watchedAt)
                    SELECT channelId, ?, watchedAt FROM recently_watched_channels
                    """.trimIndent(),
                    arrayOf(defaultUserId),
                )
                database.execSQL("DROP TABLE recently_watched_channels")
                database.execSQL("ALTER TABLE recently_watched_channels_new RENAME TO recently_watched_channels")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_recently_watched_channels_userId ON recently_watched_channels(userId)")

                // 3. Update playback_positions table
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS playback_positions_new (
                        contentId TEXT NOT NULL,
                        userId INTEGER NOT NULL,
                        positionMs INTEGER NOT NULL,
                        durationMs INTEGER NOT NULL,
                        lastUpdated INTEGER NOT NULL,
                        PRIMARY KEY (contentId, userId),
                        FOREIGN KEY(userId) REFERENCES user_accounts(id) ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    """
                    INSERT INTO playback_positions_new (contentId, userId, positionMs, durationMs, lastUpdated)
                    SELECT contentId, ?, positionMs, durationMs, lastUpdated FROM playback_positions
                    """.trimIndent(),
                    arrayOf(defaultUserId),
                )
                database.execSQL("DROP TABLE playback_positions")
                database.execSQL("ALTER TABLE playback_positions_new RENAME TO playback_positions")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_playback_positions_userId ON playback_positions(userId)")

                // 4. Update watched_episodes table
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS watched_episodes_new (
                        episodeId TEXT NOT NULL,
                        userId INTEGER NOT NULL,
                        seriesId INTEGER NOT NULL,
                        seasonNumber INTEGER NOT NULL,
                        episodeNumber INTEGER NOT NULL,
                        watchedTimestamp INTEGER NOT NULL,
                        watchProgress INTEGER NOT NULL DEFAULT 100,
                        PRIMARY KEY (episodeId, userId),
                        FOREIGN KEY(userId) REFERENCES user_accounts(id) ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    """
                    INSERT INTO watched_episodes_new (episodeId, userId, seriesId, seasonNumber, episodeNumber, watchedTimestamp, watchProgress)
                    SELECT episodeId, ?, seriesId, seasonNumber, episodeNumber, watchedTimestamp, watchProgress FROM watched_episodes
                    """.trimIndent(),
                    arrayOf(defaultUserId),
                )
                database.execSQL("DROP TABLE watched_episodes")
                database.execSQL("ALTER TABLE watched_episodes_new RENAME TO watched_episodes")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_watched_episodes_userId ON watched_episodes(userId)")

                // 5. Update viewers table
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS viewers_new (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL,
                        userId INTEGER NOT NULL,
                        isDeletable INTEGER NOT NULL DEFAULT 1,
                        FOREIGN KEY(userId) REFERENCES user_accounts(id) ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    """
                    INSERT INTO viewers_new (id, name, userId, isDeletable)
                    SELECT id, name, ?, isDeletable FROM viewers
                    """.trimIndent(),
                    arrayOf(defaultUserId),
                )
                database.execSQL("DROP TABLE viewers")
                database.execSQL("ALTER TABLE viewers_new RENAME TO viewers")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_viewers_userId ON viewers(userId)")
            }
        }
}
