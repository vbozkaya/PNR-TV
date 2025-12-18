package com.pnr.tv.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "recently_watched_channels",
    foreignKeys = [
        ForeignKey(
            entity = UserAccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["userId"])],
    primaryKeys = ["channelId", "userId"],
)
data class RecentlyWatchedEntity(
    val channelId: Int,
    val userId: Int,
    val watchedAt: Long, // Zaman damgası (System.currentTimeMillis())
)
