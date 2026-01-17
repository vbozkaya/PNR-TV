package com.pnr.tv.util.error

/**
 * Veri akışlarını sarmalayan (wrapper) sealed class.
 * Tüm veri çağrılarında tutarlı hata yönetimi sağlar.
 *
 * @param T Veri tipi
 */
sealed class Resource<out T> {
    /**
     * Veri yükleniyor durumu.
     */
    object Loading : Resource<Nothing>()

    /**
     * Veri başarıyla yüklendi durumu.
     * @param data Yüklenen veri
     */
    data class Success<out T>(val data: T) : Resource<T>()

    /**
     * Veri yükleme hatası durumu.
     * @param message Hata mesajı (kullanıcı dostu formatlanmış olmalı)
     * @param exception Opsiyonel exception (loglama için)
     * @param severity Hata şiddeti (varsayılan: MEDIUM)
     */
    data class Error(
        val message: String,
        val exception: Throwable? = null,
        val severity: ErrorSeverity = ErrorSeverity.MEDIUM,
    ) : Resource<Nothing>()

    /**
     * Veri başarıyla yüklenip yüklenmediğini kontrol eder.
     */
    val isSuccess: Boolean
        get() = this is Success

    /**
     * Veri yükleniyor mu kontrol eder.
     */
    val isLoading: Boolean
        get() = this is Loading

    /**
     * Hata durumu var mı kontrol eder.
     */
    val isError: Boolean
        get() = this is Error

    /**
     * Başarılı durumda veriyi döndürür, hata durumunda null döner.
     */
    fun getOrNull(): T? =
        when (this) {
            is Success -> data
            is Loading -> null
            is Error -> null
        }

    /**
     * Başarılı durumda veriyi döndürür, hata durumunda varsayılan değeri döndürür.
     */
    fun getOrDefault(defaultValue: @UnsafeVariance T): T =
        when (this) {
            is Success -> data
            is Loading -> defaultValue
            is Error -> defaultValue
        }
}
