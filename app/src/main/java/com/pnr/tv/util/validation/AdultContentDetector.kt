package com.pnr.tv.util.validation

/**
 * Yetişkin içerik tespiti için utility sınıfı.
 * Kategori adına göre yetişkin içerik olup olmadığını belirler.
 */
object AdultContentDetector {
    /**
     * Yetişkin içerik kategorisi olabilecek anahtar kelimeler.
     * Büyük/küçük harf duyarsız kontrol yapılır.
     */
    private val adultCategoryKeywords =
        setOf(
            "adult",
            "18+",
            "+18",
            "18 plus",
            "porn",
            "porno",
            "erotic",
            "erotik",
            "mature",
            "sexy",
            "seks",
            "nsfw",
            "not safe for work",
            "explicit",
            "explicit content",
            "yetiskin",
            "yetişkin",
            "olgun",
            "mature content",
        )

    /**
     * Yetişkin içerik kategorisi pattern'leri (daha spesifik kontrol için).
     * Bu pattern'ler kategori adının başında veya belirli bir yerde olmalı.
     * NOT: Sadece "XXX" (3 tane X) adult içerik olarak kabul edilir. "XX" veya tek "X" adult içerik değildir.
     */
    private val adultCategoryPatterns =
        listOf(
            Regex(".*v/m\\s*x{3}.*", RegexOption.IGNORE_CASE), // "V/M xXx" pattern'i (örnek: "V/M xXx ∞ ANALVİDS") - sadece 3 tane X
            Regex(
                "\\b[x]{3}\\b(?!\\w)",
                RegexOption.IGNORE_CASE,
            ), // Sadece "xxx", "xXx", "XXX" (tam olarak 3 tane X) - "XX" veya "XXEN" gibi kelimeleri yakalamaz
            Regex(".*\\+18.*", RegexOption.IGNORE_CASE), // "+18" pattern'i
        )

    /**
     * İstisna listesi: Yetişkin içerik olmayan ancak pattern'lere takılabilecek kategori/şirket adları.
     * Bu liste kontrol edildikten sonra pattern kontrolü yapılır.
     */
    private val exceptionKeywords =
        setOf(
            "exxen", // Türk dijital platform
            "xxen", // EXXEN varyasyonu (€XXEN, EXXEN gibi)
            "exxon", // Petrol şirketi
            "lexx", // Dizi adı
        )

    /**
     * Kategori adına göre yetişkin içerik olup olmadığını kontrol eder.
     * @param categoryName Kategori adı (null olabilir)
     * @return true ise yetişkin içerik, false ise değil, null ise belirsiz
     */
    fun isAdultCategory(categoryName: String?): Boolean? {
        if (categoryName.isNullOrBlank()) {
            return null
        }

        val lowerCategoryName = categoryName.lowercase().trim()

        // Önce istisna listesini kontrol et - eğer istisna listedeyse yetişkin içerik değildir
        // Özel karakterleri temizleyerek kontrol et (€, ∞ gibi semboller için)
        val normalizedCategoryName = lowerCategoryName.replace(Regex("[^a-z0-9\\s]"), "")
        val isException =
            exceptionKeywords.any { exception ->
                lowerCategoryName.contains(exception, ignoreCase = true) ||
                    normalizedCategoryName.contains(exception, ignoreCase = true)
            }
        if (isException) {
            return null // İstisna listesindeyse yetişkin içerik değil
        }

        // Sonra pattern'leri kontrol et (daha spesifik)
        val matchesPattern =
            adultCategoryPatterns.any { pattern ->
                pattern.find(categoryName) != null // matches yerine find kullan (kelime sınırları için)
            }

        // Son olarak anahtar kelimeleri kontrol et
        val matchesKeyword =
            adultCategoryKeywords.any { keyword ->
                lowerCategoryName.contains(keyword, ignoreCase = true)
            }

        val isAdult = matchesPattern || matchesKeyword

        return if (isAdult) true else null
    }

    /**
     * İçerik adına göre yetişkin içerik olup olmadığını kontrol eder.
     * Bu daha riskli bir yöntemdir, sadece kategori kontrolü yeterli değilse kullanılabilir.
     * NOT: Sadece "XXX" (3 tane X) adult içerik olarak kabul edilir. "XX" veya tek "X" adult içerik değildir.
     */
    fun isAdultContentByName(contentName: String?): Boolean? {
        if (contentName.isNullOrBlank()) {
            return null
        }

        val lowerContentName = contentName.lowercase().trim()

        // Önce istisna listesini kontrol et - eğer istisna listedeyse yetişkin içerik değildir
        // Özel karakterleri temizleyerek kontrol et (€, ∞ gibi semboller için)
        val normalizedContentName = lowerContentName.replace(Regex("[^a-z0-9\\s]"), "")
        val isException =
            exceptionKeywords.any { exception ->
                lowerContentName.contains(exception, ignoreCase = true) ||
                    normalizedContentName.contains(exception, ignoreCase = true)
            }
        if (isException) {
            return null // İstisna listesindeyse yetişkin içerik değil
        }

        // Sonra pattern'leri kontrol et (sadece XXX - 3 tane X)
        val matchesPattern =
            adultCategoryPatterns.any { pattern ->
                pattern.find(contentName) != null // matches yerine find kullan (kelime sınırları için)
            }

        // Son olarak anahtar kelimeleri kontrol et
        val matchesKeyword =
            adultCategoryKeywords.any { keyword ->
                lowerContentName.contains(keyword, ignoreCase = true)
            }

        val isAdult = matchesPattern || matchesKeyword

        return if (isAdult) true else null
    }
}
