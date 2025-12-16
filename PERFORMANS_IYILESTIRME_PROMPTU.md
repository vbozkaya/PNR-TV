# PNR TV Uygulaması Performans İyileştirme Raporu ve Eylem Planı

## 1. Genel Durum

PNR TV uygulaması, Android emülatörlerde sorunsuz çalışmasına rağmen, gerçek TV cihazlarında ciddi performans sorunları yaşamaktadır. Bu sorunlar arasında yayınların geç açılması veya hiç açılmaması, içeriklerin (kanal listeleri, filmler vb.) düzensiz güncellenmesi ve genel arayüz donmaları bulunmaktadır.

Yapılan detaylı kod analizi sonucunda, bu sorunların temel kaynağının, TV'lerin kısıtlı donanım kaynaklarının (CPU, ağ, disk I/O) verimsiz kullanılmasından kaynaklandığı tespit edilmiştir. Emülatörler, modern bilgisayarların güçlü kaynaklarını kullandığı için bu verimsizlikler fark edilmemektedir.

Bu doküman, sorunları ve bu sorunları çözmek için gereken teknik adımları detaylı bir şekilde açıklamaktadır.

## 2. Tespit Edilen Kritik Sorunlar ve Çözüm Adımları

Aşağıdaki eylem planı, sorunları öncelik sırasına göre ele almaktadır.

### Eylem 1: (En Yüksek Öncelik) Tüm Kanal İkonlarını Başlangıçta Yükleme Sorununu Çözme

**Sorunun Kaynağı:**
Uygulama, `LiveStreamRepository.kt` dosyasındaki `preloadAllLiveStreamIcons` fonksiyonu aracılığıyla, mevcut **tüm kanalların ikonlarını** tek bir seferde indirmeye çalışmaktadır. Yüzlerce kanalın olduğu bir listede bu durum, yüzlerce ağ isteğinin aynı anda başlatılmasına neden olur. Bu "istek fırtınası", TV'nin ağ ve işlemci kaynaklarını anında tüketerek aşağıdaki kritik sorunlara yol açar:

1.  **Yayınların Başlatılamaması:** Asıl yayın akışını başlatması gereken ağ isteği, yüzlerce ikon isteği arasında kaybolur, zaman aşımına uğrar veya başarısız olur.
2.  **Genel Performans Düşüşü:** Cihaz, bu yoğun ağ trafiğini yönetmeye çalışırken uygulama genelinde donmalar ve takılmalar yaşanır.

**Çözüm Adımları:**

1.  **Fonksiyonu ve Çağrılarını Tamamen Kaldırın:**
    *   **Dosya:** `app/src/main/java/com/pnr/tv/repository/LiveStreamRepository.kt`
    *   **Yapılacak İşlem:** `preloadAllLiveStreamIcons` fonksiyonunu tamamen silin.
    *   **Yapılacak İşlem:** Bu fonksiyonun projenin başka yerlerinden (örneğin, bir ViewModel içerisinden) yapılan tüm çağrılarını kaldırın.

2.  **"Lazy Loading" (Tembel Yükleme) Prensibini Uygulayın:**
    *   **Prensip:** Bir resim, sadece kullanıcı ekranında görünür hale geldiğinde yüklenmelidir. Modern resim yükleme kütüphaneleri (`Coil`, `Glide`, `Picasso`) bu davranışı varsayılan olarak zaten sergilemektedir.
    *   **Doğrulama:** `CardPresenter.kt`, `GridListRowPresenter.kt` veya `RecyclerView.Adapter` gibi liste elemanlarını ekrana çizen sınıflarda, resimlerin `ViewHolder` içerisinde `Coil` kullanılarak yüklendiğini doğrulayın. Standart `.load(url).into(imageView)` kullanımı, tembel yüklemeyi otomatik olarak gerçekleştirecektir. Önceden yükleme (preloading) yapmaya gerek yoktur.

---

### Eylem 2: Veritabanı Güncelleme Stratejisini Optimize Etme

**Sorunun Kaynağı:**
`LiveStreamRepository.kt` içerisindeki `refreshLiveStreams` fonksiyonu, verileri güncellerken `liveStreamDao.replaceAll(entities)` metodunu kullanmaktadır. Bu metod, veritabanındaki **tüm mevcut kayıtları silip, tüm yeni kayıtları yeniden eklemektedir.** Büyük veri setlerinde bu "tümünü sil ve yeniden ekle" yaklaşımı, TV'lerin yavaş depolama birimlerinde gereksiz bir yük oluşturur ve arayüzde anlık donmalara neden olur.

**Çözüm Adımları:**

