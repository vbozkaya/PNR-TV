package com.pnr.tv.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recently_watched_channels")
data class RecentlyWatchedEntity(
    @PrimaryKey val channelId: Int,
    val watchedAt: Long, // Zaman damgası (System.currentTimeMillis())
)
