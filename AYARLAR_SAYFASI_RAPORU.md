# Ayarlar Sayfası Detaylı Raporu

## Genel Bakış
Ayarlar sayfası (`SettingsActivity`), uygulama ayarlarını yönetmek için üç ana bölümden oluşan bir ekrandır. Sayfa Android TV için optimize edilmiş, tutarlı görsel hiyerarşiye sahip bir tasarıma sahiptir.

---

## Yapısal Mimari

### Ana Activity
- **Dosya**: `app/src/main/java/com/pnr/tv/SettingsActivity.kt`
- **Layout**: `app/src/main/res/layout/activity_settings.xml`
- **Temel Sınıf**: `BaseActivity`
- **Dependency Injection**: Hilt (@AndroidEntryPoint)

### Fragment Yapısı
Sayfa üç fragment'a bölünmüştür (yukarıdan aşağıya sırayla):

1. **AccountSettingsFragment** (`fragment_account_settings.xml`)
   - Container ID: `settings_container`
   - Fragment Sınıfı: `com.pnr.tv.ui.settings.AccountSettingsFragment`

2. **GeneralSettingsFragment** (`fragment_general_settings.xml`)
   - Container ID: `general_settings_container`
   - Fragment Sınıfı: `com.pnr.tv.ui.settings.GeneralSettingsFragment`

3. **PremiumSettingsFragment** (`fragment_premium_settings.xml`)
   - Container ID: `premium_settings_container`
   - Fragment Sınıfı: `com.pnr.tv.ui.settings.PremiumSettingsFragment`

---

## Layout Yapısı

### Activity Layout (`activity_settings.xml`)

#### Ana Container
- **Root Layout**: `ConstraintLayout`
- **Arka Plan**: `#1a1a1a` (Koyu gri)
- **Yapı**:
  - Navbar (üstte)
  - NestedScrollView (ortada, scroll edilebilir içerik)
  - Banner AdView (altta, premium kullanıcılarda gizli)

#### İçerik Layout (NestedScrollView içinde)
- **Container**: `LinearLayout` (vertical)
- **Margin Top**: 32dp (navbar'dan boşluk)
- **Padding**: 
  - Start: 32dp
  - End: 32dp
  - Bottom: 32dp

#### Fragment Container'ları
Tüm fragment container'ları `FrameLayout` olarak tanımlanmış ve margin/padding içermezler (boşluklar fragment içindeki satırların margin'ları ile kontrol edilir).

---

## Görsel Tasarım Standartları

### Ortak Stil: `SettingsRowStyle`

**Tanım Yeri**: `app/src/main/res/values/styles.xml`

#### Layout Özellikleri
- **Width**: `match_parent`
- **Height**: `wrap_content`
- **MinHeight**: `60dp` (minimum yükseklik garantisi)
- **Margin Bottom**: `6dp` (satırlar arası boşluk)
- **Orientation**: `horizontal` (satır içi öğeler yatay)

#### Padding Değerleri
- **Start (Sol)**: `24dp`
- **End (Sağ)**: `24dp`
- **Top (Üst)**: `5dp`
- **Bottom (Alt)**: `5dp`

#### Görsel Özellikler
- **Background**: `@drawable/second_focus_selector`
  - Normal: `button_normal_background` (hafif şeffaf beyaz #1AFFFFFF, corner radius 8dp)
  - Focus: `button_resume_focus_background` (şeffaf beyaz + 2dp altın sarı border)
- **Gravity**: `center_vertical` (içerik dikeyde ortalanmış)

#### Etkileşim Özellikleri
- **Focusable**: `true`
- **Clickable**: `true`
- **FocusableInTouchMode**: `true` (TV remote kontrolü için)

### Metin Stili: `SettingsRowTextStyle`

**Tanım Yeri**: `app/src/main/res/values/styles.xml`

- **Text Color**: `#FFFFFF` (Beyaz)
- **Text Size**: `18sp`
- **Text Style**: `bold`

---

## Sayfa İçeriği Detayları

### 1. Account Settings (Hesap Ayarları)

**Fragment**: `AccountSettingsFragment`
**Layout**: `fragment_account_settings.xml`

#### Satırlar:
1. **Kullanıcı Durumu** (`btn_user_status`)
   - Sol: "Kullanıcı Durumu" metni
   - Sağ (opsiyonel): "(Premium)" metni (premium olmayan kullanıcılarda gizli, italic, 18sp)
   - Özellik: Premium kullanıcılar tıklayabilir, diğerleri pasif
   - İşlev: Kullanıcı bilgileri dialog'unu açar

---

### 2. General Settings (Genel Ayarlar)

**Fragment**: `GeneralSettingsFragment`
**Layout**: `fragment_general_settings.xml`

#### Satırlar:
1. **Uygulama Dili** (`btn_app_language`)
   - Tek metin: "Uygulama Dili"
   - İşlev: Dil seçim dialogunu açar

2. **Önbelleği Temizle** (`btn_clear_cache`)
   - Tek metin: "Önbelleği Temizle"
   - İşlev: Onay dialogu gösterir, ardından önbelleği temizler

3. **Uygulama Hakkında** (`btn_about`)
   - Tek metin: "Uygulama Hakkında"
   - İşlev: AboutActivity'yi açar

---

### 3. Premium Settings (Premium Ayarlar)

**Fragment**: `PremiumSettingsFragment`
**Layout**: `fragment_premium_settings.xml`

#### Satırlar:
1. **İzleyiciler** (`btn_viewers`)
   - Sol: "İzleyiciler" metni
   - Sağ (opsiyonel): "(Premium)" metni (premium olmayan kullanıcılarda görünür, italic, 18sp)
   - İşlev: ViewersActivity'yi açar

2. **Premium Satın Al / Geri Yükle** (`btn_premium`)
   - Sol: "Premium Satın Al / Geri Yükle" metni (weight=1 ile genişletilmiş)
   - Sağ: Durum metni ("Açık" veya "Kapalı")
   - İşlev: Premium satın alma/iade işlemlerini başlatır

3. **Yetişkin İçerikler** (`btn_adult_content`)
   - Sol: "Yetişkin İçerikler" metni + (opsiyonel) "(Premium)" metni
   - Sağ: Durum metni ("Açık" veya "Kapalı")
   - Ortada: Esnek spacer (View) - sağdaki metni sağa hizalar
   - İşlev: Yetişkin içerik ayarını açar/kapatır

---

## Boşluk ve Hizalama Yönetimi

### Satırlar Arası Boşluk
- **Tüm satırlar arası**: `6dp` (`SettingsRowStyle` → `marginBottom`)
- Bu değer hem fragment içindeki satırlar arası hem de fragment'lar arası boşluğu kontrol eder (son satırların marginBottom'u sayesinde)

