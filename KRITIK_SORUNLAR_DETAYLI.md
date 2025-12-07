# Kritik Sorunlar - Detaylı Açıklamalar

Bu dosya, projedeki 4 kritik sorunu detaylı olarak açıklar ve çözüm adımlarını içerir.

---

## 1. 🔴 DUPLICATE CardPresenter Sınıfı

### Sorunun Detayı

Projede **iki farklı** `CardPresenter` sınıfı var:

#### 1. Basit Versiyon (KULLANILMAMALI)
**Konum:** `app/src/main/java/com/pnr/tv/CardPresenter.kt`
```kotlin
package com.pnr.tv

import android.view.ViewGroup
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter

class CardPresenter : Presenter() {
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val cardView = ImageCardView(parent.context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setMainImageDimensions(
                Constants.CardDimensions.CARD_WIDTH_16_9,
                Constants.CardDimensions.CARD_HEIGHT_16_9,
            )
        }
        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val cardView = viewHolder.view as ImageCardView
        val title = item as? String ?: ""
        cardView.titleText = title
        cardView.contentText = ""
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val cardView = viewHolder.view as ImageCardView
        cardView.mainImage = null
    }
}
```

**Özellikler:**
- ❌ Sadece `String` tipinde title kabul ediyor
- ❌ Resim yükleme yok
- ❌ `ImageCardView` kullanıyor (layout sorunları var)
- ❌ Dinamik boyutlandırma yok

#### 2. Gelişmiş Versiyon (KULLANILMALI)
**Konum:** `app/src/main/java/com/pnr/tv/ui/browse/CardPresenter.kt`
```kotlin
package com.pnr.tv.ui.browse

import android.view.ViewGroup
import androidx.leanback.widget.Presenter
import coil.load
import coil.size.Scale
import com.pnr.tv.Constants
import com.pnr.tv.R
import com.pnr.tv.model.ContentItem

class CardPresenter : Presenter() {
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        // CustomImageCardView kullan - ImageCardView'ın internal layout sorununu çözmek için
        val cardView = CustomImageCardView(parent.context)

        // Ekran genişliğini al ve dinamik olarak hesapla
        val screenWidth = parent.context.resources.displayMetrics.widthPixels
        val cardWidth = (screenWidth / Constants.CARD_WIDTH_DIVISOR).toInt()

        val layoutParams = ViewGroup.LayoutParams(
            cardWidth,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        cardView.layoutParams = layoutParams

        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val cardView = viewHolder.view as CustomImageCardView
        val contentItem = item as? ContentItem

        if (contentItem == null) {
            // Placeholder göster
            cardView.mainImageView?.load(R.drawable.placeholder_image) {
                scale(Scale.FIT)
            }
            return
        }

        cardView.titleText = contentItem.title
        cardView.contentText = ""

        // Coil ile resim yükleme
        val imageUrl = contentItem.imageUrl
        if (!imageUrl.isNullOrBlank()) {
            cardView.mainImageView?.load(imageUrl) {
                placeholder(R.drawable.placeholder_image)
                error(R.drawable.placeholder_image)
                crossfade(true)
                scale(Scale.FILL)
            }
        } else {
            cardView.mainImageView?.load(R.drawable.placeholder_image) {
                scale(Scale.FIT)
            }
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val cardView = viewHolder.view as CustomImageCardView
        cardView.mainImage = null
    }
}
```

**Özellikler:**
- ✅ `ContentItem` interface'i ile çalışıyor (MovieEntity, SeriesEntity, LiveStreamEntity)
- ✅ Coil ile resim yükleme
- ✅ `CustomImageCardView` kullanıyor (layout sorunları çözülmüş)
- ✅ Dinamik boyutlandırma
- ✅ Placeholder desteği
- ✅ Error handling

### Sorunun Etkileri

1. **Import Karışıklığı:**
   ```kotlin
   // Hangi CardPresenter kullanılacak?
   import com.pnr.tv.CardPresenter  // ❌ Yanlış
   import com.pnr.tv.ui.browse.CardPresenter  // ✅ Doğru
   ```

