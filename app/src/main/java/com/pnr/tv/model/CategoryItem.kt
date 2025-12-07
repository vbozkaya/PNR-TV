package com.pnr.tv.model

/**
 * Generic category item interface.
 * Represents any category (movie category, series category, etc.) that can be displayed in the browse screen.
 */
interface CategoryItem {
    /**
     * Category ID (String or Int, consistent with existing entities).
     */
    val categoryId: String

    /**
     * Display name of the category.
     * Can be null, but should be handled with a default empty string when displaying.
     */
    val categoryName: String?
}
