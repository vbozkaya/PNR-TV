package com.pnr.tv.ui.player.state

/**
 * Ses veya alt yazı track bilgisi
 */
data class TrackInfo(
    val groupIndex: Int, // TrackGroup indeksi
    val trackIndex: Int, // Track indeksi
    val language: String?, // Dil kodu (örn: "tr", "en")
    val label: String?, // UI'da görünen kullanıcı dostu isim (örn: "Turkish", "English")
    val rawLabel: String?, // Videodan gelen orijinal ham etiket (örn: null, "Stereo", boş string)
    val isSelected: Boolean, // Şu anda seçili mi?
)
