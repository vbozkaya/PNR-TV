package com.pnr.tv.network

import com.pnr.tv.network.dto.AuthenticationResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Xtream Codes API için özel servis.
 * Kullanıcı doğrulama (login verification) için kullanılır.
 *
 * Not: Base URL dinamik olacak (kullanıcı DNS'ine göre).
 * Retrofit instance'ı çağrı anında oluşturulacak.
 */
interface XtreamApiService {
    /**
     * Kullanıcı bilgilerini doğrular.
     *
     * @param username Kullanıcı adı
     * @param password Şifre
     * @return AuthenticationResponseDto - user_info ve server_info içerir
     */
    @GET("player_api.php")
    suspend fun verifyUser(
        @Query("username") username: String,
        @Query("password") password: String,
    ): AuthenticationResponseDto
}
