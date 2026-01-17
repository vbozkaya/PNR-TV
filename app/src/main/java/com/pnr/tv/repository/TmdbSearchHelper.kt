package com.pnr.tv.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * TMDB arama işlemleri için string temizleme ve işleme yardımcı sınıfı
 */
class TmdbSearchHelper
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        /**
         * Film adını TMDB araması için temizler
         * - Parantez içindeki yıl bilgisini çıkarır: "Film Adı (2025)" -> "Film Adı"
         * - Türkçe karakterleri İngilizce karşılıklarına çevirir
         * - Gereksiz boşlukları temizler
         */
        fun cleanMovieTitle(title: String): String {
            return title
                // Parantez içindeki her şeyi çıkar (genellikle yıl bilgisi)
                .replace(Regex("\\s*\\([^)]*\\)\\s*"), " ")
                // Türkçe karakterleri İngilizce'ye çevir (bazı durumlarda yardımcı olabilir)
                .replace("ı", "i")
                .replace("İ", "I")
                .replace("ğ", "g")
                .replace("Ğ", "G")
                .replace("ü", "u")
                .replace("Ü", "U")
                .replace("ş", "s")
                .replace("Ş", "S")
                .replace("ö", "o")
                .replace("Ö", "O")
                .replace("ç", "c")
                .replace("Ç", "C")
                // Birden fazla boşluğu tek boşluğa indir
                .replace(Regex("\\s+"), " ")
                .trim()
        }

        /**
         * Film adının ana kısmını alır (iki nokta üst üsteden önceki kısım)
         * Örnek: "Predator: Vahşi Topraklar" -> "Predator"
         */
        fun getMainTitle(title: String): String {
            return title.split(":").firstOrNull()?.trim() ?: title
        }

        /**
         * Film adından yıl bilgisini çıkarır
         */
        fun extractYear(title: String): Int? {
            val yearRegex = Regex("\\((\\d{4})\\)")
            return yearRegex.find(title)?.groupValues?.get(1)?.toIntOrNull()
        }
    }
