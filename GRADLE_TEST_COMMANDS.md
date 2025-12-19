# Gradle Test Komutları Açıklaması

## `./gradlew testDebugUnitTest` Komutu

### Ne İşe Yarar?

Bu komut, Android projenizdeki **unit testleri** çalıştırır. Özellikle:

1. **Debug build variant** için unit testleri çalıştırır
2. **JVM üzerinde** çalışır (Android cihaz/emülatör gerektirmez)
3. **Hızlıdır** - Instrumented testlerden çok daha hızlı
4. **Test coverage verisi** toplar (JaCoCo ile)

### Hangi Testleri Çalıştırır?

`app/src/test/java/` klasöründeki tüm test dosyalarını çalıştırır:

```
app/src/test/java/com/pnr/tv/
├── AddUserViewModelTest.kt
├── MainViewModelTest.kt
├── UserRepositoryTest.kt
├── util/
│   ├── DataValidationHelperTest.kt
│   ├── IntentValidatorTest.kt
│   └── ...
├── ui/
│   ├── movies/
│   │   ├── MovieDetailViewModelTest.kt
│   │   └── MovieViewModelTest.kt
│   ├── livestreams/
│   │   └── LiveStreamViewModelTest.kt
│   └── ...
└── ...
```

### Komut Yapısı

```
./gradlew [task_name]
```

- `./gradlew` - Gradle wrapper (projeye özel Gradle versiyonu)
- `testDebugUnitTest` - Gradle task adı

### Task Adı Açıklaması

- `test` - Test çalıştırma
- `Debug` - Debug build variant
- `UnitTest` - Unit testler (instrumented testler değil)

### Çıktı

Komut çalıştığında:

1. **Test sonuçları** terminalde gösterilir:
   ```
   > Task :app:testDebugUnitTest
   
   com.pnr.tv.ui.movies.MovieViewModelTest > selectMovieCategory should update selected category PASSED
   com.pnr.tv.ui.movies.MovieViewModelTest > loadMoviesIfNeeded should skip loading when data exists PASSED
   ...
   
   BUILD SUCCESSFUL in 45s
   23 actionable tasks: 23 executed
   ```

2. **Test raporları** oluşturulur:
   - `app/build/test-results/testDebugUnitTest/` - XML test sonuçları
   - `app/build/reports/tests/testDebugUnitTest/` - HTML test raporu

3. **Coverage verisi** toplanır:
   - `app/build/jacoco/testDebugUnitTest.exec` - Coverage execution data

### Diğer İlgili Komutlar

#### 1. Tüm Testleri Çalıştırma
```bash
./gradlew test
```
Hem debug hem release için tüm testleri çalıştırır.

#### 2. Sadece Release Testleri
```bash
./gradlew testReleaseUnitTest
```

#### 3. Belirli Bir Test Sınıfı
```bash
./gradlew testDebugUnitTest --tests "com.pnr.tv.ui.movies.MovieViewModelTest"
```

#### 4. Belirli Bir Test Metodu
```bash
./gradlew testDebugUnitTest --tests "com.pnr.tv.ui.movies.MovieViewModelTest.selectMovieCategory should update selected category"
```

#### 5. Test Coverage Raporu
```bash
./gradlew jacocoTestReport
```
**Not:** Bu komut `testDebugUnitTest`'i otomatik olarak çalıştırır (build.gradle'da `dependsOn` ile tanımlı).

