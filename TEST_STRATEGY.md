# Test Stratejisi

## Genel Bakış

PNR TV projesi için kapsamlı bir test stratejisi uygulanmaktadır. Bu dokümantasyon, test yaklaşımını, test türlerini ve best practice'leri açıklar.

## Test Piramidi

```
        /\
       /  \
      / UI \
     /------\
    /Integration\
   /------------\
  /   Unit Tests  \
 /----------------\
```

### 1. Unit Tests (Temel)

**Hedef Coverage**: %70+

**Kapsam**:
- ViewModels
- Repositories
- Use Cases
- Utilities
- Helpers

**Konum**: `app/src/test/java/com/pnr/tv/`

**Framework'ler**:
- JUnit 4.13.2
- Mockito 5.12.0
- Mockito Kotlin 5.3.1
- Coroutines Test 1.7.3
- Turbine 1.0.0

**Örnek**:
```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class FavoriteRepositoryTest {
    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()
    
    @Test
    fun `addFavorite should call dao with correct entity`() = runTest {
        // Given
        val channelId = 1
        val viewerId = 1
        
        // When
        repository.addFavorite(channelId, viewerId)
        
        // Then
        verify(mockFavoriteDao).addFavorite(...)
    }
}
```

### 2. Integration Tests (Orta)

**Hedef**: Database ve API entegrasyonları

**Kapsam**:
- DAO testleri (Room)
- API testleri (MockWebServer)
- Repository entegrasyon testleri

**Konum**: `app/src/androidTest/java/com/pnr/tv/`

**Framework'ler**:
- Room Testing 2.6.1
- MockWebServer 4.12.0
- Hilt Testing 2.51.1

**Örnek**:
```kotlin
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class UserDaoTest {
    @get:Rule
    var hiltRule = HiltAndroidRule(this)
    
    @Test
    fun insertAndGetUser() = runTest {
        // Test database operations
    }
}
```

### 3. UI Tests (Üst)

**Hedef**: Kullanıcı arayüzü testleri

**Kapsam**:
- Activity testleri
- Fragment testleri
- Navigation testleri

**Konum**: `app/src/androidTest/java/com/pnr/tv/`

**Framework'ler**:
- Espresso 3.6.1
- Fragment Testing
- Truth 1.4.2

**Örnek**:
```kotlin
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @Test
    fun testMainActivityLaunches() {
        val scenario = launchActivity<MainActivity>()
        // Test assertions
    }
}
```

## Test Data Management

### TestDataFactory

Test verileri için merkezi bir factory kullanılır:

**Konum**: `app/src/test/java/com/pnr/tv/testdata/TestDataFactory.kt`

**Kullanım**:
```kotlin
val testUser = TestDataFactory.createUserAccountEntity(
    id = 1,
    accountName = "Test Account"
)

val testMovies = TestDataFactory.createMovieEntities(count = 10)
```

**Avantajlar**:
- Tutarlı test verileri
- Kolay bakım
- Tekrar kullanılabilirlik

## Test Utilities

### MainCoroutineRule

Coroutine testleri için yardımcı rule:

```kotlin
@get:Rule
val mainCoroutineRule = MainCoroutineRule()
```

**Özellikler**:
- Test dispatcher yönetimi
- Coroutine test utilities
- Otomatik cleanup

## Test Coverage

### Jacoco Configuration

**Hedef**: %70+ coverage

**Rapor**: `app/build/reports/jacoco/jacocoTestReport/html/index.html`

**Komut**:
```bash
./gradlew jacocoTestReport
```

### Coverage Verification

**Komut**:
```bash
./gradlew jacocoTestCoverageVerification
```

**Minimum**: %50 (build.gradle'da tanımlı)

## CI/CD Integration

### GitHub Actions

**Workflow**: `.github/workflows/ci.yml`

**İşlemler**:
1. Unit testler çalıştırılır
2. Coverage raporu oluşturulur
3. Lint kontrolleri yapılır
4. Build işlemi gerçekleştirilir

**Artifacts**:
- Test coverage HTML raporu
- Debug APK

## Test Best Practices

### 1. Test İsimlendirme

**Format**: `should_expectedBehavior_when_stateUnderTest`

**Örnek**:
```kotlin
@Test
fun `addFavorite should call dao with correct entity when userId exists`()
```

### 2. AAA Pattern

**Arrange-Act-Assert** pattern kullanılır:

```kotlin
@Test
fun testExample() = runTest {
    // Arrange (Given)
    val testData = TestDataFactory.createMovieEntity()
    
    // Act (When)
    val result = repository.getMovie(testData.streamId)
    
    // Assert (Then)
    assertEquals(testData, result)
}
```

### 3. Mocking

- **Mockito** kullanılır
- Sadece gerekli bağımlılıklar mock'lanır
- Mock'lar her test için yeniden oluşturulur

### 4. Flow Testing

**Turbine** kullanılır:

```kotlin
result.test {
    val item = awaitItem()
    assertEquals(expected, item)
    cancelAndIgnoreRemainingEvents()
}
```

### 5. Coroutine Testing

**runTest** kullanılır:

```kotlin
@Test
fun testCoroutine() = runTest {
    // Test code
    advanceUntilIdle()
}
```

## Test Kapsamı

### Mevcut Testler

**Unit Tests** (22 dosya):
- ✅ ViewModels (7)
- ✅ Repositories (5)
- ✅ Use Cases (1)
- ✅ Utilities (4)
- ✅ Network (2)

**Integration Tests** (2 dosya):
- ✅ Fragment tests (1)
- ✅ DAO tests (1)

### Eksik Testler

**Unit Tests**:
- ⚠️ FavoriteRepository
- ⚠️ PlaybackPositionRepository
- ⚠️ RecentlyWatchedRepository
- ⚠️ TmdbRepository
- ⚠️ SharedViewModel
- ⚠️ LiveStreamViewModel
- ⚠️ SeriesViewModel
- ⚠️ MovieViewModel
- ⚠️ SessionManager
- ⚠️ SortPreferenceManager

**Integration Tests**:
- ⚠️ Database testleri (DAO testleri)
- ⚠️ API testleri (MockWebServer)

**UI Tests**:
- ⚠️ Activity testleri
- ⚠️ Fragment testleri
- ⚠️ Navigation testleri

## Test Çalıştırma

### Tüm Testler

```bash
./gradlew test
```

### Sadece Unit Testler

```bash
./gradlew testDebugUnitTest
```

### Sadece Instrumented Testler

```bash
./gradlew connectedDebugAndroidTest
```

### Coverage Raporu

```bash
./gradlew jacocoTestReport
```

## Sorun Giderme

### Test Başarısız Olursa

1. **Cache temizle**:
   ```bash
   ./gradlew clean
   ```

2. **Testleri tekrar çalıştır**:
   ```bash
   ./gradlew test --rerun-tasks
   ```

3. **Lint hatalarını kontrol et**:
   ```bash
   ./gradlew ktlintCheck
   ```

## Gelecek İyileştirmeler

1. **Test Coverage Artırma**: %70+ hedef
2. **Integration Testler**: Daha fazla entegrasyon testi
3. **UI Testler**: Kapsamlı UI test suite
4. **Performance Testler**: Performans testleri ekle
5. **Snapshot Testler**: UI snapshot testleri

---

**Son Güncelleme**: 2024-01-XX  
**Versiyon**: 1.0.0

