package com.pnr.tv.ui.player.manager

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.pnr.tv.R
import com.pnr.tv.ui.player.state.TrackInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject

/**
 * Player track yönetimi için manager sınıfı.
 * Ses ve alt yazı track'lerinin listelenmesi ve seçilmesi işlemlerini yönetir.
 */
class PlayerTrackManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        companion object {
            private const val TAG = "PlayerTrackManager"
            // Off/Kapalı durumu için özel tag - groupIndex == -1 olduğunda bu tag kullanılır
            private const val DISABLE_TAG = "__DISABLE__"
        }
        
        @Volatile
        private var currentTracks: Tracks? = null
        
        // Kalıcı seçim hafızası - Kullanıcının son seçtiği track label'ı
        // Bu hafıza sadece yeni bir seçim yapıldığında güncellenir, handleTracksChanged'de silinmez
        // Label kullanılıyor çünkü aynı dile sahip birden fazla track grubu olabilir (örn: Almanca ve Almanca [CC])
        private var pendingAudioSelection: String? = null // track label (eşleşme için label kullanılır)
        private var pendingSubtitleSelection: String? = null // track label (null = kapalı)

        /**
         * Tracks değiştiğinde çağrılmalıdır.
         * onTracksChanged callback'inden bu metoda yönlendirme yapılmalıdır.
         * NOT: Bu metod sadece currentTracks'i günceller. pendingAudioSelection ve pendingSubtitleSelection
         * asla burada silinmez - bu hafıza sadece kullanıcı yeni bir seçim yaptığında manuel olarak güncellenir.
         */
        fun handleTracksChanged(tracks: Tracks) {
            Log.d(TAG, "handleTracksChanged: START - tracks.groups.size=${tracks.groups.size}, currentTracks was null=${currentTracks == null}, thread=${Thread.currentThread().name}")
            for ((index, group) in tracks.groups.withIndex()) {
                Log.d(TAG, "handleTracksChanged: groups[$index]: type=${group.type}, isSupported=${group.isSupported}, isSelected=${group.isSelected}")
            }
            val oldTracks = currentTracks
            currentTracks = tracks
            Log.d(TAG, "handleTracksChanged: END - currentTracks updated, oldTracks=null=${oldTracks == null}, newTracks=null=${currentTracks == null}, thread=${Thread.currentThread().name}")
        }

        /**
         * Ses dillerini getirir
         * player.currentTracks üzerinden C.TRACK_TYPE_AUDIO olanları filtreler
         */
        @UnstableApi
        fun getAudioTracks(player: ExoPlayer? = null): List<TrackInfo> {
            // Önce currentTracks'i kullan, yoksa player'dan direkt al (fallback)
            val tracks = currentTracks ?: player?.currentTracks ?: return emptyList()

            val audioTracks = mutableListOf<TrackInfo>()
            var audioTrackIndex = 0 // Tüm audio track'ler için global index (fallback için)
            var groupIndex = 0 // Audio group index

            for (group in tracks.groups) {
                if (group.type == C.TRACK_TYPE_AUDIO) {
                    // Sadece isSupported olan group'ları işle
                    if (!group.isSupported) {
                        continue
                    }
                    val trackGroup = group.mediaTrackGroup
                    for (i in 0 until trackGroup.length) {
                        
                        val format = trackGroup.getFormat(i)
                        val language = format.language
                        val formatLabel = format.label
                        
                        // Display label hesapla (önce formatLabel, yoksa language'dan, yoksa fallback)
                        val displayLabel =
                            if (!formatLabel.isNullOrBlank()) {
                                formatLabel.trim()
                            } else if (!language.isNullOrBlank()) {
                                getLanguageDisplayName(language)
                            } else {
                                // Fallback: "Unknown Audio ${index}" formatında ad ver
                                context.getString(R.string.unknown) + " Audio ${audioTrackIndex + 1}"
                            }
                        
                        // Label bazlı kesin eşleşme - formatLabel veya displayLabel ile pendingSelection karşılaştır
                        // Tam metin eşleşmesi yapılır, sadece dil koduna bakılmaz
                        val isSelected = if (pendingAudioSelection != null && pendingAudioSelection != DISABLE_TAG) {
                            // Pending selection var ve "Off" değil - label ile kesin eşleşme kontrol et
                            (formatLabel?.trim() == pendingAudioSelection) || (displayLabel == pendingAudioSelection)
                        } else if (pendingAudioSelection == DISABLE_TAG) {
                            // "Off" durumu - hiçbir track seçili olmamalı
                            false
                        } else {
                            // Pending selection yok - ExoPlayer'ın isSelected değerini kullan
                            group.isSelected && group.isTrackSelected(i)
                        }

                        audioTracks.add(
                            TrackInfo(
                                groupIndex = groupIndex,
                                trackIndex = i,
                                language = language,
                                label = displayLabel,
                                rawLabel = formatLabel, // Videodan gelen orijinal ham etiket
                                isSelected = isSelected,
                            ),
                        )
                        audioTrackIndex++
                    }
                    groupIndex++
                }
            }

            // distinctBy yerine tüm track'leri döndür (null language'ları da dahil et)
            // Ancak aynı language ve label'a sahip duplicate'leri kaldır
            val distinctTracks = audioTracks.distinctBy { 
                // Language ve label kombinasyonu ile distinct yap
                // Eğer ikisi de null ise, groupIndex ve trackIndex ile ayırt et
                if (it.language.isNullOrBlank() && it.label.isNullOrBlank()) {
                    "${it.groupIndex}_${it.trackIndex}"
                } else {
                    "${it.language ?: ""}_${it.label ?: ""}"
                }
            }
            return distinctTracks
        }

        /**
         * Alt yazı dillerini getirir
         */
        @UnstableApi
        fun getSubtitleTracks(player: ExoPlayer? = null): List<TrackInfo> {
            // Önce currentTracks'i kullan, yoksa player'dan direkt al (fallback)
            val tracks = currentTracks ?: player?.currentTracks
            
            if (tracks == null) {
                Log.d(TAG, "getSubtitleTracks: FAILED - currentTracks=null, player.currentTracks=${player?.currentTracks}, player=${player}")
                
                // Boş liste kontrolü - eğer player varsa ama currentTracks null ise tüm durumu logla
                player?.let { exoPlayer ->
                    val playerTracks = exoPlayer.currentTracks
                    Log.d(TAG, "getSubtitleTracks: player.currentTracks=$playerTracks, hasTracks=${playerTracks != null}")
                    if (playerTracks != null) {
                        Log.d(TAG, "getSubtitleTracks: playerTracks.groups.size=${playerTracks.groups.size}")
                        for ((index, group) in playerTracks.groups.withIndex()) {
                            Log.d(TAG, "getSubtitleTracks: allGroups[$index]: type=${group.type}, isSupported=${group.isSupported}, isSelected=${group.isSelected}")
                        }
                    }
                }
                return emptyList()
            }

            Log.d(TAG, "getSubtitleTracks: START - tracks.groups.size=${tracks.groups.size}, pendingSubtitleSelection=$pendingSubtitleSelection, currentTracks was null=${currentTracks == null}, tracks source=${if (currentTracks != null) "currentTracks" else "player.currentTracks"}, thread=${Thread.currentThread().name}")

            val subtitleTracks = mutableListOf<TrackInfo>()
            var textGroupIndex = 0 // Sadece TEXT grupları için index

            for ((absoluteIndex, group) in tracks.groups.withIndex()) {
                if (group.type == C.TRACK_TYPE_TEXT) {
                    // Sadece isSupported olan group'ları işle
                    if (!group.isSupported) {
                        Log.d(TAG, "getSubtitleTracks: absoluteIndex=$absoluteIndex, textGroupIndex=$textGroupIndex, type=${group.type}, isSupported=false - SKIP")
                        continue
                    }
                    val trackGroup = group.mediaTrackGroup

                    Log.d(TAG, "getSubtitleTracks: Processing TEXT group - absoluteIndex=$absoluteIndex, textGroupIndex=$textGroupIndex, trackCount=${trackGroup.length}")

                    for (i in 0 until trackGroup.length) {
                        
                        val format = trackGroup.getFormat(i)
                        val language = format.language
                        val formatLabel = format.label
                        val sampleMimeType = format.sampleMimeType
                        val formatId = format.id

                        // Format bilgilerini logla
                        Log.d(TAG, "getSubtitleTracks: absoluteIndex=$absoluteIndex, textGroupIndex=$textGroupIndex, trackIndex=$i, language=$language, label=$formatLabel, mimeType=$sampleMimeType, id=$formatId, isSelected=${group.isSelected && group.isTrackSelected(i)}")

                        // Display label hesapla (önce formatLabel, yoksa language'dan, yoksa fallback)
                        val displayLabel =
                            if (!formatLabel.isNullOrBlank()) {
                                formatLabel.trim()
                            } else if (!language.isNullOrBlank()) {
                                getLanguageDisplayName(language)
                            } else {
                                context.getString(R.string.unknown)
                            }
                        
                        // Label bazlı kesin eşleşme - formatLabel veya displayLabel ile pendingSelection karşılaştır
                        // Tam metin eşleşmesi yapılır, sadece dil koduna bakılmaz
                        // Not: pendingSubtitleSelection DISABLE_TAG ise (altyazı kapalı), hiçbir track seçili olmamalı
                        val isPendingMatch = if (pendingSubtitleSelection != null && pendingSubtitleSelection != DISABLE_TAG) {
                            // Pending selection var ve "Off" değil - label ile kesin eşleşme kontrol et
                            (formatLabel?.trim() == pendingSubtitleSelection) || (displayLabel == pendingSubtitleSelection)
                        } else {
                            false
                        }
                        
                        // Seçim mantığı: Eşleşme bulunamazsa bile ExoPlayer'ın isSelected'ını kullan
                        val isSelected = when {
                            pendingSubtitleSelection == DISABLE_TAG -> {
                                // "Off" durumu - hiçbir track seçili olmamalı
                                Log.d(TAG, "getSubtitleTracks: isSelected=false (DISABLE_TAG)")
                                false
                            }
                            isPendingMatch -> {
                                // Pending selection ile eşleşme var
                                Log.d(TAG, "getSubtitleTracks: isSelected=true (pendingMatch)")
                                true
                            }
                            else -> {
                                // Pending selection yok veya eşleşme yok - ExoPlayer'ın isSelected değerini kullan
                                val exoPlayerSelected = group.isSelected && group.isTrackSelected(i)
                                Log.d(TAG, "getSubtitleTracks: isSelected=$exoPlayerSelected (from ExoPlayer: group.isSelected=${group.isSelected}, group.isTrackSelected($i)=${group.isTrackSelected(i)})")
                                exoPlayerSelected
                            }
                        }

                        subtitleTracks.add(
                            TrackInfo(
                                groupIndex = textGroupIndex,
                                trackIndex = i,
                                language = language,
                                label = displayLabel,
                                rawLabel = formatLabel, // Videodan gelen orijinal ham etiket
                                isSelected = isSelected,
                            ),
                        )
                    }
                    textGroupIndex++
                } else {
                    // TEXT olmayan grup - tüm grup tiplerini logla
                    Log.d(TAG, "getSubtitleTracks: absoluteIndex=$absoluteIndex, type=${group.type} (not TEXT) - SKIP")
                }
            }

            // Filtreleri gevşet: Benzersiz anahtar kullanarak distinct yap
            val distinctTracks = subtitleTracks.distinctBy { "${it.language}_${it.label}_${it.groupIndex}" }
            
            // Boş liste kontrolü - eğer hiç track yoksa tüm grupları logla
            if (distinctTracks.isEmpty()) {
                Log.d(TAG, "getSubtitleTracks: NO TRACKS FOUND after filtering - subtitleTracks.size=${subtitleTracks.size}, distinctTracks.size=${distinctTracks.size}")
                Log.d(TAG, "getSubtitleTracks: logging all groups from tracks:")
                for ((index, group) in tracks.groups.withIndex()) {
                    Log.d(TAG, "getSubtitleTracks: allGroups[$index]: type=${group.type}, isSupported=${group.isSupported}, isSelected=${group.isSelected}")
                    if (group.type == C.TRACK_TYPE_TEXT) {
                        Log.d(TAG, "getSubtitleTracks: TEXT group[$index] found but not added - isSupported=${group.isSupported}, mediaTrackGroup.length=${group.mediaTrackGroup.length}")
                    }
                }
            } else {
                Log.d(TAG, "getSubtitleTracks: SUCCESS - found ${distinctTracks.size} tracks (subtitleTracks.size=${subtitleTracks.size})")
            }
            
            return distinctTracks
        }

        /**
         * Ses dilini seçer
         * player.trackSelectionParameters API'sini kullanarak ExoPlayer'ın ana motoruna doğrudan emir verir
         */
        @UnstableApi
        fun selectAudioTrack(
            player: ExoPlayer,
            trackInfo: TrackInfo,
        ) {
            try {
                val currentParameters = player.trackSelectionParameters
                val newParameters = currentParameters.buildUpon()
                    .setPreferredAudioLanguage(trackInfo.language)
                    .build()
                
                player.trackSelectionParameters = newParameters
                // Label'ı hafızaya kaydet - language yerine label kullanılıyor çünkü aynı dile sahip birden fazla track olabilir
                pendingAudioSelection = trackInfo.label
                
                player.currentTracks?.let { handleTracksChanged(it) }
            } catch (e: Exception) {
                // Hata durumunda mevcut parametreleri koru
            }
        }

        /**
         * Alt yazıyı seçer (null = alt yazıyı kapat)
         * player.trackSelectionParameters API'sini kullanarak ExoPlayer'ın ana motoruna doğrudan emir verir
         */
        @UnstableApi
        fun selectSubtitleTrack(
            player: ExoPlayer,
            trackInfo: TrackInfo?,
        ) {
            try {
                if (trackInfo == null) {
                    val currentParameters = player.trackSelectionParameters
                    val newParameters = currentParameters.buildUpon()
                        .setPreferredTextLanguage(null)
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        .build()
                    
                    player.trackSelectionParameters = newParameters
                    // "Off" durumu için özel tag kullan
                    pendingSubtitleSelection = DISABLE_TAG
                } else {
                    // Altyazı track'ini seçmek için kapsamlı parametre ayarları
                    val currentParameters = player.trackSelectionParameters
                    val newParameters = currentParameters.buildUpon()
                        .setPreferredTextLanguage(trackInfo.language)
                        .setIgnoredTextSelectionFlags(0)
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false) // Zorla aktif et
                        .build()
                    
                    player.trackSelectionParameters = newParameters
                    
                    // Ekstra güvence: Seçim parametrelerini tekrar zorla aktif et
                    // Bazı streamlerde altyazı seçilse bile kanal 'disabled' kalabiliyor
                    val forcedParameters = player.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .build()
                    player.trackSelectionParameters = forcedParameters
                    
                    // Tracks değişikliğini tetikle - ExoPlayer'ın seçimi uygulamasını garanti et
                    player.currentTracks?.let { 
                        handleTracksChanged(it)
                        // Tracks değişikliğinden sonra tekrar parametreleri uygula
                        val finalParameters = player.trackSelectionParameters.buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                            .build()
                        player.trackSelectionParameters = finalParameters
                    }
                    
                    // Label'ı hafızaya kaydet - language yerine label kullanılıyor çünkü aynı dile sahip birden fazla track olabilir
                    pendingSubtitleSelection = trackInfo.label
                }
            } catch (e: Exception) {
                // Hata durumunda mevcut parametreleri koru
            }
        }

        /**
         * Dil kodunu tam dil adına çevirir.
         * Uygulamanın mevcut diline göre dil ismini gösterir.
         * Örnek: Uygulama İngilizceyse "ru" -> "Russian", Türkçeyse "ru" -> "Rusça"
         */
        private fun getLanguageDisplayName(languageCode: String?): String {
            if (languageCode.isNullOrBlank()) return context.getString(R.string.unknown)

            return try {
                val locale = Locale(languageCode)
                // Uygulamanın mevcut dilini al
                val currentLocale = getCurrentLocale()
                // Mevcut uygulama dilinde dil ismini göster
                locale.getDisplayLanguage(currentLocale)
            } catch (e: Exception) {
                languageCode.uppercase()
            }
        }

        /**
         * Uygulamanın mevcut locale'ini döndürür
         */
        private fun getCurrentLocale(): Locale {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                context.resources.configuration.locales[0]
            } else {
                @Suppress("DEPRECATION")
                context.resources.configuration.locale
            }
        }
    }
