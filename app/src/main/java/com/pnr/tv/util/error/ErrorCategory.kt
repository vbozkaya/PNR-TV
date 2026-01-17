package com.pnr.tv.util.error

/**
 * Hata kategorileri - Crashlytics sınıflandırması için
 * NOT: Kullanıcı verileri (PII) kesinlikle eklenmez
 */
object ErrorCategory {
    const val NETWORK_ERROR = "network_error"
    const val CONNECTION_ERROR = "connection_error"
    const val TIMEOUT_ERROR = "timeout_error"
    const val HTTP_ERROR = "http_error"
    const val DATABASE_ERROR = "database_error"
    const val UI_ERROR = "ui_error"
    const val PLAYER_ERROR = "player_error"
    const val UNKNOWN_ERROR = "unknown_error"
}