2. **Derleme Hataları:**
   - Aynı isimde iki sınıf olduğu için import hataları oluşabilir
   - IDE hangi sınıfı kullanacağını bilemeyebilir

3. **Runtime Hataları:**
   - Yanlış CardPresenter kullanılırsa, resimler yüklenmez
   - UI düzgün çalışmaz

### Çözüm Adımları

#### Adım 1: Kullanım Yerlerini Kontrol Et
```bash
# Projede CardPresenter'ın nerede kullanıldığını bul
grep -r "CardPresenter" app/src/main/java/
```

#### Adım 2: Root'taki Dosyayı Sil
```bash
# Windows PowerShell
Remove-Item "app\src\main\java\com\pnr\tv\CardPresenter.kt"

# Veya Android Studio'da:
# Sağ tık -> Delete
```

#### Adım 3: Import'ları Kontrol Et
Tüm dosyalarda `CardPresenter` import'larının doğru olduğundan emin ol:
```kotlin
// ✅ Doğru import
import com.pnr.tv.ui.browse.CardPresenter

// ❌ Yanlış import (artık olmayacak)
import com.pnr.tv.CardPresenter
```

#### Adım 4: Projeyi Derle
```bash
./gradlew clean build
```

### Sonuç
- ✅ Tek bir `CardPresenter` sınıfı kalacak
- ✅ Import karışıklığı olmayacak
- ✅ Kod daha temiz olacak

---

## 2. 🔴 Destructive Database Migration

### Sorunun Detayı

**Konum:** `app/src/main/java/com/pnr/tv/di/DatabaseModule.kt:68`

**Mevcut Kod:**
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase {
        val builder = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "pnr-tv-database",
        )
            .addMigrations(
                DatabaseMigrations.MIGRATION_1_2,
                DatabaseMigrations.MIGRATION_2_3,
                // ... tüm migration'lar
                DatabaseMigrations.MIGRATION_17_18,
            )

        // ⚠️ SORUN: Her zaman destructive migration aktif!
        builder.fallbackToDestructiveMigration()
        timber.log.Timber.d("⚠️ Database: Destructive migration aktif (veri silinebilir)")

        return builder.build()
    }
}
```

### Sorunun Açıklaması

`fallbackToDestructiveMigration()` metodu, migration başarısız olduğunda **tüm veritabanını siler ve sıfırdan oluşturur**.

#### Ne Zaman Çalışır?
1. Migration hatası olduğunda
2. Veritabanı versiyonu uyumsuz olduğunda
3. Migration kodu hatalı olduğunda

#### Production'da Ne Olur?
```
Kullanıcı uygulamayı açıyor
    ↓
Veritabanı versiyonu 17'den 18'e geçmeye çalışıyor
    ↓
Migration hatası oluşuyor (örnek: SQL syntax hatası)
    ↓
fallbackToDestructiveMigration() devreye giriyor
    ↓
TÜM VERİLER SİLİNİYOR! ❌
    ↓
Kullanıcı:
  - Favorilerini kaybeder
  - İzleme geçmişini kaybeder
  - Kullanıcı hesaplarını kaybeder
  - Tüm yerel cache'i kaybeder
```

### Örnek Senaryo

**Kullanıcı:** 1000+ favori kanal, 500+ izlenen içerik  
**Durum:** Migration 17→18 hatası  
**Sonuç:** Tüm veriler silinir, kullanıcı sıfırdan başlar 😢

### Çözüm

#### Adım 1: BuildConfig Kontrolü Ekle
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase {
        val builder = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "pnr-tv-database",
        )
            .addMigrations(
                DatabaseMigrations.MIGRATION_1_2,
                DatabaseMigrations.MIGRATION_2_3,
                DatabaseMigrations.MIGRATION_3_4,
                DatabaseMigrations.MIGRATION_4_5,
                DatabaseMigrations.MIGRATION_5_6,
                DatabaseMigrations.MIGRATION_6_7,
                DatabaseMigrations.MIGRATION_7_8,
                DatabaseMigrations.MIGRATION_8_9,
                DatabaseMigrations.MIGRATION_9_10,
                DatabaseMigrations.MIGRATION_10_11,
                DatabaseMigrations.MIGRATION_11_12,
                DatabaseMigrations.MIGRATION_12_13,
                DatabaseMigrations.MIGRATION_13_14,
                DatabaseMigrations.MIGRATION_14_15,
                DatabaseMigrations.MIGRATION_15_16,
                DatabaseMigrations.MIGRATION_16_17,
                DatabaseMigrations.MIGRATION_17_18,
            )

        // ✅ ÇÖZÜM: Sadece DEBUG modda destructive migration
        if (BuildConfig.DEBUG) {
            builder.fallbackToDestructiveMigration()
            Timber.d("⚠️ Database: Destructive migration aktif (DEBUG mod)")
        } else {
            // Production'da migration başarısız olursa crash et
            // Bu sayede veri kaybı önlenir ve hatayı görebiliriz
            Timber.w("⚠️ Database: Destructive migration KAPALI (RELEASE mod)")
        }

        return builder.build()
    }
}
```

