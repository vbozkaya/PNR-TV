package com.pnr.tv.util.error

/**
 * HTTP hata kodları enum'u.
 * Her HTTP status code için kullanıcı dostu mesaj ve severity bilgisi içerir.
 */
enum class HttpErrorCode(
    val code: Int,
    val severity: ErrorSeverity,
    val userFriendlyMessageKey: String? = null,
) {
    // Client Errors (4xx)
    BAD_REQUEST(400, ErrorSeverity.LOW),
    UNAUTHORIZED(401, ErrorSeverity.HIGH),
    FORBIDDEN(403, ErrorSeverity.HIGH),
    NOT_FOUND(404, ErrorSeverity.LOW),
    METHOD_NOT_ALLOWED(405, ErrorSeverity.LOW),
    NOT_ACCEPTABLE(406, ErrorSeverity.LOW),
    REQUEST_TIMEOUT(408, ErrorSeverity.MEDIUM),
    CONFLICT(409, ErrorSeverity.LOW),
    GONE(410, ErrorSeverity.LOW),
    LENGTH_REQUIRED(411, ErrorSeverity.LOW),
    PRECONDITION_FAILED(412, ErrorSeverity.LOW),
    PAYLOAD_TOO_LARGE(413, ErrorSeverity.LOW),
    URI_TOO_LONG(414, ErrorSeverity.LOW),
    UNSUPPORTED_MEDIA_TYPE(415, ErrorSeverity.LOW),
    RANGE_NOT_SATISFIABLE(416, ErrorSeverity.LOW),
    EXPECTATION_FAILED(417, ErrorSeverity.LOW),
    UNPROCESSABLE_ENTITY(422, ErrorSeverity.LOW),
    TOO_MANY_REQUESTS(429, ErrorSeverity.MEDIUM),
    REQUEST_HEADER_FIELDS_TOO_LARGE(431, ErrorSeverity.LOW),
    UNAVAILABLE_FOR_LEGAL_REASONS(451, ErrorSeverity.LOW),

    // Server Errors (5xx)
    INTERNAL_SERVER_ERROR(500, ErrorSeverity.HIGH),
    NOT_IMPLEMENTED(501, ErrorSeverity.HIGH),
    BAD_GATEWAY(502, ErrorSeverity.HIGH),
    SERVICE_UNAVAILABLE(503, ErrorSeverity.HIGH),
    GATEWAY_TIMEOUT(504, ErrorSeverity.HIGH),
    HTTP_VERSION_NOT_SUPPORTED(505, ErrorSeverity.HIGH),
    VARIANT_ALSO_NEGOTIATES(506, ErrorSeverity.HIGH),
    INSUFFICIENT_STORAGE(507, ErrorSeverity.HIGH),
    LOOP_DETECTED(508, ErrorSeverity.HIGH),
    NOT_EXTENDED(510, ErrorSeverity.HIGH),
    NETWORK_AUTHENTICATION_REQUIRED(511, ErrorSeverity.HIGH),
    ;

    companion object {
        /**
         * HTTP status code'dan HttpErrorCode enum'una dönüştürür.
         *
         * @param code HTTP status code
         * @return HttpErrorCode veya null (bilinmeyen kod için)
         */
        fun fromCode(code: Int): HttpErrorCode? {
            return values().find { it.code == code }
        }

        /**
         * HTTP status code'un client error (4xx) olup olmadığını kontrol eder.
         */
        fun isClientError(code: Int): Boolean {
            return code in 400..499
        }

        /**
         * HTTP status code'un server error (5xx) olup olmadığını kontrol eder.
         */
        fun isServerError(code: Int): Boolean {
            return code >= 500
        }

        /**
         * HTTP status code'un retry edilebilir olup olmadığını kontrol eder.
         * Genellikle server error'lar (5xx) retry edilebilir.
         */
        fun isRetryable(code: Int): Boolean {
            return isServerError(code) || code == 408 || code == 429 // Request timeout ve Too many requests
        }
    }
}
