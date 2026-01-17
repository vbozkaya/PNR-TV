package com.pnr.tv.util.error

/**
 * Hata context bilgisi için data class.
 * Hatanın oluştuğu yeri ve koşulları detaylı olarak tanımlar.
 * Crashlytics'e güvenli bilgiler eklemek için kullanılır (PII içermez).
 */
data class ErrorContext(
    /**
     * Hatanın oluştuğu repository veya class adı.
     * Örnek: "MovieRepository", "BaseContentRepository"
     */
    val repository: String? = null,
    /**
     * Hatanın oluştuğu operasyon adı.
     * Örnek: "loadMovies", "refreshCategories", "getMovieDetails"
     */
    val operation: String? = null,
    /**
     * İlgili kategori ID'si (varsa).
     * NOT: Kullanıcı verisi değil, sadece kategori tanımlayıcısı.
     */
    val categoryId: String? = null,
    /**
     * İlgili içerik ID'si (varsa).
     * NOT: Kullanıcı verisi değil, sadece içerik tanımlayıcısı.
     */
    val contentId: String? = null,
    /**
     * Ek bilgiler (key-value çiftleri).
     * NOT: Kullanıcı verileri (username, password, DNS, email) kesinlikle eklenmez.
     */
    val additionalInfo: Map<String, String> = emptyMap(),
) {
    /**
     * Builder pattern ile ErrorContext oluşturmayı kolaylaştırır.
     */
    class Builder {
        private var repository: String? = null
        private var operation: String? = null
        private var categoryId: String? = null
        private var contentId: String? = null
        private val additionalInfo = mutableMapOf<String, String>()

        /**
         * Repository adını ayarlar.
         */
        fun setRepository(repository: String) =
            apply {
                this.repository = repository
            }

        /**
         * Operasyon adını ayarlar.
         */
        fun setOperation(operation: String) =
            apply {
                this.operation = operation
            }

        /**
         * Kategori ID'sini ayarlar.
         */
        fun setCategoryId(categoryId: String) =
            apply {
                this.categoryId = categoryId
            }

        /**
         * İçerik ID'sini ayarlar.
         */
        fun setContentId(contentId: String) =
            apply {
                this.contentId = contentId
            }

        /**
         * Ek bilgi ekler.
         * NOT: Kullanıcı verileri (username, password, DNS, email) kesinlikle eklenmez.
         */
        fun addInfo(
            key: String,
            value: String,
        ) = apply {
            additionalInfo[key] = value
        }

        /**
         * ErrorContext'i oluşturur.
         */
        fun build(): ErrorContext {
            return ErrorContext(
                repository = repository,
                operation = operation,
                categoryId = categoryId,
                contentId = contentId,
                additionalInfo = additionalInfo,
            )
        }
    }

    /**
     * Crashlytics'e gönderilecek context string'ini oluşturur.
     * PII içermez, sadece teknik bilgiler.
     */
    fun toCrashlyticsContext(): String {
        return buildString {
            repository?.let { append("Repository: $it") }
            operation?.let {
                if (isNotEmpty()) append(", ")
                append("Operation: $it")
            }
            categoryId?.let {
                if (isNotEmpty()) append(", ")
                append("CategoryId: $it")
            }
            contentId?.let {
                if (isNotEmpty()) append(", ")
                append("ContentId: $it")
            }
            if (additionalInfo.isNotEmpty()) {
                if (isNotEmpty()) append(", ")
                append("AdditionalInfo: ${additionalInfo.entries.joinToString(", ") { "${it.key}=${it.value}" }}")
            }
        }
    }

    /**
     * Basit string context (geriye dönük uyumluluk için).
     * Sadece repository adını döndürür.
     */
    fun toSimpleContext(): String? {
        return repository
    }
}