1.  **Akıllı Güncelleme (Upsert) Mantığı Geliştirin:**
    *   **Dosya:** `app/src/main/java/com/pnr/tv/db/dao/LiveStreamDao.kt`
    *   **Yapılacak İşlem:** `replaceAll` metodunu, sadece değişiklikleri işleyen daha verimli bir metod ile değiştirin. Bu yeni metod, bir `@Transaction` anotasyonu altında çalışmalıdır.
    *   **Örnek Mantık:**
        ```kotlin
        @Transaction
        suspend fun upsert(newStreams: List<LiveStreamEntity>) {
            val oldStreams = getAll().first() // Mevcut tüm yayınları al
            val oldStreamMap = oldStreams.associateBy { it.streamId }
            val newStreamMap = newStreams.associateBy { it.streamId }

            // Silinecekler: Eskide olup yeni de olmayanlar
            val toDelete = oldStreams.filter { it.streamId !in newStreamMap }
            if (toDelete.isNotEmpty()) {
                deleteStreams(toDelete) // @Delete alan bir metod
            }

            // Eklenecek ve güncellenecekler (Upsert)
            // Room'un @Insert(onConflict = OnConflictStrategy.REPLACE) stratejisi
            // bu işi tek seferde verimli bir şekilde yapar.
            insertOrUpdateStreams(newStreams) // @Insert(onConflict = OnConflictStrategy.REPLACE) kullanan bir metod
        }
        ```
    *   Yukarıdaki mantığa uygun olarak `LiveStreamDao` arayüzüne yeni fonksiyonlar ekleyin (`@Insert(onConflict = OnConflictStrategy.REPLACE)`, `@Delete`).

2.  **Repository'de Yeni Metodu Kullanın:**
    *   **Dosya:** `app/src/main/java/com/pnr/tv/repository/LiveStreamRepository.kt`
    *   **Yapılacak İşlem:** `refreshLiveStreams` fonksiyonu içindeki `liveStreamDao.replaceAll(entities)` çağrısını, yukarıda oluşturduğunuz yeni `upsert` metodu ile değiştirin: `liveStreamDao.upsert(entities)`.

---

### Eylem 3: Ağ Trafiği Güvenliğini ve Uyumluluğunu Artırma

**Sorunun Kaynağı:**
`network_security_config.xml` dosyasındaki `<base-config cleartextTrafficPermitted="true">` ayarı, uygulamanın şifrelenmemiş (HTTP) trafiğe izin vermesine neden olmaktadır. Gerçek cihaz ağları veya internet servis sağlayıcıları, güvenlik gerekçesiyle bu tür trafiği engelleyebilir veya yavaşlatabilir. Bu durum, içeriğin emülatörde çalışırken gerçek TV'de neden "bazen çalışıp bazen çalışmadığını" açıklayan faktörlerden biridir.

**Çözüm Adımları:**

1.  **Varsayılan Olarak HTTPS'yi Zorunlu Kılın:**
    *   **Dosya:** `app/src/main/res/xml/network_security_config.xml`
    *   **Yapılacak İşlem:** `<base-config cleartextTrafficPermitted="true">` satırını `<base-config cleartextTrafficPermitted="false">` olarak değiştirin.

2.  **Gerekli Durumlar İçin İstisnalar Tanımlayın:**
    *   Eğer bazı IPTV yayın URL'leri gibi servisler kesinlikle HTTPS desteklemiyorsa ve sadece HTTP üzerinden çalışıyorsa, bu spesifik adresler için istisna tanımlayın.
    *   **Yapılacak İşlem:** Sadece HTTP gerektiren alan adları veya IP adresleri için `<domain-config>` blokları ekleyin.
    *   **Örnek:**
        ```xml
        <network-security-config>
            <!-- Varsayılan olarak HTTPS zorunlu -->
            <base-config cleartextTrafficPermitted="false">
                <trust-anchors>
                    <certificates src="system" />
                </trust-anchors>
            </base-config>

            <!-- Sadece bu IP adresi için HTTP'ye izin ver -->
            <domain-config cleartextTrafficPermitted="true">
                <domain includeSubdomains="false">123.123.123.123</domain>
            </domain-config>
        </network-security-config>
        ```

Bu değişiklik, uygulamanızı daha güvenli hale getirirken, ağ kısıtlamaları olan gerçek cihazlarda daha tutarlı çalışmasını sağlayacaktır.

---

Bu adımlar tamamlandığında, uygulamanın gerçek TV cihazlarındaki performansı, tepkiselliği ve güvenilirliği belirgin bir şekilde artacaktır.
