package com.pnr.tv.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.pnr.tv.model.CategoryItem

@Entity(tableName = "live_stream_categories")
data class LiveStreamCategoryEntity(
    @PrimaryKey @ColumnInfo(name = "categoryId") val categoryIdInt: Int,
    override val categoryName: String?,
    val sortOrder: Int = 0, // Kaynaktan gelen sırayı korumak için
) : CategoryItem {
    // CategoryItem interface requires String categoryId, convert from Int
    override val categoryId: String get() = categoryIdInt.toString()
}
