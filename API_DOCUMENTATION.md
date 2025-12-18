# API Documentation

Bu dokümantasyon, PNR TV uygulamasının kullandığı API endpoint'lerini ve request/response formatlarını açıklar.

## İçindekiler

- [IPTV API](#iptv-api)
  - [Authentication](#authentication)
  - [Movies (VOD)](#movies-vod)
  - [Series](#series)
  - [Live Streams](#live-streams)
- [TMDB API](#tmdb-api)
  - [Movie Search](#movie-search)
  - [Movie Details](#movie-details)
  - [TV Show Search](#tv-show-search)
  - [TV Show Details](#tv-show-details)

---

## IPTV API

PNR TV, standart IPTV API formatını kullanır. Tüm endpoint'ler `player_api.php` üzerinden çalışır.

### Base URL

```
{USER_DNS}/player_api.php
```

**Not:** `{USER_DNS}` kullanıcının hesap ayarlarında tanımlı DNS adresidir.

### Authentication

Tüm API çağrıları `username` ve `password` query parametreleri ile kimlik doğrulama gerektirir.

---

## Authentication

### Get User Info

Kullanıcı bilgilerini ve hesap durumunu getirir.

**Endpoint:** `GET /player_api.php`

**Query Parameters:**
- `username` (required): Kullanıcı adı
- `password` (required): Kullanıcı şifresi

**Response:**

```json
{
  "user_info": {
    "username": "string",
    "password": "string",
    "message": "string | null",
    "auth": 1,
    "status": "Active | null",
    "exp_date": "2024-12-31 | null",
    "is_trial": "0 | 1 | null",
    "active_cons": "1 | null",
    "created_at": "2024-01-01 | null",
    "max_connections": "2 | null",
    "allowed_output_formats": ["m3u8", "ts"] | null
  },
  "server_info": {
    "url": "string | null",
    "port": "string | null",
    "https_port": "string | null",
    "server_protocol": "http | https | null",
    "rtmp_port": "string | null",
    "timezone": "UTC | null",
    "timestamp_now": 1234567890,
    "time_now": "2024-01-01 12:00:00 | null",
    "process": true | false | null
  }
}
```

**Response DTO:** `AuthenticationResponseDto`

**Kullanım:**
```kotlin
val response = apiService.getUserInfo(username, password)
val userInfo = response.extractUserInfo()
```

---

## Movies (VOD)

### Get Movie Categories

Film kategorilerini getirir.

**Endpoint:** `GET /player_api.php`

**Query Parameters:**
- `username` (required): Kullanıcı adı
- `password` (required): Kullanıcı şifresi
- `action` (required): `"get_vod_categories"`

**Response:**

```json
[
  {
    "category_id": "1",
    "category_name": "Aksiyon",
    "parent_id": 0
  }
]
```

**Response DTO:** `List<MovieCategoryDto>`

**Kullanım:**
```kotlin
val categories = apiService.getMovieCategories(username, password)
```

---

### Get Movies

Belirli bir kategorideki filmleri getirir.

**Endpoint:** `GET /player_api.php`

**Query Parameters:**
- `username` (required): Kullanıcı adı
- `password` (required): Kullanıcı şifresi
- `action` (required): `"get_vod_streams"`
- `category_id` (optional): Kategori ID'si (tüm filmler için boş bırakılabilir)

**Response:**

```json
[
  {
    "stream_id": 12345,
    "num": 12345,
    "name": "Film Adı",
    "stream_icon": "https://example.com/poster.jpg",
    "rating": "8.5",
    "rating_5based": 4.2,
    "plot": "Film açıklaması...",
    "category_id": "1",
    "category_ids": [1, 2],
    "backdrop_path": ["https://example.com/backdrop.jpg"],
    "stream_type": "movie",
    "tmdb": "550",
    "trailer": "https://youtube.com/watch?v=...",
    "added": "2024-01-01 12:00:00",
    "is_adult": 0,
    "container_extension": "ts",
    "custom_sid": null,
    "direct_source": null
  }
]
```

**Response DTO:** `List<MovieDto>`

**Kullanım:**
```kotlin
val movies = apiService.getMovies(
    username = username,
    password = password,
    action = ApiActions.GET_VOD_STREAMS
)
```

**Not:** `category_id` parametresi opsiyoneldir. Belirtilmezse tüm filmler döner.

---

## Series

### Get Series Categories

Dizi kategorilerini getirir.

**Endpoint:** `GET /player_api.php`

**Query Parameters:**
- `username` (required): Kullanıcı adı
- `password` (required): Kullanıcı şifresi
- `action` (required): `"get_series_categories"`

**Response:**

```json
[
  {
    "category_id": "2",
    "category_name": "Drama",
    "parent_id": 0
  }
]
```

**Response DTO:** `List<SeriesCategoryDto>`

**Kullanım:**
```kotlin
val categories = apiService.getSeriesCategories(username, password)
```

---

### Get Series

Belirli bir kategorideki dizileri getirir.

**Endpoint:** `GET /player_api.php`

**Query Parameters:**
- `username` (required): Kullanıcı adı
- `password` (required): Kullanıcı şifresi
- `action` (required): `"get_series"`
- `category_id` (optional): Kategori ID'si (tüm diziler için boş bırakılabilir)

**Response:**

```json
[
  {
    "series_id": 67890,
    "name": "Dizi Adı",
    "cover": "https://example.com/cover.jpg",
    "plot": "Dizi açıklaması...",
    "releaseDate": "2020-01-01",
    "last_modified": "2024-01-01 12:00:00",
    "rating": "9.0",
    "rating_5based": 4.5,
    "backdrop_path": ["https://example.com/backdrop.jpg"],
    "youtube_trailer": "https://youtube.com/watch?v=...",
    "episode_run_time": "45",
    "category_id": "2",
    "added": "2024-01-01 12:00:00",
    "tmdb": "1396"
  }
]
```

**Response DTO:** `List<SeriesDto>`

**Kullanım:**
```kotlin
val series = apiService.getSeries(
    username = username,
    password = password,
    action = ApiActions.GET_SERIES
)
```

---

### Get Series Info

Belirli bir dizinin bölüm bilgilerini getirir.

**Endpoint:** `GET /player_api.php`

**Query Parameters:**
- `username` (required): Kullanıcı adı
- `password` (required): Kullanıcı şifresi
- `action` (required): `"get_series_info"`
- `series_id` (required): Dizi ID'si

**Response:**

```json
{
  "info": {
    "name": "Dizi Adı",
    "cover": "https://example.com/cover.jpg",
    "plot": "Dizi açıklaması...",
    "cast": "Oyuncu 1, Oyuncu 2",
    "director": "Yönetmen Adı",
    "genre": "Drama, Aksiyon",
    "releaseDate": "2020-01-01",
    "rating": "9.0",
    "rating_5based": 4.5,
    "backdrop_path": ["https://example.com/backdrop.jpg"],
    "youtube_trailer": "https://youtube.com/watch?v=...",
    "episode_run_time": "45",
    "tmdb": "1396"
  },
  "episodes": {
    "1": {
      "1": {
        "id": "123456",
        "title": "Bölüm 1",
        "container_extension": "ts",
        "info": {
          "plot": "Bölüm açıklaması...",
          "duration": "45:00",
          "movie_image": "https://example.com/episode.jpg"
        }
      }
    }
  }
}
```

**Response DTO:** `SeriesInfoDto`

**Kullanım:**
```kotlin
val seriesInfo = apiService.getSeriesInfo(
    username = username,
    password = password,
    seriesId = 67890
)
```

---

## Live Streams

### Get Live Stream Categories

Canlı yayın kategorilerini getirir.

**Endpoint:** `GET /player_api.php`

**Query Parameters:**
- `username` (required): Kullanıcı adı
- `password` (required): Kullanıcı şifresi
- `action` (required): `"get_live_categories"`

**Response:**

```json
[
  {
    "category_id": "3",
    "category_name": "Haber",
    "parent_id": 0
  }
]
```

**Response DTO:** `List<LiveStreamCategoryDto>`

**Kullanım:**
```kotlin
val categories = apiService.getLiveStreamCategories(
    username = username,
    password = password,
    action = ApiActions.GET_LIVE_CATEGORIES
)
```

---

### Get Live Streams

Belirli bir kategorideki canlı yayınları getirir.

**Endpoint:** `GET /player_api.php`

**Query Parameters:**
- `username` (required): Kullanıcı adı
- `password` (required): Kullanıcı şifresi
- `action` (required): `"get_live_streams"`
- `category_id` (optional): Kategori ID'si (tüm yayınlar için boş bırakılabilir)

**Response:**

```json
[
  {
    "stream_id": 11111,
    "name": "Kanal Adı",
    "stream_icon": "https://example.com/icon.png",
    "category_id": "3",
    "category_name": "Haber"
  }
]
```

**Response DTO:** `List<LiveStreamDto>`

**Kullanım:**
```kotlin
val liveStreams = apiService.getLiveStreams(
    username = username,
    password = password,
    action = ApiActions.GET_LIVE_STREAMS
)
```

---

## TMDB API

PNR TV, film ve dizi detayları için The Movie Database (TMDB) API'sini kullanır.

### Base URL

```
https://api.themoviedb.org/3/
```

### Authentication

Tüm TMDB API çağrıları `api_key` query parametresi gerektirir. API anahtarı `local.properties` dosyasında `TMDB_API_KEY` olarak tanımlanmalıdır.

---

## Movie Search

### Search Movie

Film adına göre TMDB'de arama yapar.

**Endpoint:** `GET /search/movie`

**Query Parameters:**
- `api_key` (required): TMDB API anahtarı
- `query` (required): Aranacak film adı
- `language` (required): Dil kodu (örn: "tr-TR", "en-US")
- `region` (optional): Bölge kodu (örn: "TR", "US")
- `year` (optional): Yıl filtresi
- `include_adult` (optional): Yetişkin içerik dahil edilsin mi? (default: false)

**Response:**

```json
{
  "page": 1,
  "results": [
    {
      "id": 550,
      "title": "Fight Club",
      "overview": "Film açıklaması...",
      "poster_path": "/poster.jpg",
      "backdrop_path": "/backdrop.jpg",
      "release_date": "1999-10-15",
      "vote_average": 8.4,
      "vote_count": 25000
    }
  ],
  "total_pages": 1,
  "total_results": 1
}
```

**Response DTO:** `TmdbSearchResultDto`

**Kullanım:**
```kotlin
val result = tmdbApiService.searchMovie(
    apiKey = BuildConfig.TMDB_API_KEY,
    query = "Fight Club",
    language = "tr-TR"
)
```

---

## Movie Details

### Get Movie Details

Film ID'sine göre detaylı bilgi getirir (oyuncular ve ekip dahil).

**Endpoint:** `GET /movie/{movie_id}`

**Path Parameters:**
- `movie_id` (required): TMDB film ID'si

**Query Parameters:**
- `api_key` (required): TMDB API anahtarı
- `append_to_response` (optional): Ek bilgiler (default: "credits")
- `language` (optional): Dil kodu (default: "tr-TR")

**Response:**

```json
{
  "id": 550,
  "title": "Fight Club",
  "overview": "Film açıklaması...",
  "poster_path": "/poster.jpg",
  "backdrop_path": "/backdrop.jpg",
  "release_date": "1999-10-15",
  "vote_average": 8.4,
  "vote_count": 25000,
  "runtime": 139,
  "genres": [
    {
      "id": 18,
      "name": "Drama"
    }
  ],
  "credits": {
    "cast": [
      {
        "id": 819,
        "name": "Edward Norton",
        "character": "The Narrator",
        "profile_path": "/profile.jpg"
      }
    ],
    "crew": [
      {
        "id": 7467,
        "name": "David Fincher",
        "job": "Director",
        "profile_path": "/profile.jpg"
      }
    ]
  }
}
```

**Response DTO:** `TmdbMovieDetailsDto`

**Kullanım:**
```kotlin
val details = tmdbApiService.getMovieDetails(
    movieId = 550,
    apiKey = BuildConfig.TMDB_API_KEY
)
```

---

## TV Show Search

### Search TV Show

Dizi adına göre TMDB'de arama yapar.

**Endpoint:** `GET /search/tv`

**Query Parameters:**
- `api_key` (required): TMDB API anahtarı
- `query` (required): Aranacak dizi adı
- `language` (required): Dil kodu (örn: "tr-TR", "en-US")
- `region` (optional): Bölge kodu (örn: "TR", "US")
- `first_air_date_year` (optional): İlk yayın yılı filtresi
- `include_adult` (optional): Yetişkin içerik dahil edilsin mi? (default: false)

**Response:**

```json
{
  "page": 1,
  "results": [
    {
      "id": 1396,
      "name": "Breaking Bad",
      "overview": "Dizi açıklaması...",
      "poster_path": "/poster.jpg",
      "backdrop_path": "/backdrop.jpg",
      "first_air_date": "2008-01-20",
      "vote_average": 9.5,
      "vote_count": 50000
    }
  ],
  "total_pages": 1,
  "total_results": 1
}
```

**Response DTO:** `TmdbTvSearchResultDto`

**Kullanım:**
```kotlin
val result = tmdbApiService.searchTvShow(
    apiKey = BuildConfig.TMDB_API_KEY,
    query = "Breaking Bad",
    language = "tr-TR"
)
```

---

## TV Show Details

### Get TV Show Details

Dizi ID'sine göre detaylı bilgi getirir (oyuncular, yaratıcı dahil).

**Endpoint:** `GET /tv/{tv_id}`

**Path Parameters:**
- `tv_id` (required): TMDB dizi ID'si

**Query Parameters:**
- `api_key` (required): TMDB API anahtarı
- `append_to_response` (optional): Ek bilgiler (default: "credits")
- `language` (optional): Dil kodu (default: "tr-TR")

**Response:**

```json
{
  "id": 1396,
  "name": "Breaking Bad",
  "overview": "Dizi açıklaması...",
  "poster_path": "/poster.jpg",
  "backdrop_path": "/backdrop.jpg",
  "first_air_date": "2008-01-20",
  "vote_average": 9.5,
  "vote_count": 50000,
  "episode_run_time": [45],
  "number_of_seasons": 5,
  "number_of_episodes": 62,
  "genres": [
    {
      "id": 18,
      "name": "Drama"
    }
  ],
  "credits": {
    "cast": [
      {
        "id": 17419,
        "name": "Bryan Cranston",
        "character": "Walter White",
        "profile_path": "/profile.jpg"
      }
    ],
    "crew": [
      {
        "id": 66633,
        "name": "Vince Gilligan",
        "job": "Creator",
        "profile_path": "/profile.jpg"
      }
    ]
  }
}
```

**Response DTO:** `TmdbTvShowDetailsDto`

**Kullanım:**
```kotlin
val details = tmdbApiService.getTvShowDetails(
    tvId = 1396,
    apiKey = BuildConfig.TMDB_API_KEY
)
```

---

## Hata Yönetimi

### IPTV API Hataları

IPTV API çağrıları `Result<T>` tipinde döner:

- **Success:** `Result.Success(data)`
- **Error:** `Result.Error(message, exception)`
- **Loading:** `Result.Loading`

**Örnek Kullanım:**
```kotlin
when (val result = repository.getMovies()) {
    is Result.Success -> {
        // Başarılı
        val movies = result.data
    }
    is Result.Error -> {
        // Hata
        val message = result.message
        val exception = result.exception
    }
    is Result.Loading -> {
        // Yükleniyor
    }
}
```

### TMDB API Hataları

TMDB API çağrıları doğrudan exception fırlatabilir. Hata yönetimi repository katmanında yapılır.

---

## Rate Limiting

IPTV API çağrıları için `RateLimiterInterceptor` kullanılır. Bu interceptor, aynı endpoint'e çok sık yapılan istekleri sınırlar.

**Yapılandırma:**
- Varsayılan limit: 1 saniyede 1 istek
- Önbellek süresi: 5 saniye

---

## Network Security

Uygulama, özel sertifikalara sahip IPTV sunucularına bağlanabilmek için `network_security_config.xml` kullanır. Bu yapılandırma, geliştirme ortamında self-signed sertifikalara izin verir.

**Not:** Production build'lerde güvenlik ayarları sıkılaştırılmalıdır.

---

## Örnekler

### Tam API Çağrı Örneği

```kotlin
// Repository katmanında
suspend fun getMovies(): Result<List<MovieEntity>> {
    return safeApiCall(
        apiCall = { apiService, username, password ->
            apiService.getMovies(
                username = username,
                password = password,
                action = ApiActions.GET_VOD_STREAMS
            )
        }
    ).map { movies ->
        movies.mapNotNull { it.toEntity() }
    }
}
```

### TMDB Entegrasyonu

```kotlin
// Repository katmanında
suspend fun getMovieDetails(tmdbId: Int): Result<TmdbMovieDetailsDto> {
    return try {
        val details = tmdbApiService.getMovieDetails(
            movieId = tmdbId,
            apiKey = BuildConfig.TMDB_API_KEY
        )
        Result.Success(details)
    } catch (e: Exception) {
        Result.Error("Film detayları alınamadı", e)
    }
}
```

---

## Notlar

1. **DNS Normalizasyonu:** Tüm DNS adresleri `normalizeDnsUrl()` extension fonksiyonu ile normalize edilir.
2. **API Service Caching:** Her DNS için tek bir `ApiService` instance'ı önbellekte tutulur.
3. **Retry Mekanizması:** `safeApiCall` fonksiyonu retry mekanizması içerir (varsayılan: retry yok).
4. **Offline Detection:** Network durumu kontrol edilir ve uygun hata mesajları gösterilir.

