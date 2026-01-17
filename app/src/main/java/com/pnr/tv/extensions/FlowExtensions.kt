package com.pnr.tv.extensions

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

/**
 * Flow extension functions for repository operations.
 */

/**
 * Veritabanında veri olup olmadığını kontrol eder.
 * Repository'lerdeki "has" metodları için ortak helper.
 *
 * @return true ise veri var, false ise veri yok
 */
suspend fun <T> Flow<List<T>>.hasData(): Boolean {
    return firstOrNull()?.isNotEmpty() == true
}
