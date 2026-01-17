package com.pnr.tv.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migrations21to22 {
    /**
     * Migration from version 20 to 21:
     * - Add isAdult column to live_streams table
     */
    val MIGRATION_20_21 =
        object : Migration(20, 21) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add isAdult column to live_streams table (INTEGER: 0 = false, 1 = true, NULL = null)
                database.execSQL("ALTER TABLE live_streams ADD COLUMN isAdult INTEGER")
            }
        }

    /**
     * Migration from version 21 to 22:
     * - Add categoryId indices to movies, series, and live_streams tables for better query performance
     * Note: These indices might already exist from MIGRATION_7_8, but adding them here ensures consistency
     * Room will handle this gracefully with IF NOT EXISTS behavior
     */
    val MIGRATION_21_22 =
        object : Migration(21, 22) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create indexes for categoryId columns (IF NOT EXISTS ensures no error if already present)
                // These indices significantly improve query performance for category-based queries
                database.execSQL("CREATE INDEX IF NOT EXISTS index_movies_categoryId ON movies(categoryId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_series_categoryId ON series(categoryId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_live_streams_categoryId ON live_streams(categoryId)")
            }
        }

    val list =
        listOf(
            MIGRATION_20_21,
            MIGRATION_21_22,
        )
}
