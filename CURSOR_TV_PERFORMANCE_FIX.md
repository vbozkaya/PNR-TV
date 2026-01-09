# Android TV Performans Optimizasyonu ve Flickering Giderme

Önceki optimizasyonlar yapıldı ancak düşük işlemcili **Gerçek Android TV cihazlarında** hala kategori geçişlerinde ve ilk yüklemede "yanıp sönme" (flickering) sorunu devam ediyor. Emülatörde sorun yok, bu durum TV donanımının render darboğazına işaret ediyor.

Aşağıdaki **TV Odaklı Performans İyileştirmelerini** uygula:

## 1. Adapter Optimizasyonu (Stable IDs)
**Sorun:** `DiffUtil` hesaplaması sonrası RecyclerView tüm listeyi yeniden çiziyor olabilir. TV işlemcisi buna yetişemediği için titreme oluyor.
**Görev:**
- `ContentAdapter` ve `CategoryAdapter` sınıflarını güncelle.
- Constructor içinde `setHasStableIds(true)` çağrısını ekle.
- `getItemId(position: Int): Long` metodunu override et ve her öğe için benzersiz, değişmeyen bir ID döndür (Örn: `movie.id` veya `series.id`. `position` döndürme!).
- Bu sayede RecyclerView sadece değişen satırları güncelleyecek, titreme azalacak.

## 2. Focus ve State Koruması
**Sorun:** Veri yenilendiğinde RecyclerView focus'u kaybediyor veya en başa dönüyor, bu da görsel bir sıçrama (jump/flicker) yaratıyor.
**Görev:**
- `ContentBrowseFragment` içinde (veya Base sınıfta) RecyclerView adaptörüne şu ayarı ekle/güncelle:
  ```kotlin
  adapter.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
  ```
- Bu ayar, veri tam yüklenmeden RecyclerView'ın pozisyon restore etmeye çalışmasını (ve focus'un saçmalamasını) engeller.

## 3. Resim Yükleme Optimizasyonu (Coil/Glide)
**Sorun:** TV'lerde resim yükleme ve işleme (decode) ana thread'i yorabilir.
**Görev:**
- `ContentAdapter` içindeki resim yükleme kodunu (Coil veya Glide) kontrol et.
- `crossfade(true)` varsa, düşük donanımlı cihazlar için bunu kapatmayı veya süresini düşürmeyi değerlendir. TV'lerde anlık resim değişimi, animasyonlu değişimden daha performanslı olabilir.
- Resim boyutlarını (resize) `ViewHolder` boyutuna tam uyacak şekilde ayarla (oversized bitmap yüklemesini engelle).

## 4. Layout İyileştirmeleri (Kontrol)
**Görev:**
- `item_movie_card.xml` veya `item_category.xml` gibi sık kullanılan layoutları incele.
- Gereksiz `LinearLayout` veya `RelativeLayout` iç içe geçmelerini kaldır. Mümkünse tek katmanlı `ConstraintLayout` veya `FrameLayout` kullan.
- `overdraw` (üst üste çizim) sorunlarını azaltmak için gereksiz `background` atamalarını temizle.

Bu adımlar, kodun mantığını değiştirmeden sadece **Render ve UI Performansını** artırmaya yöneliktir. Mevcut iş akışlarını bozmadan uygula.