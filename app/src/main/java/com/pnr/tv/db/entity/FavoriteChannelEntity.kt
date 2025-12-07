package com.pnr.tv.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "favorite_channels",
    foreignKeys = [
        ForeignKey(
            entity = ViewerEntity::class,
            parentColumns = ["id"],
            childColumns = ["viewerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["viewerId"])],
)
data class FavoriteChannelEntity(
    @PrimaryKey val channelId: Int,
    val viewerId: Int,
)
