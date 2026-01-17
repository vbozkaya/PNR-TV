package com.pnr.tv.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migrations1to10 {
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

    val list =
        listOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9,
            MIGRATION_9_10,
        )
}
