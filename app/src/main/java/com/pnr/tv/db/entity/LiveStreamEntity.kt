package com.pnr.tv.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.pnr.tv.model.ContentItem

@Entity(
    tableName = "live_streams",
    indices = [Index(value = ["categoryId"])],
)
data class LiveStreamEntity(
    @PrimaryKey val streamId: Int,
    val name: String?,
    val streamIconUrl: String?,
    val categoryId: Int?,
    val categoryName: String?,
    val isAdult: Boolean? = null, // Yetişkin içerik işareti
) : ContentItem {
    override val id: Int get() = streamId
    override val title: String get() = name ?: ""
    override val imageUrl: String? get() = streamIconUrl
}