#### 6. Coverage Verification
```bash
./gradlew jacocoTestCoverageVerification
```
Minimum coverage kontrolü yapar (%50 - build.gradle'da tanımlı).

### Unit Test vs Instrumented Test

| Özellik | Unit Test | Instrumented Test |
|---------|-----------|-------------------|
| **Komut** | `testDebugUnitTest` | `connectedDebugAndroidTest` |
| **Konum** | `app/src/test/` | `app/src/androidTest/` |
| **Çalışma Ortamı** | JVM (bilgisayarınızda) | Android cihaz/emülatör |
| **Hız** | Çok hızlı | Yavaş |
| **Android API** | Mock'lanmalı | Gerçek API |
| **Kullanım** | ViewModel, Repository, Utility testleri | UI, Activity, Fragment testleri |

### Windows'ta Kullanım

Windows'ta `gradlew.bat` kullanın:

```cmd
gradlew.bat testDebugUnitTest
```

veya PowerShell'de:

```powershell
.\gradlew testDebugUnitTest
```

### CI/CD'de Kullanım

Projenizde GitHub Actions'da kullanılıyor:

```yaml
- name: Run unit tests
  run: ./gradlew testDebugUnitTest --no-daemon
```

`--no-daemon` flag'i CI ortamlarında önerilir.

### Sorun Giderme

#### Testler Çalışmıyor
```bash
# Temizle ve tekrar dene
./gradlew clean
./gradlew testDebugUnitTest
```

#### Sadece Başarısız Testleri Göster
```bash
./gradlew testDebugUnitTest --continue
```

#### Test Sonuçlarını Detaylı Göster
```bash
./gradlew testDebugUnitTest --info
```

## 🚨 Terminalde Hata Görürseniz Ne Yapmalısınız?

### 1. Hata Türünü Belirleyin

Terminalde gördüğünüz hatayı kategorize edin:

#### A) Compilation (Derleme) Hataları

**Örnek Hata:**
```
error: unresolved reference: mock
error: cannot find symbol: class MovieViewModel
```

**Çözüm:**
```bash
# 1. Temizle
./gradlew clean

# 2. Gradle sync yap (Android Studio'da)
# File → Sync Project with Gradle Files

# 3. Tekrar dene
./gradlew testDebugUnitTest
```

**Eğer hala çalışmıyorsa:**
- Test dosyasındaki import'ları kontrol edin
- Eksik dependency olup olmadığını kontrol edin (`app/build.gradle`)
- Android Studio'yu yeniden başlatın

#### B) Test Failures (Test Başarısızlıkları)

**Örnek Hata:**
```
com.pnr.tv.ui.movies.MovieViewModelTest > test FAILED
    java.lang.AssertionError: expected:<value1> but was:<value2>
```

**Çözüm:**
1. **Hatanın detaylarını görün:**
   ```bash
   # HTML raporunu açın
   # app/build/reports/tests/testDebugUnitTest/index.html
   ```

2. **Sadece başarısız testi çalıştırın:**
   ```bash
   ./gradlew testDebugUnitTest --tests "com.pnr.tv.ui.movies.MovieViewModelTest.testMethodName"
   ```

3. **Test kodunu kontrol edin:**
   - Assertion'lar doğru mu?
   - Mock'lar doğru yapılandırılmış mı?
   - Test verileri doğru mu?

#### C) Mockito/Mocking Hataları

**Örnek Hata:**
```
org.mockito.exceptions.misusing.UnfinishedStubbingException
org.mockito.exceptions.misusing.WrongTypeOfReturnValue
```

**Çözüm:**
```kotlin
// ❌ YANLIŞ - suspend fonksiyon için
whenever(mockRepository.getData()).thenReturn(data)

// ✅ DOĞRU - suspend fonksiyon için
runBlocking {
    whenever(mockRepository.getData()).thenReturn(data)
}
```

**Veya:**
```kotlin
// ✅ DOĞRU - Flow için
whenever(mockRepository.getDataFlow()).thenReturn(flowOf(data))
```

#### D) Coroutine Hataları

**Örnek Hata:**
```
java.lang.IllegalStateException: This job has not completed yet
kotlinx.coroutines.test.UncompletedCoroutinesError
```

**Çözüm:**
```kotlin
@Test
fun `test method`() = runTest {  // ✅ runTest kullanın
    // Given
    // When
    viewModel.doSomething()
    advanceUntilIdle()  // ✅ Coroutine'leri tamamlayın
    
    // Then
}
```

#### E) Flow Test Hataları

**Örnek Hata:**
```
java.util.concurrent.TimeoutException
```

**Çözüm:**
```kotlin
@Test
fun `test flow`() = runTest {
    // Given
    whenever(mockRepository.getFlow()).thenReturn(flowOf(data))
    
    // When & Then
    viewModel.dataFlow.test {
        val item = awaitItem()  // ✅ awaitItem kullanın
        assertEquals(expected, item)
        cancelAndIgnoreRemainingEvents()  // ✅ Temizleyin
    }
}
```

#### F) Context/String Resource Hataları

**Örnek Hata:**
```
android.content.res.Resources$NotFoundException: String resource ID #0x...
```

**Çözüm:**
```kotlin
@Before
fun setup() {
    mockContext = mock()
    // ✅ Tüm string resource'ları mock'layın
    whenever(mockContext.getString(any())).thenReturn("Mock String")
    whenever(mockContext.getString(any(), any())).thenReturn("Mock String")
}
```

### 2. Adım Adım Sorun Giderme

#### Adım 1: Temizleme
```bash
# Windows
gradlew.bat clean

# Linux/Mac
./gradlew clean
```

#### Adım 2: Gradle Sync
Android Studio'da:
- **File → Sync Project with Gradle Files**

#### Adım 3: Sadece Başarısız Testi Çalıştır
```bash
# Belirli bir test sınıfı
./gradlew testDebugUnitTest --tests "com.pnr.tv.ui.movies.MovieViewModelTest"

# Belirli bir test metodu
./gradlew testDebugUnitTest --tests "com.pnr.tv.ui.movies.MovieViewModelTest.testMethodName"
```

#### Adım 4: Detaylı Log
```bash
# Daha fazla bilgi için
./gradlew testDebugUnitTest --info --stacktrace
```

#### Adım 5: HTML Raporunu Kontrol Et
```bash
# Raporu açın
# app/build/reports/tests/testDebugUnitTest/index.html
```

### 3. Yaygın Hatalar ve Çözümleri

#### Hata: "ClassNotFoundException"
```bash
# Çözüm: Clean ve rebuild
./gradlew clean
./gradlew testDebugUnitTest
```

#### Hata: "No tests found"
```bash
# Çözüm: Test dosyasının doğru yerde olduğundan emin olun
# app/src/test/java/com/pnr/tv/.../TestClass.kt

# Test metodunun @Test annotation'ı olduğundan emin olun
```

#### Hata: "Mockito cannot mock final class"
```bash
# Çözüm: build.gradle'a ekleyin
testImplementation 'org.mockito:mockito-inline:5.12.0'
```

#### Hata: "Hilt dependency injection error"
```bash
# Çözüm: Test sınıfında Hilt kullanmıyorsanız @HiltAndroidTest kaldırın
# Veya test için Hilt'i doğru yapılandırın
```

### 4. Debug İpuçları

#### Test'i Debug Modda Çalıştır
Android Studio'da:
1. Test dosyasını açın
2. Test metodunun yanındaki yeşil oka tıklayın
3. "Debug" seçeneğini seçin
4. Breakpoint koyup adım adım ilerleyin

#### Log Ekleme
```kotlin
@Test
fun `test method`() = runTest {
    println("DEBUG: Test başladı")
    // Test kodu
    println("DEBUG: Test bitti")
}
```

### 5. Hızlı Kontrol Listesi

Hata görürseniz şunları kontrol edin:

- [ ] Test dosyası `app/src/test/` klasöründe mi?
- [ ] Test metodu `@Test` annotation'ına sahip mi?
- [ ] `runTest { }` kullanıyor musunuz? (coroutine testleri için)
- [ ] Mock'lar doğru yapılandırılmış mı?
- [ ] Context mock'lanmış mı? (string resource'lar için)
- [ ] Flow testlerinde `cancelAndIgnoreRemainingEvents()` çağrılıyor mu?
- [ ] `advanceUntilIdle()` kullanılıyor mu? (coroutine testleri için)
- [ ] Import'lar doğru mu?

