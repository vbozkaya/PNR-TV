package com.pnr.tv.network

/**
 * API action parametreleri için sabit değerler.
 * Bu object, API çağrılarında kullanılan "action" parametrelerinin
 * yazım hatalarına karşı korunmasını ve merkezi yönetimini sağlar.
 */
object ApiActions {
    /**
     * VOD (Video on Demand) - Film içerikleri için action'lar
     */
    const val GET_VOD_STREAMS = "get_vod_streams"
    const val GET_VOD_CATEGORIES = "get_vod_categories"

    /**
     * Dizi içerikleri için action'lar
     */
    const val GET_SERIES = "get_series"
    const val GET_SERIES_CATEGORIES = "get_series_categories"
    const val GET_SERIES_INFO = "get_series_info"

    /**
     * Canlı yayın içerikleri için action'lar
     */
    const val GET_LIVE_STREAMS = "get_live_streams"
    const val GET_LIVE_CATEGORIES = "get_live_categories"
}




