package com.pnr.tv.ui.player

/**
 * Ses veya alt yazı track bilgisi
 */
data class TrackInfo(
    val groupIndex: Int,      // TrackGroup indeksi
    val trackIndex: Int,      // Track indeksi
    val language: String?,    // Dil kodu (örn: "tr", "en")
    val label: String?,       // Label (örn: "Turkish", "English")
    val isSelected: Boolean   // Şu anda seçili mi?
)
