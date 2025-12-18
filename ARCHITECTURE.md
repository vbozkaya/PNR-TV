# Architecture Documentation

Bu dokümantasyon, PNR TV uygulamasının mimari yapısını, bileşenlerini ve veri akışını açıklar.

## İçindekiler

- [Genel Bakış](#genel-bakış)
- [Mimari Desenler](#mimari-desenler)
- [Katmanlar](#katmanlar)
- [Bileşenler](#bileşenler)
- [Veri Akışı](#veri-akışı)
- [Dependency Injection](#dependency-injection)
- [Veritabanı Yapısı](#veritabanı-yapısı)
- [Network Katmanı](#network-katmanı)

---

## Genel Bakış

PNR TV, **Model-View-ViewModel (MVVM)** mimarisini kullanır ve Android TV platformu için optimize edilmiştir.

### Mimari Prensipler

1. **Separation of Concerns:** Her katman kendi sorumluluğuna odaklanır
2. **Single Source of Truth:** Veritabanı tek gerçek kaynak olarak kullanılır
3. **Reactive Programming:** Kotlin Flow ve StateFlow ile reaktif veri akışı
4. **Dependency Injection:** Hilt ile merkezi bağımlılık yönetimi
5. **Offline-First:** Yerel veritabanı ile offline çalışma desteği

---

## Mimari Desenler

### 1. MVVM (Model-View-ViewModel)

```
┌─────────────┐
│    View     │ (Fragment/Activity)
│  (UI Layer) │
└──────┬──────┘
       │ observes
       ▼
┌─────────────┐
│ ViewModel   │ (Business Logic)
│   (State)   │
└──────┬──────┘
       │ uses
       ▼
┌─────────────┐
│ Repository  │ (Data Layer)
│  (Data)     │
└──────┬──────┘
       │ uses
       ▼
┌─────────────┐     ┌─────────────┐
│   Database  │     │    API      │
│   (Room)    │     │  (Retrofit) │
└─────────────┘     └─────────────┘
```

### 2. Repository Pattern

Repository katmanı, veri kaynaklarını (API, Database) ViewModel'den soyutlar:

- **Single Source of Truth:** Database
- **Network as Cache:** API'den gelen veriler database'e kaydedilir
- **Offline Support:** Database'den veri okunur, API sadece güncelleme için kullanılır

### 3. Use Case Pattern

Domain katmanında iş mantığı Use Case'lerde toplanır:

- `BuildLiveStreamUrlUseCase`: Canlı yayın URL'lerini oluşturur

---

## Katmanlar

### 1. UI Layer (Presentation)

**Konum:** `app/src/main/java/com/pnr/tv/ui/`

**Bileşenler:**
- **Activities:** `MainActivity`, `PlayerActivity`, `SplashActivity`
- **Fragments:** `MainFragment`, `ContentBrowseFragment`, `LiveStreamsBrowseFragment`
- **ViewModels:** `MainViewModel`, `MovieDetailViewModel`, `SeriesDetailViewModel`
- **Adapters:** `ContentAdapter`, `CategoryAdapter`, `EpisodesAdapter`

**Sorumluluklar:**
- Kullanıcı arayüzünü göstermek
- Kullanıcı etkileşimlerini yönetmek
- ViewModel'den gelen state'leri gözlemlemek
- Android TV için özel UI bileşenleri (Leanback)

**Örnek Yapı:**
```kotlin
class MainFragment : Fragment() {
    private val viewModel: MainViewModel by viewModels()
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.categories.collect { categories ->
                // UI güncelle
            }
        }
    }
}
```

### 2. Domain Layer

**Konum:** `app/src/main/java/com/pnr/tv/domain/`

**Bileşenler:**
- **Use Cases:** `BuildLiveStreamUrlUseCase`
- **Models:** `ContentItem`, `CategoryItem`, `SortOrder`

**Sorumluluklar:**
- İş mantığını içerir
- UI ve Data katmanlarından bağımsızdır
- Test edilebilir iş kuralları

### 3. Data Layer

**Konum:** `app/src/main/java/com/pnr/tv/repository/`, `app/src/main/java/com/pnr/tv/network/`, `app/src/main/java/com/pnr/tv/db/`

**Bileşenler:**
- **Repositories:** `ContentRepository`, `MovieRepository`, `SeriesRepository`
- **Network:** `ApiService`, `TmdbApiService`
- **Database:** `AppDatabase`, DAOs, Entities

**Sorumluluklar:**
- Veri kaynaklarına erişim (API, Database)
- Veri dönüşümleri (DTO → Entity)
- Cache yönetimi
- Hata yönetimi

**Veri Akışı:**
```
API (DTO) → Repository → Database (Entity) → ViewModel → UI
```

---

## Bileşenler

### 1. Application

**Sınıf:** `PnrTvApplication`

**Sorumluluklar:**
- Hilt uygulama sınıfı
- Crashlytics başlatma
- Timber logging yapılandırması
- ImageLoader (Coil) yapılandırması
- WorkManager yapılandırması

### 2. Activities

#### MainActivity
- Ana ekran container'ı
- `MainFragment`'i barındırır
- Toolbar yönetimi

#### PlayerActivity
- Video oynatma ekranı
- ExoPlayer entegrasyonu
- Playback kontrolü

#### SplashActivity
- Uygulama başlangıç ekranı
- Kullanıcı kontrolü
- Yönlendirme

### 3. ViewModels

#### MainViewModel
- Ana ekran state yönetimi
- Kategori ve içerik listeleri
- Kullanıcı bilgileri
- Güncelleme durumu

#### MovieDetailViewModel
- Film detayları
- TMDB entegrasyonu
- İzleme durumu

#### SeriesDetailViewModel
- Dizi detayları
- Sezon ve bölüm yönetimi
- İzlenen bölüm takibi

### 4. Repositories

#### ContentRepository
- Tüm içerik tipleri için ortak işlemler
- API çağrıları
- Database işlemleri

#### MovieRepository
- Film özel işlemleri
- Film kategorileri
- Film arama ve filtreleme

#### SeriesRepository
- Dizi özel işlemleri
- Dizi kategorileri
- Dizi bilgileri ve bölümler

#### LiveStreamRepository
- Canlı yayın işlemleri
- Canlı yayın kategorileri
- URL oluşturma

#### UserRepository
- Kullanıcı yönetimi
- Oturum yönetimi
- Kullanıcı verilerini temizleme

#### TmdbRepository
- TMDB API entegrasyonu
- Film/dizi detayları
- Cache yönetimi

### 5. Database

**Sınıf:** `AppDatabase`

**Entities:**
- `MovieEntity`: Film bilgileri
- `SeriesEntity`: Dizi bilgileri
- `LiveStreamEntity`: Canlı yayın bilgileri
- `MovieCategoryEntity`: Film kategorileri
- `SeriesCategoryEntity`: Dizi kategorileri
- `LiveStreamCategoryEntity`: Canlı yayın kategorileri
- `UserAccountEntity`: Kullanıcı hesapları
- `ViewerEntity`: İzleyici profilleri
- `FavoriteChannelEntity`: Favori kanallar
- `RecentlyWatchedEntity`: Son izlenenler
- `WatchedEpisodeEntity`: İzlenen bölümler
- `PlaybackPositionEntity`: Oynatma pozisyonları
- `TmdbCacheEntity`: TMDB cache

**DAO Pattern:**
Her entity için bir DAO (Data Access Object) tanımlanmıştır:
- `MovieDao`
- `SeriesDao`
- `LiveStreamDao`
- `UserDao`
- vb.

---

## Veri Akışı

### 1. İçerik Yükleme Akışı

```
User Action (Fragment)
    ↓
ViewModel.loadContent()
    ↓
Repository.getContent()
    ↓
┌─────────────────┐
│  Database Read  │ (Önce local'den oku)
└────────┬────────┘
         │
         ▼
    Cache Hit?
    ├─ Yes → Return cached data
    └─ No  → API Call
              ↓
         ┌─────────┐
         │  API    │
         └────┬────┘
              │
              ▼
         Save to Database
              ↓
         Return data
```

### 2. Güncelleme Akışı

```
User Action (Refresh)
    ↓
ViewModel.refreshContent()
    ↓
Repository.refreshContent()
    ↓
API Call
    ↓
Update Database
    ↓
Flow emits new data
    ↓
UI updates automatically
```

### 3. Oynatma Akışı

```
User selects content
    ↓
ViewModel.loadContentDetails()
    ↓
Repository.getContentDetails()
    ↓
Build stream URL (UseCase)
    ↓
PlayerActivity starts
    ↓
ExoPlayer plays stream
    ↓
Playback position saved (Repository)
```

---

## Dependency Injection

**Framework:** Hilt (Dagger)

### Modules

#### DatabaseModule
- `AppDatabase` sağlar
- Tüm DAO'ları sağlar
- `UserRepository` sağlar

#### NetworkModule
- `OkHttpClient` yapılandırması
- `Retrofit.Builder` (IPTV için dinamik baseUrl)
- `Retrofit` (TMDB için sabit baseUrl)
- `ApiService` ve `TmdbApiService`

### Injection Örnekleri

```kotlin
// ViewModel Injection
@HiltViewModel
class MainViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val contentRepository: ContentRepository,
    @ApplicationContext private val context: Context
) : BaseViewModel()

// Repository Injection
class ContentRepository @Inject constructor(
    private val movieRepository: MovieRepository,
    private val seriesRepository: SeriesRepository,
    private val liveStreamRepository: LiveStreamRepository
)

// Activity/Fragment Injection
@AndroidEntryPoint
class MainActivity : FragmentActivity()
```

---

## Veritabanı Yapısı

### Room Database

**Version:** 18

**Migration Strategy:**
- DEBUG: Destructive migration (veri kaybı olabilir)
- RELEASE: Migration zorunlu (veri kaybı önlenir)

**Migration Dosyası:** `DatabaseMigrations.kt`

### Entity İlişkileri

```
UserAccountEntity (1) ──┐
                        │
                        ├──> (Many) Movies, Series, LiveStreams
                        │
ViewerEntity (Many) ────┘
                        │
                        ├──> (Many) RecentlyWatchedEntity
                        │
                        ├──> (Many) WatchedEpisodeEntity
                        │
                        └──> (Many) PlaybackPositionEntity
```

### Cache Stratejisi

1. **API Response Cache:** Database'de saklanır
2. **TMDB Cache:** `TmdbCacheEntity` ile önbelleklenir
3. **Image Cache:** Coil ile memory ve disk cache

---

## Network Katmanı

### API Services

#### ApiService (IPTV)
- Base URL: Dinamik (kullanıcı DNS'ine göre)
- Authentication: Query parameters (username, password)
- Endpoints: `player_api.php`

#### TmdbApiService
- Base URL: `https://api.themoviedb.org/3/`
- Authentication: API Key (query parameter)
- Endpoints: `/search/movie`, `/movie/{id}`, vb.

### Network Configuration

**OkHttpClient:**
- Timeout: 30 saniye
- Retry: `retryOnConnectionFailure = true`
- Interceptors:
  - `RateLimiterInterceptor`: Rate limiting
  - `HttpLoggingInterceptor`: Debug logging

**Retrofit:**
- Converter: Moshi (JSON)
- Call Adapter: Coroutines (suspend functions)

### Error Handling

**Result Wrapper:**
```kotlin
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String, val exception: Exception?) : Result<Nothing>()
    object Loading : Result<Nothing>()
}
```

**Kullanım:**
```kotlin
when (val result = repository.getContent()) {
    is Result.Success -> { /* Handle success */ }
    is Result.Error -> { /* Handle error */ }
    is Result.Loading -> { /* Show loading */ }
}
```

---

## UI Components

### Android TV Components

#### Leanback Library
- `BrowseSupportFragment`: Ana ekran
- `DetailsSupportFragment`: Detay ekranları
- `PlaybackSupportFragment`: Oynatma ekranı

#### Custom Components
- `CustomContentRecyclerView`: İçerik listeleri
- `CustomCategoriesRecyclerView`: Kategori listeleri
- `CustomGridLayoutManager`: Grid layout yönetimi
- `CardPresenter`: İçerik kartları

### State Management

**StateFlow:**
```kotlin
private val _uiState = MutableStateFlow<UiState>(UiState.Initial)
val uiState: StateFlow<UiState> = _uiState.asStateFlow()
```

**SharedFlow (Events):**
```kotlin
private val _toastEvent = MutableSharedFlow<String>()
val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()
```

---

## Background Tasks

### WorkManager

**Worker:** `TmdbSyncWorker`
- TMDB verilerini arka planda senkronize eder
- Network constraint ile çalışır
- Periyodik veya tek seferlik çalışabilir

---

## Testing Strategy

### Unit Tests
- ViewModel testleri
- Repository testleri
- Use Case testleri

### Integration Tests
- Database testleri
- API testleri (MockWebServer)

### UI Tests
- Fragment testleri
- Activity testleri

---

## Güvenlik

### Network Security
- `network_security_config.xml` ile özel sertifika desteği
- Production'da sıkı güvenlik ayarları

### Data Security
- Kullanıcı şifreleri şifrelenmez (IPTV API gereksinimi)
- Local database şifreleme yok (Android sistem güvenliği)

---

## Performans Optimizasyonları

1. **Database Indexing:** Sık sorgulanan alanlar için indexler
2. **Pagination:** Büyük listeler için sayfalama
3. **Image Caching:** Coil ile memory ve disk cache
4. **API Caching:** Database'de API response cache
5. **Lazy Loading:** RecyclerView ile lazy loading
6. **Background Tasks:** WorkManager ile arka plan işlemleri

---

## Gelecek İyileştirmeler

1. **Clean Architecture:** Daha katı katman ayrımı
2. **Use Cases:** Daha fazla use case eklenmesi
3. **Error Handling:** Daha detaylı hata yönetimi
4. **Offline Mode:** Tam offline çalışma desteği
5. **Analytics:** Kullanıcı analitiği entegrasyonu

---

## Diagram

### Basit Mimari Diyagramı

```
┌─────────────────────────────────────────────────────────┐
│                      UI Layer                            │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐              │
│  │Activity  │  │Fragment  │  │Adapter   │              │
│  └────┬─────┘  └────┬──────┘  └────┬─────┘              │
│       │            │              │                     │
│       └────────────┼──────────────┘                     │
│                    │                                    │
│              ┌─────▼─────┐                             │
│              │ ViewModel │                             │
│              └─────┬─────┘                             │
└────────────────────┼───────────────────────────────────┘
                     │
┌────────────────────▼───────────────────────────────────┐
│                  Domain Layer                          │
│  ┌──────────────┐  ┌──────────────┐                   │
│  │  Use Cases   │  │    Models    │                   │
│  └──────────────┘  └──────────────┘                   │
└────────────────────┬───────────────────────────────────┘
                     │
┌────────────────────▼───────────────────────────────────┐
│                   Data Layer                           │
│  ┌──────────────┐  ┌──────────────┐                   │
│  │  Repository  │  │   Network    │                   │
│  └──────┬───────┘  └──────┬───────┘                   │
│         │                 │                            │
│  ┌──────▼───────┐  ┌──────▼───────┐                   │
│  │  Database    │  │     API      │                   │
│  │   (Room)     │  │  (Retrofit)  │                   │
│  └──────────────┘  └──────────────┘                   │
└─────────────────────────────────────────────────────────┘
```

---

## Notlar

1. **Hilt:** Tüm dependency injection Hilt ile yapılır
2. **Coroutines:** Tüm async işlemler coroutines ile yapılır
3. **Flow:** Reactive programming için Flow kullanılır
4. **Room:** Local database için Room kullanılır
5. **Retrofit:** Network işlemleri için Retrofit kullanılır
6. **ExoPlayer:** Video oynatma için ExoPlayer kullanılır

