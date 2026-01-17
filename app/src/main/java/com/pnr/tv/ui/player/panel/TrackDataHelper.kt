package com.pnr.tv.ui.player.panel

import android.content.Context
import com.pnr.tv.R
import com.pnr.tv.ui.player.state.TrackInfo

/**
 * Track veri işleme mantığını yöneten helper sınıf.
 * Altyazı ve ses track'lerinin yüklenmesi, "Kapalı" seçeneğinin eklenmesi
 * ve seçili track'in belirlenmesi işlemlerini yapar.
 */
class TrackDataHelper(private val context: Context) {

    /**
     * Alt yazı track'lerini yükler ve işler.
     * "Kapalı" seçeneğini ekler ve seçili track'i belirler.
     *
     * @param subtitleTracks ViewModel'den gelen alt yazı track'leri
     * @param currentSelectedTrack Şu anda seçili olan track (null olabilir)
     * @return İşlenmiş track listesi ve seçili track bilgisi
     */
    fun processSubtitleTracks(
        subtitleTracks: List<TrackInfo>,
        currentSelectedTrack: TrackInfo?,
    ): TrackDataResult {
        val tracks = subtitleTracks.toMutableList()
        val currentSubtitleTrack = tracks.firstOrNull { it.isSelected }
        val finalSelectedTrack = currentSubtitleTrack ?: currentSelectedTrack

        // Eğer hiç alt yazı seçili değilse, "Kapalı" seçeneğini ekle
        if (finalSelectedTrack == null) {
            val closedOption =
                TrackInfo(
                    groupIndex = -1,
                    trackIndex = -1,
                    language = null,
                    label = context.getString(R.string.player_subtitles_off),
                    rawLabel = null,
                    isSelected = true,
                )
            tracks.add(0, closedOption)
            return TrackDataResult(tracks, closedOption)
        } else {
            // "Kapalı" seçeneğini ekle (seçili değil)
            val hasClosedOption = tracks.any { it.groupIndex == -1 }
            if (!hasClosedOption) {
                val closedOption =
                    TrackInfo(
                        groupIndex = -1,
                        trackIndex = -1,
                        language = null,
                        label = context.getString(R.string.player_subtitles_off),
                        rawLabel = null,
                        isSelected = false,
                    )
                tracks.add(0, closedOption)
            }
            return TrackDataResult(tracks, finalSelectedTrack)
        }
    }

    /**
     * Ses track'lerini yükler ve işler.
     * Seçili track'i belirler.
     *
     * @param audioTracks ViewModel'den gelen ses track'leri
     * @param currentSelectedTrack Şu anda seçili olan track (null olabilir)
     * @return İşlenmiş track listesi ve seçili track bilgisi
     */
    fun processAudioTracks(
        audioTracks: List<TrackInfo>,
        currentSelectedTrack: TrackInfo?,
    ): TrackDataResult {
        val currentAudioTrack = audioTracks.firstOrNull { it.isSelected }
        val finalSelectedTrack = currentAudioTrack ?: currentSelectedTrack
        return TrackDataResult(audioTracks, finalSelectedTrack)
    }
}

/**
 * Track veri işleme sonucu.
 *
 * @param tracks İşlenmiş track listesi
 * @param selectedTrack Seçili track (null olabilir)
 */
data class TrackDataResult(
    val tracks: List<TrackInfo>,
    val selectedTrack: TrackInfo?,
)
