package com.pnr.tv.model

/**
 * Generic content item interface.
 * Represents any piece of content (movie, series, etc.) that can be displayed in the browse screen.
 */
interface ContentItem {
    /**
     * Stable ID for the content item.
     */
    val id: Int

    /**
     * Display name/title of the content.
     */
    val title: String

    /**
     * Poster/thumbnail URL for the content.
     */
    val imageUrl: String?
}
