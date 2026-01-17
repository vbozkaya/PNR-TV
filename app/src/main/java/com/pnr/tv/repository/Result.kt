package com.pnr.tv.repository

import com.pnr.tv.util.error.ErrorSeverity

/**
 * Ağ ve veritabanı işlemlerinin sonuçlarını temsil eden sealed class.
 * İşlemin başarılı olup olmadığını ve veriyi/hata bilgisini taşır.
 */
sealed class Result<out T> {
    /**
     * İşlem başarılı olduğunda döner, sonuç verisini içerir.
     */
    data class Success<out T>(val data: T) : Result<T>()

    /**
     * Kısmi başarı durumu.
     * Bazı veriler yüklendi, bazıları yüklenemedi.
     * Örnek: 10 filmden 8'i yüklendi, 2'si yüklenemedi.
     */
    data class PartialSuccess<out T>(
        /**
         * Başarıyla yüklenen veri.
         */
        val data: T,
        /**
         * Yüklenemeyen item'ların ID'leri veya tanımlayıcıları.
         */
        val failedItems: List<String> = emptyList(),
        /**
         * Kısmi başarısızlık için hata mesajı.
         */
        val errorMessage: String? = null,
        /**
         * Toplam item sayısı.
         */
        val totalItems: Int? = null,
        /**
         * Başarıyla yüklenen item sayısı.
         */
        val successCount: Int? = null,
    ) : Result<T>()

    /**
     * İşlem başarısız olduğunda döner, hata mesajını ve exception'ı içerir.
     */
    data class Error(
        val message: String,
        val exception: Throwable? = null,
        /**
         * Hata şiddeti. Hatanın otomatik kapanma süresini belirler.
         * Varsayılan: MEDIUM (5 saniye)
         */
        val severity: ErrorSeverity = ErrorSeverity.MEDIUM,
    ) : Result<Nothing>()

    /**
     * İşlemin başarılı olup olmadığını kontrol eder.
     * PartialSuccess de başarılı sayılır (veri var).
     */
    val isSuccess: Boolean
        get() = this is Success || this is PartialSuccess

    /**
     * İşlemin başarısız olup olmadığını kontrol eder.
     */
    val isError: Boolean
        get() = this is Error

    /**
     * İşlemin kısmi başarılı olup olmadığını kontrol eder.
     */
    val isPartialSuccess: Boolean
        get() = this is PartialSuccess

    /**
     * Başarılı durumda veriyi döndürür, hata durumunda null döner.
     * PartialSuccess durumunda da veri döndürülür.
     */
    fun getOrNull(): T? =
        when (this) {
            is Success -> data
            is PartialSuccess -> data
            is Error -> null
        }

    /**
     * Başarılı durumda veriyi döndürür, hata durumunda varsayılan değeri döndürür.
     * PartialSuccess durumunda da veri döndürülür.
     */
    fun getOrDefault(defaultValue: @UnsafeVariance T): T =
        when (this) {
            is Success -> data
            is PartialSuccess -> data
            is Error -> defaultValue
        }
}
