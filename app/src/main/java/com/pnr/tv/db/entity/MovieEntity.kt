package com.pnr.tv.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.pnr.tv.model.ContentItem

@Entity(
    tableName = "movies",
    indices = [Index(value = ["categoryId"])],
)
data class MovieEntity(
    @PrimaryKey val streamId: Int,
    val name: String?,
    val streamIconUrl: String?,
    val rating: Double?,
    val plot: String?,
    val categoryId: String?, // Added categoryId
    val added: String?,
    val tmdbId: Int?, // TMDB film ID'si
    val containerExtension: String?, // Container format (ts, mp4, mkv, etc.)
    val isAdult: Boolean? = null, // Yetişkin içerik işareti
) : ContentItem {
    override val id: Int get() = streamId
    override val title: String get() = name ?: ""
    override val imageUrl: String? get() = streamIconUrl
    // rating property zaten data class'ta var, override etmeye gerek yok
}
