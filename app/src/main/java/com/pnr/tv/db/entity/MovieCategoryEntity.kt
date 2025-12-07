package com.pnr.tv.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.pnr.tv.model.CategoryItem

@Entity(tableName = "movie_categories")
data class MovieCategoryEntity(
    @PrimaryKey override val categoryId: String,
    override val categoryName: String?,
    val parentId: Int,
    val sortOrder: Int,
) : CategoryItem
