# Proje Kapsamlı Hard-Coded String Analizi ve Çözüm Yolları

Merhaba,

Bu doküman, Android projenizdeki hard-coded (doğrudan koda yazılmış) metinlerin analizini ve bu metinlerin nasıl düzeltileceğine dair bir yol haritası sunmaktadır. Bu değişikliklerin yapılması, uygulamanızın farklı dillere çevrilmesini (yerelleştirme) kolaylaştıracak ve kodun bakımını daha verimli hale getirecektir.

---

## 1. Tespit Edilen Sorunlar

Aşağıdaki dosyalarda, `strings.xml` kaynak dosyası kullanılmadan doğrudan koda veya XML layout dosyalarına yazılmış metinler tespit edilmiştir.

### XML Layout Dosyaları

-   **`app/src/main/res/layout/activity_main.xml`**
    -   `TextView` (ID: `@+id/tv_current_user`): `android:text="Mevcut Kullanıcı: -"`
-   **`app/src/main/res/layout/player_settings_panel.xml`**
    -   `TextView` (ID: `@+id/txt_subtitle_title`): `android:text="Alt Yazılar"`
    -   `Button` (ID: `@+id/btn_save_settings`): `android:text="Kaydet"`
-   **`app/src/main/res/layout/custom_player_controls.xml`**
    -   `ImageButton` (ID: `@id/exo_rew`): `android:contentDescription="Geri sarma"`
    -   `ImageButton` (ID: `@id/exo_play`): `android:contentDescription="Oynat"`
    -   `ImageButton` (ID: `@id/exo_pause`): `android:contentDescription="Durdur"`
    -   `ImageButton` (ID: `@id/exo_ffwd`): `android:contentDescription="İleri sarma"`
    -   `ImageButton` (ID: `@+id/exo_subtitle`): `android:contentDescription="Altyazı"`
    -   `ImageButton` (ID: `@+id/exo_audio_track`): `android:contentDescription="Ses Dili"`
-   **`app/src/main/res/layout/dialog_user_status.xml`**
    -   `TextView` (ID: `@+id/tv_current_user`): `android:text="Mevcut Kullanıcı: -"`
    -   `TextView` (ID: `@+id/tv_status`): `android:text="Durum: -"`
    -   `TextView` (ID: `@+id/tv_expiry_date`): `android:text="Bitiş Tarihi: -"`
    -   `TextView` (ID: `@+id/tv_connection`): `android:text="Bağlantı: -"`
    -   `TextView` (ID: `@+id/tv_trial`): `android:text="Trial: -"`
-   **`app/src/main/res/layout/item_episode_row.xml`**
    -   `TextView` (Ayırıcı): `android:text="•"`
-   **`app/src/main/res/layout/item_track_selection.xml`**
    -   `TextView` (ID: `@+id/txt_track_name`): `android:text="Türkçe"`

### Kotlin Dosyaları

-   **`app/src/main/java/com/pnr/tv/PlayerControlView.kt`**
    -   `setContentInfo` metodu içinde: `"  •  "` ve `" ⭐"` gibi metin birleştirme karakterleri.
-   **`app/src/main/java/com/pnr/tv/SettingsActivity.kt`**
    -   `showUserStatusDialog` metodu içinde, API'den veri gelmediğinde varsayılan değer olarak: `"-"`
-   **`app/src/main/java/com/pnr/tv/util/LocaleHelper.kt`**
    -   `SupportedLanguage` enum'ı içindeki `displayName` değerleri: `"Türkçe"`, `"English"`, `"Español"`, vb.

### Eksik Dil Çevirileri

-   **`app/src/main/res/layout/layout_navbar.xml`**
    -   Arama çubuğunun ipucu metni (`@string/search_hint`) ve filtre butonunun içerik açıklaması (`@string/btn_filter`) doğru şekilde `strings.xml`'e referans verilmiş olmasına rağmen, dil değiştirildiğinde çevrilmiyor. Bu durum, diğer dil dosyalarında (ör: `values-en/strings.xml`, `values-es/strings.xml`) bu string anahtarlarının çevirilerinin eksik olduğunu göstermektedir.

---

## 2. Neden Düzeltilmeli?

1.  **Yerelleştirme (Localization):** Hard-coded string'ler, uygulamanızın farklı dillere çevrilmesini imkansız hale getirir. Tüm metinler `strings.xml` dosyasında olduğunda, sadece o dosyayı farklı diller için çevirerek uygulamanızı çok dilli yapabilirsiniz.
2.  **Bakım Kolaylığı:** Metinleri tek bir yerde toplamak, uygulama genelinde bir metni değiştirmek istediğinizde (örneğin "Kaydet" yerine "Sakla" yazmak) sadece `strings.xml` dosyasını düzenlemenizi sağlar. Aksi takdirde projedeki tüm dosyalarda bu metni arayıp bulmanız gerekir.
3.  **Erişilebilirlik:** `contentDescription` gibi özellikler, ekran okuyucular tarafından kullanılır. Bu metinlerin de yerelleştirilmesi, uygulamanın farklı dillerdeki görme engelli kullanıcılar için erişilebilir olmasını sağlar.

