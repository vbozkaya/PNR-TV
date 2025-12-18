# Contributing Guide

PNR TV projesine katkıda bulunmak istediğiniz için teşekkürler! Bu rehber, projeye nasıl katkıda bulunabileceğinizi açıklar.

## İçindekiler

- [Katkıda Bulunma](#katkıda-bulunma)
- [Kod Stili](#kod-stili)
- [Pull Request Süreci](#pull-request-süreci)
- [Commit Mesajları](#commit-mesajları)
- [Test Yazma](#test-yazma)
- [Dokümantasyon](#dokümantasyon)

---

## Katkıda Bulunma

### 1. Fork ve Clone

1. Projeyi fork edin
2. Fork'unuzu clone edin:
   ```bash
   git clone https://github.com/your-username/pnr-tv.git
   cd pnr-tv
   ```

### 2. Branch Oluşturma

Her özellik veya düzeltme için yeni bir branch oluşturun:

```bash
git checkout -b feature/your-feature-name
# veya
git checkout -b fix/your-bug-fix
```

**Branch İsimlendirme:**
- `feature/`: Yeni özellikler için
- `fix/`: Hata düzeltmeleri için
- `refactor/`: Kod iyileştirmeleri için
- `docs/`: Dokümantasyon için
- `test/`: Test eklemeleri için

### 3. Değişiklikleri Yapma

1. Kodunuzu yazın
2. Kod stilini kontrol edin: `./gradlew ktlintCheck`
3. Testleri çalıştırın: `./gradlew testDebugUnitTest`
4. Değişikliklerinizi commit edin

---

## Kod Stili

### Kotlin Code Style

Proje **Kotlin Official Code Style** kullanır. Kod stili otomatik olarak ktlint ile kontrol edilir.

#### Temel Kurallar

1. **Indentation:** 4 boşluk (tab değil)
2. **Line Length:** Maksimum 120 karakter
3. **Naming:**
   - Classes: PascalCase (`MainActivity`)
   - Functions: camelCase (`getMovies`)
   - Constants: UPPER_SNAKE_CASE (`API_BASE_URL`)
   - Private properties: camelCase with underscore prefix (`_uiState`)

#### Örnekler

```kotlin
// ✅ İyi
class MainViewModel @Inject constructor(
    private val repository: ContentRepository
) : BaseViewModel() {
    private val _uiState = MutableStateFlow<UiState>(UiState.Initial)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    
    fun loadContent() {
        viewModelScope.launch {
            // ...
        }
    }
}

// ❌ Kötü
class MainViewModel @Inject constructor(private val repository:ContentRepository):BaseViewModel(){
    private val uiState=MutableStateFlow<UiState>(UiState.Initial)
    fun loadContent(){
        viewModelScope.launch{/*...*/}
    }
}
```

### Ktlint Kontrolü

Kod stilini kontrol etmek için:

```bash
./gradlew ktlintCheck
```

Kod stilini otomatik düzeltmek için:

```bash
./gradlew ktlintFormat
```

### Import Organizasyonu

Import'lar şu sırayla organize edilmelidir:

1. Android imports
2. Kotlin imports
3. Third-party library imports
4. Project imports

```kotlin
// Android
import android.content.Context
import androidx.lifecycle.ViewModel

// Kotlin
import kotlinx.coroutines.flow.StateFlow

// Third-party
import dagger.hilt.android.lifecycle.HiltViewModel

// Project
import com.pnr.tv.repository.ContentRepository
```

---

## Pull Request Süreci

### 1. PR Oluşturma

1. Değişikliklerinizi commit edin
2. Branch'inizi push edin:
   ```bash
   git push origin feature/your-feature-name
   ```
3. GitHub'da Pull Request oluşturun

### 2. PR Açıklaması

PR açıklamasında şunları belirtin:

- **Ne yapıldı:** Yapılan değişikliklerin özeti
- **Neden yapıldı:** Değişikliğin gerekçesi
- **Nasıl test edildi:** Test adımları
- **Ekran görüntüleri:** UI değişiklikleri varsa
- **Breaking changes:** Varsa belirtin

**PR Template Örneği:**

```markdown
## Açıklama
Bu PR, [özellik/hata düzeltmesi] ekler/düzeltir.

## Değişiklikler
- [ ] Yeni özellik eklendi
- [ ] Hata düzeltildi
- [ ] Dokümantasyon güncellendi
- [ ] Test eklendi

## Test
- [ ] Unit testler geçti
- [ ] Manuel test yapıldı
- [ ] Android TV emülatöründe test edildi

## Ekran Görüntüleri
[Varsa ekleyin]

## Breaking Changes
[Varsa belirtin]
```

### 3. Code Review

- Tüm PR'lar code review'den geçmelidir
- En az bir onay alınmalıdır
- CI/CD kontrolleri geçmelidir

### 4. Merge

- PR onaylandıktan sonra merge edilir
- Squash and merge tercih edilir
- Merge'den sonra branch silinir

---

## Commit Mesajları

### Format

```
<type>(<scope>): <subject>

<body>

<footer>
```

### Types

- `feat`: Yeni özellik
- `fix`: Hata düzeltmesi
- `docs`: Dokümantasyon
- `style`: Kod stili (formatting)
- `refactor`: Kod iyileştirmesi
- `test`: Test ekleme/düzeltme
- `chore`: Build, config, vb.

### Örnekler

```bash
# Yeni özellik
feat(player): Add resume playback feature

# Hata düzeltmesi
fix(ui): Fix focus issue in content grid

# Dokümantasyon
docs(api): Add API documentation

# Refactor
refactor(repository): Simplify content loading logic
```

### Kısa Commit Mesajları

Basit değişiklikler için kısa commit mesajları da kabul edilir:

```bash
fix: Fix crash on empty category list
docs: Update README
```

---

## Test Yazma

### Unit Tests

**Konum:** `app/src/test/java/com/pnr/tv/`

**Örnek:**

```kotlin
@RunWith(MockitoJUnitRunner::class)
class ContentRepositoryTest {
    @Mock
    private lateinit var apiService: ApiService
    
    @Mock
    private lateinit var movieDao: MovieDao
    
    @InjectMocks
    private lateinit var repository: ContentRepository
    
    @Test
    fun `getMovies should return cached data when available`() = runTest {
        // Given
        val cachedMovies = listOf(MovieEntity(...))
        whenever(movieDao.getAllMovies()).thenReturn(flowOf(cachedMovies))
        
        // When
        val result = repository.getMovies().first()
        
        // Then
        assertEquals(cachedMovies, result)
    }
}
```

### Instrumented Tests

**Konum:** `app/src/androidTest/java/com/pnr/tv/`

**Örnek:**

```kotlin
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule
    var hiltRule = HiltAndroidRule(this)
    
    @Test
    fun testMainActivityLaunches() {
        val scenario = launchActivity<MainActivity>()
        // Test assertions
    }
}
```

### Test Çalıştırma

```bash
# Tüm testler
./gradlew test

# Sadece unit testler
./gradlew testDebugUnitTest

# Sadece instrumented testler
./gradlew connectedDebugAndroidTest
```

---

## Dokümantasyon

### Kod Dokümantasyonu

Tüm public API'ler için KDoc yazın:

```kotlin
/**
 * Film listesini getirir.
 * 
 * Önce yerel veritabanından okur, eğer veri yoksa API'den çeker.
 * 
 * @param categoryId Kategori ID'si (opsiyonel, tüm filmler için null)
 * @return Film listesi Flow'u
 */
suspend fun getMovies(categoryId: String? = null): Flow<List<MovieEntity>>
```

### Markdown Dokümantasyonu

Yeni özellikler için dokümantasyon ekleyin:

- `API_DOCUMENTATION.md`: API değişiklikleri
- `ARCHITECTURE.md`: Mimari değişiklikleri
- `SETUP_GUIDE.md`: Kurulum değişiklikleri

---

## Kod İnceleme Kontrol Listesi

PR göndermeden önce kontrol edin:

- [ ] Kod stili kurallarına uygun (`ktlintCheck` geçti)
- [ ] Tüm testler geçiyor
- [ ] Yeni testler eklendi (gerekirse)
- [ ] Dokümantasyon güncellendi
- [ ] Commit mesajları açıklayıcı
- [ ] Breaking changes belirtildi
- [ ] Ekran görüntüleri eklendi (UI değişiklikleri için)

---

## Sorun Bildirme

### Bug Report

Bir hata bulduysanız:

1. GitHub Issues'da yeni bir issue oluşturun
2. **Bug Report** template'ini kullanın
3. Şunları belirtin:
   - Hatanın açıklaması
   - Adımlar (reproduce etmek için)
   - Beklenen davranış
   - Gerçek davranış
   - Ekran görüntüleri (varsa)
   - Cihaz/Emülatör bilgisi
   - Android versiyonu

### Feature Request

Yeni özellik önerisi için:

1. GitHub Issues'da yeni bir issue oluşturun
2. **Feature Request** template'ini kullanın
3. Şunları belirtin:
   - Özelliğin açıklaması
   - Kullanım senaryosu
   - Alternatif çözümler (varsa)

---

## İletişim

Sorularınız için:

- GitHub Issues kullanın
- Discussions bölümüne göz atın

---

## Lisans

Bu projeye katkıda bulunarak, katkılarınızın proje lisansı altında yayınlanacağını kabul etmiş olursunuz.

---

## Teşekkürler

PNR TV projesine katkıda bulunduğunuz için teşekkürler! 🎉

