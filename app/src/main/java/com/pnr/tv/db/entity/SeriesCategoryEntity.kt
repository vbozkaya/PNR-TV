package com.pnr.tv.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.pnr.tv.model.CategoryItem

@Entity(tableName = "series_categories")
data class SeriesCategoryEntity(
    @PrimaryKey override val categoryId: String,
    override val categoryName: String?,
    val parentId: Int,
    val sortOrder: Int,
) : CategoryItem
