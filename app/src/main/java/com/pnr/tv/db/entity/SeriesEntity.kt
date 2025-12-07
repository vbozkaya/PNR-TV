package com.pnr.tv.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.pnr.tv.model.ContentItem

@Entity(tableName = "series")
data class SeriesEntity(
    @PrimaryKey val streamId: Int,
    val name: String?,
    val coverUrl: String?,
    val rating: Double?,
    val plot: String?,
    val releaseDate: String?,
    val categoryId: String?, // Added categoryId
    val added: String?,
    val tmdbId: Int?, // TMDB dizi ID'si
) : ContentItem {
    override val id: Int get() = streamId
    override val title: String get() = name ?: ""
    override val imageUrl: String? get() = coverUrl
    // rating property zaten data class'ta var, override etmeye gerek yok
}
