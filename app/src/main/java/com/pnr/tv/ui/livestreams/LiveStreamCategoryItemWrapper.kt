package com.pnr.tv.ui.livestreams

import com.pnr.tv.db.entity.LiveStreamCategoryEntity
import com.pnr.tv.model.CategoryItem

/**
 * LiveStreamCategoryEntity'yi CategoryItem interface'ine uyumlu hale getiren wrapper sınıf.
 * Int categoryId'yi String'e çevirir.
 */
data class LiveStreamCategoryItemWrapper(
    private val entity: LiveStreamCategoryEntity,
) : CategoryItem {
    override val categoryId: String
        get() = entity.categoryId.toString()

    override val categoryName: String?
        get() = entity.categoryName

    /**
     * Orijinal entity'yi döndürür.
     */
    fun toEntity(): LiveStreamCategoryEntity = entity

    /**
     * Orijinal Int categoryId'yi döndürür.
     */
    fun getIntCategoryId(): Int = entity.categoryIdInt
}
