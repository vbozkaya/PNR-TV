package com.pnr.tv.ui.movies

import com.pnr.tv.db.entity.MovieEntity
import com.pnr.tv.network.dto.TmdbMovieDetailsDto

/**
 * Film detay sayfası için UI durumlarını temsil eden sealed class
 * 
 * Bu yapı, ekranın tüm olası durumlarını (yükleme, başarı, hata) 
 * tek bir tip altında toplar ve Fragment'te kolay yönetim sağlar.
 */
sealed class MovieDetailUiState {
    /**
     * İlk durum - Henüz veri yüklenmedi
     */
    object Initial : MovieDetailUiState()

    /**
     * Yükleme durumu - Film ve TMDB verileri yükleniyor
     */
    object Loading : MovieDetailUiState()

    /**
     * Başarı durumu - Veriler başarıyla yüklendi
     * 
     * @param movie IPTV'den gelen film bilgileri
     * @param tmdbDetails TMDB'den gelen detaylar (nullable - yoksa null)
     * @param director Yönetmen adı (TMDB veya cache'den)
     * @param genre Film türü (TMDB'den - örn: "Action, Sci-Fi")
     * @param cast Oyuncular listesi (TMDB veya cache'den)
     * @param overview Film açıklaması (TMDB veya orijinal)
     */
    data class Success(
        val movie: MovieEntity,
        val tmdbDetails: TmdbMovieDetailsDto?,
        val director: String?,
        val genre: String?,
        val cast: String?,
        val overview: String?
    ) : MovieDetailUiState()

    /**
     * Hata durumu - Veri yüklenirken hata oluştu
     * 
     * @param message Hata mesajı
     * @param exception İstisna nesnesi (debug için)
     */
    data class Error(
        val message: String,
        val exception: Throwable? = null
    ) : MovieDetailUiState()
}

