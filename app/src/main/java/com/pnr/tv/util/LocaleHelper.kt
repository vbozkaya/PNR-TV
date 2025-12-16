package com.pnr.tv.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

/**
 * Cihaz dili ve bölge bilgilerini almak için yardımcı sınıf
 * Dil değiştirme ve kaydetme özellikleri içerir
 */
object LocaleHelper {
    private const val PREFS_NAME = "locale_prefs"
    private const val PREFS_KEY_LANGUAGE = "selected_language"

    // Desteklenen diller
    enum class SupportedLanguage(val code: String, val androidCode: String, val displayName: String) {
        TURKISH("tr", "tr", "Türkçe"),
        ENGLISH("en", "en", "English"),
        SPANISH("es", "es", "Español"),
        INDONESIAN("id", "in", "Bahasa Indonesia"), // Android'de Endonezce için "in" kullanılır
        HINDI("hi", "hi", "हिन्दी"),
        PORTUGUESE("pt", "pt", "Português"),
        FRENCH("fr", "fr", "Français"),
        ;

        companion object {
            fun fromCode(code: String): SupportedLanguage? {
                return values().find { it.code == code }
            }
        }
    }

    /**
     * Kaydedilmiş dili yükler, yoksa cihaz dilini kontrol eder.
     * Cihaz dili destekleniyorsa onu kullanır, desteklenmiyorsa İngilizce'yi varsayılan olarak kullanır.
     */
    fun getSavedLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedLanguage = prefs.getString(PREFS_KEY_LANGUAGE, null)

        // Kaydedilmiş dil varsa onu döndür
        if (savedLanguage != null) {
            return savedLanguage
        }

        // Kaydedilmiş dil yoksa, cihaz dilini kontrol et
        val deviceLanguage = getDeviceLanguage(context)

        // Cihaz dili destekleniyorsa onu kullan
        if (SupportedLanguage.fromCode(deviceLanguage) != null) {
            return deviceLanguage
        }

        // Cihaz dili desteklenmiyorsa İngilizce'yi varsayılan olarak kullan
        return SupportedLanguage.ENGLISH.code
    }

    /**
     * Dil kaydeder
     */
    fun saveLanguage(
        context: Context,
        languageCode: String,
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(PREFS_KEY_LANGUAGE, languageCode).apply()
    }

    /**
     * Context'i seçili dile göre wrap eder
     */
    @SuppressLint("NewApi")
    fun wrapContext(context: Context): Context {
        val language = getSavedLanguage(context)
        // Android locale kodunu al
        val androidCode = SupportedLanguage.fromCode(language)?.androidCode ?: language
        val locale = Locale(androidCode)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(locale)
            return context.createConfigurationContext(config)
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
            return context
        }
    }

    /**
     * Cihazın mevcut dilini ve bölgesini TMDB formatında döndürür
     * Örnek: "tr-TR", "en-US", "pt-BR", "fr-CA"
     *
     * @return TMDB API için dil-bölge kodu
     */
    fun getDeviceLanguageWithRegion(context: Context): String {
        val locale = getCurrentLocale(context)
        val language = locale.language // Örn: "tr", "en", "pt"
        val country = locale.country // Örn: "TR", "US", "BR"

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
