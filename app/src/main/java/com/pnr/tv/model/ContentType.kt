package com.pnr.tv.model

import java.io.Serializable

/**
 * İçerik türlerini temsil eden enum sınıfı.
 * Uygulamada gösterilecek farklı içerik kategorilerini tanımlar.
 */
enum class ContentType : Serializable {
    LIVE_TV,
    MOVIES,
    SERIES,
}
