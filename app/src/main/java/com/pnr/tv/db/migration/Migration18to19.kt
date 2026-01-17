package com.pnr.tv.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from version 18 to 19:
 * - Add userId column to all user-specific tables for proper data isolation
 * - Update primary keys to composite keys including userId
 * - Migrate existing data to first user (if exists) or create default user
 */
class Migration18to19 : Migration(18, 19) {
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
