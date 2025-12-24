# Cursor AI Task: RecyclerView Crash Düzeltmesi (SelectViewerDialog.kt)

**Sorun:**
Uygulamada `java.lang.IllegalStateException: Cannot call this method while RecyclerView is computing a layout or scrolling` hatası alınıyor.
Stack trace'e göre hata `com.pnr.tv.ui.viewers.SelectViewerAdapter.updateSelectedViewer` metodunda yapılan `notifyItemChanged` çağrısından kaynaklanıyor. Bu çağrı, focus değişimi sırasında (`onFocusChange`) tetiklendiği için RecyclerView'ın layout işlemiyle çakışıyor.

**İlgili Dosya:**
`app/src/main/java/com/pnr/tv/ui/viewers/SelectViewerDialog.kt`

**Yapılacak Düzeltme:**

1.  `SelectViewerDialog.kt` dosyasını aç.
2.  `SelectViewerAdapter` sınıfını ve içindeki `updateSelectedViewer` metodunu bul.
3.  `updateSelectedViewer` metodu içindeki `notifyItemChanged` çağrısını (veya veri güncelleme işlemini) bir `Handler` veya `View.post` bloğu içine alarak Main Thread kuyruğuna at.

**Örnek Kod Değişikliği:**

*Mevcut Hatalı Kod (Tahmini):*
```kotlin
fun updateSelectedViewer(newPosition: Int) {
    // ... veri güncelleme mantığı ...
    notifyItemChanged(oldPosition)
    notifyItemChanged(newPosition)
}
```

*Düzeltilmiş Kod:*
```kotlin
fun updateSelectedViewer(newPosition: Int) {
    // ... veri güncelleme mantığı ...
    
    // RecyclerView'a bağlı bir view üzerinden veya Handler ile post et
    // Eğer bir view referansı yoksa Handler(Looper.getMainLooper()).post kullanabilirsin
    // Ancak ViewHolder veya RecyclerView referansı varsa:
    
    recyclerView?.post { 
        notifyItemChanged(oldPosition)
        notifyItemChanged(newPosition)
    }
    
    // VEYA (daha garanti yöntem, view referansı gerekmez):
    android.os.Handler(android.os.Looper.getMainLooper()).post {
        notifyItemChanged(oldPosition)
        notifyItemChanged(newPosition)
    }
}
```

**Hedef:** `notifyItemChanged` çağrısının, RecyclerView'ın o anki layout/scroll işlemi bittikten sonra çalışmasını sağlayarak `IllegalStateException` hatasını önlemek. Lütfen bu mantığı `updateSelectedViewer` fonksiyonuna uygula.