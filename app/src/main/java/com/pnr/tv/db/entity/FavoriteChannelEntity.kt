package com.pnr.tv.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "favorite_channels",
    foreignKeys = [
        ForeignKey(
            entity = ViewerEntity::class,
            parentColumns = ["id"],
            childColumns = ["viewerId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = UserAccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["viewerId"]), Index(value = ["userId"])],
    primaryKeys = ["channelId", "viewerId", "userId"],
)
data class FavoriteChannelEntity(
    val channelId: Int,
    val viewerId: Int,
    val userId: Int,
)
