package com.pnr.tv.repository

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
     * İşlem başarısız olduğunda döner, hata mesajını ve exception'ı içerir.
     */
    data class Error(
        val message: String,
        val exception: Throwable? = null,
    ) : Result<Nothing>()

    /**
     * İşlemin başarılı olup olmadığını kontrol eder.
     */
    val isSuccess: Boolean
        get() = this is Success

    /**
     * İşlemin başarısız olup olmadığını kontrol eder.
     */
    val isError: Boolean
        get() = this is Error

    /**
     * Başarılı durumda veriyi döndürür, hata durumunda null döner.
     */
    fun getOrNull(): T? =
        when (this) {
            is Success -> data
            is Error -> null
        }

    /**
     * Başarılı durumda veriyi döndürür, hata durumunda varsayılan değeri döndürür.
     */
    fun getOrDefault(defaultValue: @UnsafeVariance T): T =
        when (this) {
            is Success -> data
            is Error -> defaultValue
        }
}