#### Adım 2: Migration Hatalarını Yakala
Production'da migration hatası olduğunda, uygulama crash edecek ve log'larda hatayı görebilirsiniz. Bu sayede:
- Hatayı tespit edersiniz
- Düzeltme yaparsınız
- Yeni bir migration ekleyebilirsiniz
- Kullanıcı verileri korunur

#### Adım 3: Migration Test Et
Her yeni migration eklediğinizde:
```kotlin
// Test için
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @Test
    fun testMigration17To18() {
        // Migration'ı test et
    }
}
```

### Sonuç

**Önce:**
- ❌ Production'da veri kaybı riski
- ❌ Migration hatası sessizce geçiştiriliyor
- ❌ Kullanıcı verileri korunmuyor

**Sonra:**
- ✅ Production'da veri korunuyor
- ✅ Migration hatası tespit ediliyor
- ✅ DEBUG modda hızlı geliştirme
- ✅ RELEASE modda güvenli

---

## 3. 🔴 Hardcoded API Key

### Sorunun Detayı

**Konum:** `app/src/main/java/com/pnr/tv/Constants.kt:225`

**Mevcut Kod:**
```kotlin
object Tmdb {
    const val BASE_URL = "https://api.themoviedb.org/3/"
    
    // ⚠️ SORUN: API key kod içinde hardcoded!
    const val API_KEY = "b38260e06bcb387355ab90a002a59ca5"
}
```

### Sorunun Açıklaması

#### Güvenlik Riskleri

1. **Kod İnceleme:**
   ```
   APK dosyası decompile edilebilir
       ↓
   Constants.kt dosyası okunabilir
       ↓
   API key görülebilir
       ↓
   Kötüye kullanılabilir
   ```

2. **Git Repository:**
   ```
   API key Git'e commit edilir
       ↓
   Repository public ise herkes görebilir
       ↓
   API key çalınabilir
       ↓
   API quota aşılabilir
       ↓
   Maliyet oluşabilir
   ```

3. **APK Analizi:**
   - APK dosyası `.apk` formatında bir ZIP dosyasıdır
   - İçindeki `.dex` dosyaları decompile edilebilir
   - Kotlin koduna dönüştürülebilir
   - API key görülebilir

### Örnek Senaryo

**Durum:** Proje GitHub'da public  
**Sonuç:** Herkes API key'i görebilir  
**Etki:** 
- API quota aşılabilir
- Maliyet oluşabilir
- API key iptal edilebilir

### Çözüm

