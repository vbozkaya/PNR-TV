package com.pnr.tv.model

import java.io.Serializable

/**
 * İçerik sıralama seçeneklerini temsil eden enum.
 */
enum class SortOrder : Serializable {
    A_TO_Z, // A'dan Z'ye sırala
    Z_TO_A, // Z'den A'ya sırala
    RATING_HIGH_TO_LOW, // Yüksek Puandan Düşüğe Sırala
    RATING_LOW_TO_HIGH, // Düşük Puandan Yükseğe Sırala
    DATE_NEW_TO_OLD, // Eklenme Tarihi Yeniden Eskiye
    DATE_OLD_TO_NEW, // Eklenme Tarihi Eskiden Yeniye
}
