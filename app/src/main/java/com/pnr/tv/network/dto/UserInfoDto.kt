package com.pnr.tv.network.dto

import com.squareup.moshi.Json

data class UserInfoDto(
    @field:Json(name = "username") val username: String,
    @field:Json(name = "password") val password: String,
    @field:Json(name = "message") val message: String?,
    @field:Json(name = "auth") val auth: Int,
    @field:Json(name = "status") val status: String?,
    @field:Json(name = "exp_date") val expDate: String?,
    @field:Json(name = "is_trial") val isTrial: String?,
    @field:Json(name = "active_cons") val activeCons: String?,
    @field:Json(name = "created_at") val createdAt: String?,
    @field:Json(name = "max_connections") val maxConnections: String?,
    @field:Json(name = "allowed_output_formats") val allowedOutputFormats: List<String>?,
)

data class ServerInfoDto(
    @field:Json(name = "url") val url: String?,
    @field:Json(name = "port") val port: String?,
    @field:Json(name = "https_port") val httpsPort: String?,
    @field:Json(name = "server_protocol") val serverProtocol: String?,
    @field:Json(name = "rtmp_port") val rtmpPort: String?,
    @field:Json(name = "timezone") val timezone: String?,
    @field:Json(name = "timestamp_now") val timestampNow: Long?,
    @field:Json(name = "time_now") val timeNow: String?,
    @field:Json(name = "process") val process: Boolean?,
)

data class AuthenticationResponseDto(
    @field:Json(name = "user_info") val userInfo: UserInfoDto,
    @field:Json(name = "server_info") val serverInfo: ServerInfoDto?,
)