#### Adım 1: local.properties Dosyasına Ekle
**Konum:** `local.properties` (proje root'unda)

```properties
# TMDB API Key
TMDB_API_KEY=b38260e06bcb387355ab90a002a59ca5
```

**Not:** `local.properties` zaten `.gitignore`'da olmalı (genellikle otomatik eklenir)

#### Adım 2: build.gradle'ı Güncelle
**Konum:** `app/build.gradle`

```kotlin
android {
    namespace 'com.pnr.tv'
    compileSdk 34

    defaultConfig {
        applicationId "com.pnr.tv"
        minSdk 21
        targetSdk 34
        versionCode 1
        versionName "1.0"
        multiDexEnabled true
        testInstrumentationRunner "com.pnr.tv.HiltTestRunner"
        
        // ✅ API key'i local.properties'ten oku
        val tmdbApiKey = project.findProperty("TMDB_API_KEY") as String?
            ?: throw GradleException("TMDB_API_KEY bulunamadı! local.properties dosyasına ekleyin.")
        
        buildConfigField "String", "TMDB_API_KEY", "\"$tmdbApiKey\""
    }
    
    // ... diğer ayarlar
}
```

#### Adım 3: Constants.kt'ı Güncelle
**Konum:** `app/src/main/java/com/pnr/tv/Constants.kt`

```kotlin
object Tmdb {
    /**
     * TMDB API base URL.
     */
    const val BASE_URL = "https://api.themoviedb.org/3/"

    /**
     * TMDB API key.
     * BuildConfig'den alınır (local.properties'ten okunur).
     */
    val API_KEY: String = BuildConfig.TMDB_API_KEY
}
```

#### Adım 4: .gitignore Kontrolü
**Konum:** `.gitignore`

```gitignore
# Local configuration files
local.properties

# Build files
build/
app/build/
```

#### Adım 5: README'ye Not Ekle
**Konum:** `README.md`

```markdown
## Kurulum

1. Projeyi klonlayın
2. `local.properties` dosyası oluşturun:
   ```properties
   TMDB_API_KEY=your_api_key_here
   ```
3. Projeyi derleyin
```

### Alternatif Çözüm: Gradle Properties

Eğer `local.properties` yerine `gradle.properties` kullanmak isterseniz:

**Konum:** `gradle.properties`
```properties
TMDB_API_KEY=b38260e06bcb387355ab90a002a59ca5
```

**Not:** `gradle.properties` genellikle Git'e commit edilir, bu yüzden dikkatli olun!

### Sonuç

**Önce:**
- ❌ API key kod içinde görünür
- ❌ Git'e commit edilebilir
- ❌ APK'dan çıkarılabilir
- ❌ Güvenlik riski

**Sonra:**
- ✅ API key kod içinde yok
- ✅ Git'e commit edilmez
- ✅ APK'dan çıkarılamaz (BuildConfig obfuscate edilebilir)
- ✅ Güvenli

---

## 4. 🔴 Boş ProGuard Rules

### Sorunun Detayı

**Konum:** `app/proguard-rules.pro`

**Mevcut Durum:**
```proguard
# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.





```

Dosya tamamen boş! Sadece yorum satırları var.

### Sorunun Açıklaması

#### ProGuard Nedir?

ProGuard, Android uygulamaları için:
- **Kod küçültme (Shrinking):** Kullanılmayan kod'u kaldırır
- **Optimizasyon:** Kodu optimize eder
- **Obfuscation:** Sınıf ve metod isimlerini anlamsız hale getirir

#### Şu Anki Durum

**build.gradle:**
```kotlin
buildTypes {
    release {
        minifyEnabled false  // ⚠️ ProGuard kapalı!
        proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
    }
}
```

**Sorun:** `minifyEnabled false` olduğu için ProGuard hiç çalışmıyor!

### Sorunun Etkileri

1. **APK Boyutu:**
   - Kullanılmayan kod'lar APK'da kalır
   - APK boyutu büyük olur
   - İndirme süresi uzar

2. **Güvenlik:**
   - Kod obfuscate edilmez
   - Reverse engineering kolay
   - API key'ler görülebilir

3. **Performans:**
   - Kod optimize edilmez
   - Daha yavaş çalışabilir

### Çözüm

#### Adım 1: ProGuard'ı Aktif Et
**Konum:** `app/build.gradle`

```kotlin
buildTypes {
    debug {
        testCoverageEnabled = true
        buildConfigField "boolean", "ENABLE_LOGGING", "true"
        buildConfigField "boolean", "IS_PRODUCTION", "false"
        // Debug'da ProGuard kapalı (hızlı build için)
        minifyEnabled false
    }
    release {
        // ✅ ProGuard'ı aktif et
        minifyEnabled true
        shrinkResources true  // Kullanılmayan resource'ları da kaldır
        
        proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        
        buildConfigField "boolean", "ENABLE_LOGGING", "false"
        buildConfigField "boolean", "IS_PRODUCTION", "true"
    }
}
```

#### Adım 2: ProGuard Rules Ekle
**Konum:** `app/proguard-rules.pro`

```proguard
# ============================================
# Retrofit
# ============================================
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# ============================================
# OkHttp
# ============================================
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# ============================================
# Moshi
# ============================================
-keep class com.squareup.moshi.** { *; }
-keep @com.squareup.moshi.JsonQualifier interface *
-keepclassmembers class * {
    @com.squareup.moshi.FromJson <methods>;
    @com.squareup.moshi.ToJson <methods>;
}

# ============================================
# Room Database
# ============================================
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-dontwarn androidx.room.paging.**

# ============================================
# Hilt (Dagger)
# ============================================
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-dontwarn dagger.hilt.**

# ============================================
# Parcelable
# ============================================
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# ============================================
# Data Classes (Room Entities, DTOs)
# ============================================
-keep class com.pnr.tv.db.entity.** { *; }
-keep class com.pnr.tv.network.dto.** { *; }

# ============================================
# Model Classes
# ============================================
-keep class com.pnr.tv.model.** { *; }

# ============================================
# Kotlin
# ============================================
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# ============================================
# Coil (Image Loading)
# ============================================
-keep class coil.** { *; }
-dontwarn coil.**

# ============================================
# ExoPlayer (Media3)
# ============================================
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ============================================
# Timber (Logging)
# ============================================
-keep class timber.log.** { *; }
-dontwarn timber.log.**

# ============================================
# Keep Application Class
# ============================================
-keep class com.pnr.tv.PnrTvApplication { *; }

# ============================================
# Keep ViewModels
# ============================================
-keep class * extends androidx.lifecycle.ViewModel { *; }

# ============================================
# Keep Activities and Fragments
# ============================================
-keep class * extends androidx.fragment.app.Fragment { *; }
-keep class * extends androidx.appcompat.app.AppCompatActivity { *; }

# ============================================
# Keep BuildConfig
# ============================================
-keep class com.pnr.tv.BuildConfig { *; }
```

#### Adım 3: Test Et
```bash
# Release build oluştur
./gradlew assembleRelease

# APK'yı analiz et
# Android Studio -> Build -> Analyze APK
```

#### Adım 4: Hata Ayıklama
Eğer ProGuard sonrası uygulama crash ederse:

1. **Mapping dosyasını kontrol et:**
   ```
   app/build/outputs/mapping/release/mapping.txt
   ```

2. **Crash log'unu decode et:**
   ```bash
   retrace.bat mapping.txt crash.log
   ```

3. **Gerekirse keep rule ekle:**
   ```proguard
   -keep class com.pnr.tv.YourClass { *; }
   ```

### Sonuç

**Önce:**
- ❌ ProGuard kapalı
- ❌ APK boyutu büyük
- ❌ Kod obfuscate edilmiyor
- ❌ Güvenlik riski

**Sonra:**
- ✅ ProGuard aktif
- ✅ APK boyutu küçük (%30-50 azalma)
- ✅ Kod obfuscate ediliyor
- ✅ Güvenli

### Beklenen İyileştirmeler

- **APK Boyutu:** %30-50 azalma
- **Performans:** %10-20 iyileşme
- **Güvenlik:** Reverse engineering zorlaşır

---

## 📋 Özet

| Sorun | Öncelik | Etki | Çözüm Süresi |
|-------|---------|------|--------------|
| Duplicate CardPresenter | 🔴 YÜKSEK | Kod karışıklığı | 5 dakika |
| Destructive Migration | 🔴 YÜKSEK | Veri kaybı | 10 dakika |
| Hardcoded API Key | 🔴 YÜKSEK | Güvenlik riski | 15 dakika |
| Boş ProGuard Rules | 🔴 YÜKSEK | APK boyutu, güvenlik | 30 dakika |

**Toplam Süre:** ~1 saat

---

## ✅ Sonraki Adımlar

1. Bu 4 sorunu çözün
2. Projeyi test edin
3. Release build oluşturun
4. APK'yı analiz edin
5. Production'a deploy edin

**Not:** Her değişiklikten sonra projeyi derleyip test edin!

