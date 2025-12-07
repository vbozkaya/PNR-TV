package com.pnr.tv.network.dto

import com.pnr.tv.db.entity.LiveStreamCategoryEntity
import com.squareup.moshi.Json

data class LiveStreamCategoryDto(
    // API'den string olarak geliyor: "365"
    @Json(name = "category_id") val categoryId: String?,
    @Json(name = "category_name") val categoryName: String?,
    @Json(name = "parent_id") val parentId: Int? = null, // Opsiyonel field
)

fun LiveStreamCategoryDto.toEntity(sortOrder: Int = 0): LiveStreamCategoryEntity? {
    // categoryId string olarak geliyor, Int'e çevir
    val id = categoryId?.toIntOrNull()

    // categoryId ve categoryName varsa entity oluştur
    return if (id != null && categoryName != null) {
        LiveStreamCategoryEntity(
            categoryIdInt = id,
            categoryName = categoryName.trim(), // Başındaki/sonundaki boşlukları temizle
            sortOrder = sortOrder, // Kaynaktan gelen sırayı korumak için
        )
    } else {
        null
    }
}
