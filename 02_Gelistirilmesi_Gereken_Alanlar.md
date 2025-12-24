# 2. Geliştirilmesi Gereken Alanlar ve Eksiklikler

Mükemmelliğe giden yolda aşağıdaki alanlarda iyileştirmeler yapılabilir:

### 1. Bağımlılık Yönetiminin Modernizasyonu (Version Catalogs)
*   **Durum:** Şu anda `build.gradle` dosyalarında bağımlılıklar ve versiyon numaraları hardcoded (sabit) olarak tanımlanmış veya dağınık durumda olabilir.
*   **Neden Önemli?** Proje büyüdükçe versiyonların takibi zorlaşır. Gradle'ın modern özelliği **Version Catalogs (`libs.versions.toml`)** kullanımı, tüm bağımlılıkların tek bir dosyadan merkezi olarak yönetilmesini, versiyon tutarlılığını ve güncellemelerin kolaylaşmasını sağlar.

### 2. UI/View Katmanının Modernizasyonu (Jetpack Compose for TV)
*   **Durum:** Projede şu anda XML tabanlı layoutlar ve Leanback kütüphanesi yoğun olarak kullanılıyor.
*   **Neden Önemli?** Google, Android geliştirmede **Jetpack Compose**'u standart olarak belirledi. Android TV için de "Compose for TV" kararlı sürüme ulaştı. Yeni ekranların Compose ile yazılması veya mevcutların zamanla dönüştürülmesi, UI geliştirme hızını artıracak ve daha modern, dinamik arayüzler sağlayacaktır. XML ve ViewBinding kullanımı artık "legacy" (eski) olarak kabul edilmeye başlandı.

### 3. Test Kapsamının Artırılması
*   **Durum:** Test altyapısı (JUnit, Mockito, Hilt Testing) kurulu olsa da, testlerin kapsamı ve çeşitliliği artırılabilir. Özellikle UI testleri ve entegrasyon testleri genellikle eksik kalabiliyor.
*   **Neden Önemli?** Kod tabanı büyüdükçe manuel testler sürdürülemez hale gelir. Özellikle kritik iş mantığı (ViewModel'ler, UseCase'ler) ve Repository katmanı için birim testlerinin (Unit Tests) %80 üzerinde kapsama sahip olması hedeflenmelidir.

### 4. Statik Kod Analizi ve Formatlama
*   **Durum:** `ktlint` eklentisi mevcut ancak daha katı kurallar veya CI/CD süreçlerine entegrasyonu (Detekt gibi araçlarla) güçlendirilebilir.
*   **Neden Önemli?** Kod standartlarının otomatik olarak denetlenmesi, ekip içi kod tutarlılığını sağlar ve "code smell" (kötü kod kokusu) oluşumunu engeller.

### 5. Modülarizasyon (İleri Seviye)
*   **Durum:** Proje şu anda `:app` modülü altında "Monolitik" bir yapıda görünüyor.
*   **Neden Önemli?** Gelecekte proje daha da büyürse, derleme sürelerini kısaltmak ve özellik tabanlı geliştirmeyi (feature-based development) kolaylaştırmak için projenin `:data`, `:domain`, `:feature:home`, `:feature:player` gibi modüllere ayrılması düşünülebilir. Şu an için kritik değil ancak uzun vadeli bir hedef olabilir.

### 6. Kaynak Yönetimi ve Lokalizasyon
*   **Durum:** String ve diğer kaynakların kullanımı standart olsa da, hardcoded stringlerin tamamen temizlendiğinden ve çoklu dil desteğinin (eğer hedefleniyorsa) tam olduğundan emin olunmalı.
