# PNR TV Optimizasyon ve Düzeltme Görevleri

Bu proje için `FILM_DIZI_YUKLEME_ANALIZI.md` dosyasında belirtilen performans sorunlarını ve "yanıp sönme" (flickering) problemlerini gidermek için aşağıdaki değişiklikleri yap.

## 1. ContentBrowseFragment.kt (Flickering Önleme)
**Sorun:** `onResume` içinde koşulsuz çağrılan `refreshCategoriesOnly()`, detay sayfasından her geri dönüşte API/DB trafiği oluşturuyor ve UI'ı titretiyor.
**Görev:**
- `refreshCategoriesOnly()` çağrısını bir zaman aşımına (throttle) veya koşula bağla.
- Bir `lastRefreshTime` değişkeni tut.
- Eğer son yenilemeden bu yana yeterli süre (örn. 5 dakika) geçmediyse `onResume` içinde yenileme yapma.
- Detay sayfasından geri dönüldüğünde listenin gereksiz yere yenilenmesini engelle.

## 2. BaseContentViewModel.kt (Performans)
**Sorun:** `buildCategories()` metodu, sadece kategori içindeki eleman sayılarını (count) hesaplamak için `getAllContent()` ile tüm veritabanını çekiyor. Binlerce içerik olduğunda bu işlem UI thread'i veya IO thread'i gereksiz meşgul ediyor.
**Görev:**
- `buildCategories` içindeki `combine` yapısını optimize et.
- Mümkünse `getAllContent()` (tüm entity verisi) yerine, sadece kategori ID'lerini içeren daha hafif bir veri akışı kullanmayı veya repository tarafında count hesabı yapmayı değerlendir.

## 3. MovieViewModel.kt & SeriesViewModel.kt (Akış Yönetimi)
**Sorun:** Kategori filtrelemesi yapılırken `flatMapLatest` kullanılıyor. Kullanıcı kategori listesinde gezerken her değişimde önceki sorgu iptal ediliyor ve yeni veri gelene kadar liste boş kalabiliyor.
**Görev:**
- `moviesWithSearch` ve `seriesWithSearch` akışlarını incele.
- Kategori geçişlerinde listenin anlık olarak boşalmasını (flickering) önlemek için akış stratejisini iyileştir (örn. önceki veriyi tutma veya yükleme durumunu daha iyi yönetme).

Bu değişiklikleri yaparken mevcut kod stiline sadık kal ve Timber loglarını koru.