---

## 3. Çözüm İçin Adım Adım Yol Haritası

### Adım 1: `strings.xml` Dosyasını Zenginleştirme

Öncelikle, tespit edilen tüm hard-coded metinler için `app/src/main/res/values/strings.xml` dosyanızda yeni anahtarlar oluşturun.

**Örnek `strings.xml` Eklemleri:**

```xml
<resources>
    <!-- Mevcut string'leriniz... -->

    <!-- Genel -->
    <string name="data_unavailable">-</string>
    <string name="bullet_symbol">•</string>
    <string name="star_symbol">⭐</string>
    <string name="save">Kaydet</string>

    <!-- Ana Sayfa -->
    <string name="current_user_prefix">Mevcut Kullanıcı:</string>
    
    <!-- Player -->
    <string name="player_subtitles_title">Alt Yazılar</string>
    <string name="player_audio_language_title">Ses Dili</string>
    <string name="cd_rewind">Geri sarma</string>
    <string name="cd_play">Oynat</string>
    <string name="cd_pause">Durdur</string>
    <string name="cd_fast_forward">İleri sarma</string>
    <string name="cd_subtitle">Altyazı</string>
    <string name="cd_audio_track">Ses Dili</string>
    <string name="track_language_turkish">Türkçe</string>

    <!-- Kullanıcı Durumu Dialoğu -->
    <string name="status_prefix">Durum:</string>
    <string name="expiry_date_prefix">Bitiş Tarihi:</string>
    <string name="connection_prefix">Bağlantı:</string>
    <string name="trial_prefix">Trial:</string>

    <!-- Dil İsimleri (LocaleHelper için) -->
    <string name="language_turkish">Türkçe</string>
    <string name="language_english">English</string>
    <string name="language_spanish">Español</string>
    <string name="language_indonesian">Bahasa Indonesia</string>
    <string name="language_hindi">हिन्दी</string>
    <string name="language_portuguese">Português</string>
    <string name="language_french">Français</string>
</resources>
```

### Adım 2: XML Dosyalarını Güncelleme

`strings.xml`'e eklediğiniz anahtarları kullanarak ilgili XML dosyalarındaki hard-coded metinleri değiştirin.

**Örnek (`player_settings_panel.xml`):**

```xml
<!-- ESKİ HALİ -->
<TextView
    android:id="@+id/txt_subtitle_title"
    android:text="Alt Yazılar"
    ... />
<Button
    android:id="@+id/btn_save_settings"
    android:text="Kaydet"
    ... />

<!-- YENİ HALİ -->
<TextView
    android:id="@+id/txt_subtitle_title"
    android:text="@string/player_subtitles_title"
    ... />
<Button
    android:id="@+id/btn_save_settings"
    android:text="@string/save"
    ... />
```

Bu işlemi tespit edilen tüm XML dosyaları için tekrarlayın.

### Adım 3: Kotlin Dosyalarını Güncelleme

Kotlin dosyalarında, `Context` üzerinden `getString()` metodunu kullanarak string kaynaklarına erişin.

**Örnek (`SettingsActivity.kt`):**

```kotlin
// ESKİ HALİ
val status = userInfo.status ?: "-"
binding.tvStatus.text = getString(R.string.status_label, status)

// YENİ HALİ
val unavailable = getString(R.string.data_unavailable)
val status = userInfo.status ?: unavailable
binding.tvStatus.text = getString(R.string.status_label, status)
```

**Örnek (`LocaleHelper.kt`):**

`SupportedLanguage` enum'ını `displayName` yerine string resource ID'si tutacak şekilde güncelleyin.

```kotlin
// ESKİ HALİ
// enum class SupportedLanguage(val code: String, val androidCode: String, val displayName: String) {
//    TURKISH("tr", "tr", "Türkçe"), ... }

// YENİ HALİ (Öneri)
import androidx.annotation.StringRes

enum class SupportedLanguage(val code: String, val androidCode: String, @StringRes val nameResId: Int) {
    TURKISH("tr", "tr", R.string.language_turkish),
    ENGLISH("en", "en", R.string.language_english),
    // ... diğer diller
}

// Bu enum'ı kullanan Adapter içinde:
// holder.textView.text = holder.itemView.context.getString(language.nameResId)
```

### Adım 4: Eksik Çevirileri Tamamlama

Uygulamanızın desteklediği her dil için `res` klasörü altında `values-[dil_kodu]` (ör: `values-en`, `values-es`) klasörleri oluşturun. Her birinin içine `strings.xml` dosyası koyun ve `search_hint` ile `btn_filter` dahil olmak üzere tüm string anahtarlarının çevirilerini ekleyin.

**Örnek (`app/src/main/res/values-en/strings.xml`):**

```xml
<resources>
    <string name="app_name">PNR TV</string>
    <string name="search_hint">Search…</string>
    <string name="btn_filter">Filter</string>
    <string name="save">Save</string>
    <!-- Diğer tüm string'lerin İngilizce çevirileri -->
</resources>
```

---

Bu adımları takip ederek projenizi daha profesyonel, sürdürülebilir ve küresel bir kitleye hazır hale getirebilirsiniz.
