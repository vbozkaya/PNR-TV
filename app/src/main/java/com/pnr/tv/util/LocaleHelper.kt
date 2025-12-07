package com.pnr.tv.util

import android.content.Context
import android.os.Build
import java.util.Locale

/**
 * Cihaz dili ve bölge bilgilerini almak için yardımcı sınıf
 */
object LocaleHelper {
    /**
     * Cihazın mevcut dilini ve bölgesini TMDB formatında döndürür
     * Örnek: "tr-TR", "en-US", "pt-BR", "fr-CA"
     * 
     * @return TMDB API için dil-bölge kodu
     */
    fun getDeviceLanguageWithRegion(context: Context): String {
        val locale = getCurrentLocale(context)
        val language = locale.language // Örn: "tr", "en", "pt"
        val country = locale.country   // Örn: "TR", "US", "BR"
        
        return if (country.isNotEmpty()) {
            "$language-$country" // Örn: "tr-TR", "pt-BR"
        } else {
            "$language-${language.uppercase()}" // Örn: "en-EN" (fallback)
        }
    }

    /**
     * Cihazın mevcut dilini döndürür (sadece dil kodu, bölge yok)
     * Örnek: "tr", "en", "pt", "fr"
     * 
     * @return TMDB API için dil kodu
     */
    fun getDeviceLanguage(context: Context): String {
        val locale = getCurrentLocale(context)
        return locale.language
    }

    /**
     * Android sürümüne göre doğru Locale nesnesini döndürür
     */
    private fun getCurrentLocale(context: Context): Locale {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.resources.configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale
        }
    }

    /**
     * TMDB API için standart fallback dil zincirini döndürür
     * 
     * Basit ve etkili 2 adımlı fallback:
     * 1. Cihaz dili (tr-TR, pt-BR, vb.)
     * 2. İngilizce (en-US) - global standart
     * 3. Orijinal dil - runtime'da eklenir
     * 
     * Örnek:
     * Cihaz: tr-TR → ["tr-TR", "en-US"]
     * Cihaz: pt-BR → ["pt-BR", "en-US"]
     * Cihaz: en-US → ["en-US"]
     * 
     * @return Dil kodları listesi (öncelik sırasına göre)
     */
    fun getLanguageFallbackChain(context: Context): List<String> {
        val deviceLanguageWithRegion = getDeviceLanguageWithRegion(context)
        val deviceLanguageOnly = getDeviceLanguage(context)
        
        val chain = mutableListOf<String>()
        
        // 1. Cihaz dili + bölge (örn: "tr-TR", "pt-BR")
        chain.add(deviceLanguageWithRegion)
        
        // 2. İngilizce (global standart) - cihaz dili İngilizce değilse ekle
        if (deviceLanguageOnly != "en") {
            chain.add("en-US")
        }
        
        return chain
    }
}