### Fragment Container Boşlukları
- Fragment container'ları margin/padding içermez
- Boşluklar tamamen satırların `marginBottom` değeri ile kontrol edilir

### Sayfa Kenar Boşlukları
- **Üst**: 32dp (LinearLayout marginTop - navbar'dan boşluk)
- **Yanlar**: 32dp (LinearLayout paddingStart/End)
- **Alt**: 32dp (LinearLayout paddingBottom)

---

## Background Drawable Detayları

### Normal Durum (`button_normal_background.xml`)
- **Shape**: Rectangle
- **Corner Radius**: 8dp
- **Arka Plan Rengi**: #1AFFFFFF (hafif şeffaf beyaz)
- **Stroke**: Yok

### Focus Durumu (`button_resume_focus_background.xml`)
- **Shape**: Layer-list (iki katman)
- **Corner Radius**: 8dp
- **Arka Plan Rengi**: #1AFFFFFF (hafif şeffaf beyaz)
- **Stroke**: 
  - Genişlik: 2dp
  - Renk: #D4AF37 (koyu sarı/altın - category_selected)

---

## Özel Özellikler

### 1. Scroll Özelliği
- İçerik uzunsa dikey kaydırma yapılabilir (`NestedScrollView`)

### 2. TV Optimizasyonu
- Tüm butonlar TV remote kontrolüne uyumlu
- Focus yönetimi ile görsel geri bildirim sağlanır

### 3. Premium Entegrasyonu
- Premium özellikler dinamik olarak gösterilir/gizlenir
- Premium metinleri ve durumları runtime'da güncellenir

### 4. Debug Modu
- Debug build'lerde "Crashlytics Debug" butonu görünür (normalde gizli)
- Buton `SettingsRowStyle` kullanır (standart görünüm)

### 5. Banner Reklam
- Sayfanın en altında gösterilir
- Premium kullanıcılar için otomatik olarak gizlenir

---

## Standartlaştırma Özellikleri

### Stil Tabanlı Tasarım
Tüm satırlar `SettingsRowStyle` kullanarak:
- ✅ Tutarlı görsel görünüm
- ✅ Merkezi stil yönetimi
- ✅ Tek noktadan değişiklik imkanı
- ✅ Milimetrik hizalama garantisi

### Standart Ölçüler
- **Satırlar arası boşluk**: 6dp (tüm satırlarda aynı)
- **Dikey padding**: 5dp (üst/alt)
- **Yatay padding**: 24dp (sol/sağ)
- **Minimum yükseklik**: 60dp
- **Corner radius**: 8dp (background drawable)

---

## Dosya Listesi

### Layout Dosyaları
- `activity_settings.xml` - Ana activity layout'u
- `fragment_account_settings.xml` - Hesap ayarları fragment'ı
- `fragment_general_settings.xml` - Genel ayarlar fragment'ı
- `fragment_premium_settings.xml` - Premium ayarlar fragment'ı

### Stil Dosyaları
- `styles.xml` - SettingsRowStyle ve SettingsRowTextStyle tanımları

### Drawable Dosyaları
- `second_focus_selector.xml` - Buton focus selector'ı
- `button_normal_background.xml` - Normal durum background'u
- `button_resume_focus_background.xml` - Focus durum background'u

### Kod Dosyaları
- `SettingsActivity.kt` - Ana activity sınıfı
- `AccountSettingsFragment.kt` - Hesap ayarları fragment sınıfı
- `GeneralSettingsFragment.kt` - Genel ayarlar fragment sınıfı
- `PremiumSettingsFragment.kt` - Premium ayarlar fragment sınıfı

---

## Son Güncellemeler

### Yapılan İyileştirmeler
1. ✅ Tüm satırlar için ortak stil (`SettingsRowStyle`) oluşturuldu
2. ✅ Satırlar arası boşluklar standardize edildi (6dp)
3. ✅ Dikey padding değerleri optimize edildi (5dp)
4. ✅ Fragment container margin'ları kaldırıldı (tutarlılık için)
5. ✅ Tüm TextView'lar `SettingsRowTextStyle` kullanıyor
6. ✅ Crashlytics Debug butonu standart stile geçirildi

### Güncel Değerler (2025)
- **Margin Bottom**: 6dp
- **Padding Top/Bottom**: 5dp
- **Padding Start/End**: 24dp
- **MinHeight**: 60dp
- **Corner Radius**: 8dp

---

## Notlar

- Sayfa Android TV için optimize edilmiştir
- Tüm ölçüler dp (density-independent pixels) birimindedir
- Görsel tutarlılık `SettingsRowStyle` ile sağlanır
- Fragment'lar arası geçişler sorunsuz çalışır
- Premium özellikler runtime'da kontrol edilir