### 6. Yardım Alma

Eğer hata devam ediyorsa:

1. **Hata mesajının tamamını kopyalayın**
2. **Test dosyasını paylaşın**
3. **build.gradle'daki test dependencies'i kontrol edin**
4. **Stack trace'i inceleyin** (en önemli kısım genelde en üstte)

### 7. Örnek Hata Senaryoları

#### Senaryo 1: Mockito Stubbing Hatası
```kotlin
// ❌ HATA
whenever(mockRepository.getData()).thenReturn(data)  // suspend fonksiyon

// ✅ ÇÖZÜM
runBlocking {
    whenever(mockRepository.getData()).thenReturn(data)
}
```

#### Senaryo 2: Flow Test Timeout
```kotlin
// ❌ HATA
viewModel.dataFlow.test {
    val item = awaitItem()  // Timeout
}

// ✅ ÇÖZÜM
viewModel.dataFlow.test {
    val item = awaitItem()
    assertEquals(expected, item)
    cancelAndIgnoreRemainingEvents()  // Önemli!
}
```

#### Senaryo 3: Context String Resource
```kotlin
// ❌ HATA
whenever(mockContext.getString(R.string.error_unknown)).thenReturn("Error")

// ✅ ÇÖZÜM
whenever(mockContext.getString(any())).thenReturn("Mock String")
whenever(mockContext.getString(any(), any())).thenReturn("Mock String")
```

### Özet

`./gradlew testDebugUnitTest` komutu:
- ✅ Debug build için unit testleri çalıştırır
- ✅ JVM üzerinde çalışır (hızlı)
- ✅ Test sonuçlarını gösterir
- ✅ Coverage verisi toplar
- ✅ HTML/XML raporlar oluşturur

Bu komut, kod kalitesini kontrol etmek ve test coverage'ı ölçmek için kullanılır.

