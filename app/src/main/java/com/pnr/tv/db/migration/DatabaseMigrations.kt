package com.pnr.tv.db.migration

import androidx.room.migration.Migration

/**
 * Database Migrations Registry
 *
 * This object serves as a central registry that combines all migration groups.
 * Individual migrations are organized into separate files by version ranges:
 * - Migrations1to10: Versions 1-10
 * - Migrations11to20: Versions 11-20
 * - Migrations21to22: Versions 21-22
 *
 * Complex migrations (like Migration18to19) are extracted into their own classes
 * for better readability and maintainability.
 */
object DatabaseMigrations {
    /**
     * All migrations combined from all version ranges.
     * This list is used by Room to apply migrations in order.
     */
    val ALL_MIGRATIONS: List<Migration> = Migrations1to10.list + Migrations11to20.list + Migrations21to22.list

    // Individual migration references for backward compatibility
    // These delegate to the grouped migrations
    val MIGRATION_1_2 = Migrations1to10.MIGRATION_1_2
    val MIGRATION_2_3 = Migrations1to10.MIGRATION_2_3
    val MIGRATION_3_4 = Migrations1to10.MIGRATION_3_4
    val MIGRATION_4_5 = Migrations1to10.MIGRATION_4_5
    val MIGRATION_5_6 = Migrations1to10.MIGRATION_5_6
    val MIGRATION_6_7 = Migrations1to10.MIGRATION_6_7
    val MIGRATION_7_8 = Migrations1to10.MIGRATION_7_8
    val MIGRATION_8_9 = Migrations1to10.MIGRATION_8_9
    val MIGRATION_9_10 = Migrations1to10.MIGRATION_9_10
    val MIGRATION_10_11 = Migrations11to20.MIGRATION_10_11
    val MIGRATION_11_12 = Migrations11to20.MIGRATION_11_12
    val MIGRATION_12_13 = Migrations11to20.MIGRATION_12_13
    val MIGRATION_13_14 = Migrations11to20.MIGRATION_13_14
    val MIGRATION_14_15 = Migrations11to20.MIGRATION_14_15
    val MIGRATION_15_16 = Migrations11to20.MIGRATION_15_16
    val MIGRATION_16_17 = Migrations11to20.MIGRATION_16_17
    val MIGRATION_17_18 = Migrations11to20.MIGRATION_17_18
    val MIGRATION_18_19 = Migrations11to20.MIGRATION_18_19
    val MIGRATION_19_20 = Migrations11to20.MIGRATION_19_20
    val MIGRATION_20_21 = Migrations21to22.MIGRATION_20_21
    val MIGRATION_21_22 = Migrations21to22.MIGRATION_21_22
}
