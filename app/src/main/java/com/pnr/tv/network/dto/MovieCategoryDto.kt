package com.pnr.tv.network.dto

import com.squareup.moshi.Json

data class MovieCategoryDto(
    // API'den string olarak geliyor: "1440"
    @Json(name = "category_id") val categoryId: String?,
    @Json(name = "category_name") val categoryName: String?,
    @Json(name = "parent_id") val parentId: Int? = null, // Opsiyonel field
) {
    fun getCategoryIdAsString(): String? = categoryId
}
