package com.pnr.tv.util.ui

import android.content.Context
import com.pnr.tv.R

/**
 * Kategori isimlerini mevcut dile göre yerelleştiren yardımcı sınıf.
 * API'den gelen İngilizce kategori isimlerini kullanıcının seçtiği dile çevirir.
 */
object CategoryNameHelper {
    /**
     * Kategori ismini mevcut dile göre yerelleştirir.
     * Eğer kategori ismi bilinmiyorsa veya çeviri yoksa, orijinal ismi döndürür.
     *
     * @param context Context (string kaynaklarına erişmek için)
     * @param categoryName API'den gelen kategori ismi (genellikle İngilizce)
     * @return Yerelleştirilmiş kategori ismi
     */
    fun getLocalizedCategoryName(
        context: Context,
        categoryName: String?,
    ): String {
        if (categoryName.isNullOrBlank()) {
            return ""
        }

        // Kategori ismini normalize et (trim, lowercase)
        val normalizedName = categoryName.trim().lowercase()

        // Kategori ismine göre string resource ID'sini bul
        val stringResId =
            when {
                normalizedName == "action" || normalizedName == "aksiyon" -> R.string.category_action
                normalizedName == "adventure" || normalizedName == "macera" -> R.string.category_adventure
                normalizedName == "animation" || normalizedName == "animasyon" -> R.string.category_animation
                normalizedName == "comedy" || normalizedName == "komedi" -> R.string.category_comedy
                normalizedName == "crime" || normalizedName == "suç" -> R.string.category_crime
                normalizedName == "documentary" || normalizedName == "belgesel" -> R.string.category_documentary
                normalizedName == "drama" -> R.string.category_drama
                normalizedName == "family" || normalizedName == "aile" -> R.string.category_family
                normalizedName == "fantasy" || normalizedName == "fantastik" -> R.string.category_fantasy
                normalizedName == "horror" || normalizedName == "korku" -> R.string.category_horror
                normalizedName == "music" || normalizedName == "müzik" -> R.string.category_music
                normalizedName == "mystery" || normalizedName == "gizem" -> R.string.category_mystery
                normalizedName == "romance" || normalizedName == "romantik" || normalizedName == "aşk" -> R.string.category_romance
                normalizedName.contains("science fiction") || normalizedName.contains("sci-fi") || normalizedName.contains("bilim kurgu") || normalizedName.contains("bilimkurgu") -> R.string.category_science_fiction
                normalizedName == "thriller" || normalizedName == "gerilim" -> R.string.category_thriller
                normalizedName == "war" || normalizedName == "savaş" -> R.string.category_war
                normalizedName == "western" || normalizedName == "batı" || normalizedName == "kovboy" -> R.string.category_western
                normalizedName == "sports" || normalizedName == "spor" -> R.string.category_sports
                normalizedName == "reality" || normalizedName == "gerçek" || normalizedName == "realite" -> R.string.category_reality
                normalizedName.contains("talk show") || normalizedName.contains("sohbet programı") -> R.string.category_talk_show
                normalizedName == "news" || normalizedName == "haber" || normalizedName == "haberler" -> R.string.category_news
                normalizedName == "kids" || normalizedName == "çocuk" || normalizedName == "çocuklar" -> R.string.category_kids
                normalizedName.contains("tv movie") || normalizedName.contains("tv film") || normalizedName.contains("tv filmi") -> R.string.category_tv_movie
                normalizedName == "all" || normalizedName == "tümü" || normalizedName == "hepsi" -> R.string.category_all
                else -> null
            }

        // Eğer çeviri bulunduysa, string resource'dan al
        return if (stringResId != null) {
            try {
                context.getString(stringResId)
            } catch (e: Exception) {
                // String resource bulunamazsa orijinal ismi döndür
                categoryName
            }
        } else {
            // Bilinmeyen kategori ismi için orijinal ismi döndür
            categoryName
        }
    }
}
