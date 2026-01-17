package com.pnr.tv.network.dto

import com.pnr.tv.db.entity.LiveStreamEntity
import com.pnr.tv.util.validation.AdultContentDetector
import com.squareup.moshi.Json

data class LiveStreamDto(
    @Json(name = "stream_id") val streamId: Int?,
    @Json(name = "name") val name: String?,
    @Json(name = "stream_icon") val streamIconUrl: String?,
    @Json(name = "category_id") val categoryId: String?, // API'den string olarak geliyor
    @Json(name = "category_name") val categoryName: String?,
)

fun LiveStreamDto.toEntity(): LiveStreamEntity? =
    streamId?.let {
        // Kategori adına göre yetişkin içerik tespiti
        val isAdultFromCategory = AdultContentDetector.isAdultCategory(categoryName)

        LiveStreamEntity(
            streamId = it,
            name = name,
            streamIconUrl = streamIconUrl,
            categoryId = categoryId?.toIntOrNull(), // String'i Int'e çevir
            categoryName = categoryName,
            isAdult = isAdultFromCategory,
        )
    }
