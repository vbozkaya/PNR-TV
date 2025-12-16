### **Prompt Başlangıcı**

**Hedef:** Android TV projemde, özellikle düşük donanımlı cihazlarda listeler ve kategoriler arasında gezinirken yaşanan takılma (jank) sorununu çözmek. Sorun, emülatörde değil, yalnızca gerçek TV cihazlarında gözlemleniyor. Analizler, sorunun temel olarak resimlerin verimsiz yüklenmesi ve liste satırlarının verimsiz oluşturulmasından kaynaklandığını gösteriyor.

Aşağıda sana iki dosyanın mevcut içeriğini ve bu dosyalarda yapman gereken değişiklikleri adım adım, gerekçeleriyle birlikte sunuyorum.

---

### **Dosya 1: `app/src/main/java/com/pnr/tv/ui/browse/CardPresenter.kt`**

Bu dosya, listelerdeki her bir öğenin (film afişi, kanal logosu vb.) nasıl görüneceğini ve yükleneceğini kontrol ediyor. Mevcut hali performans sorunları içeriyor.

**Mevcut Kod:**

'''kotlin
package com.pnr.tv.ui.browse

import android.view.ViewGroup
import androidx.leanback.widget.Presenter
import coil.load
import coil.size.Scale
import coil.size.Size
import com.pnr.tv.Constants
import com.pnr.tv.R
import com.pnr.tv.model.ContentItem

class CardPresenter : Presenter() {
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val cardView = CustomImageCardView(parent.context)
        val screenWidth = parent.context.resources.displayMetrics.widthPixels
        val cardWidth = (screenWidth / Constants.CARD_WIDTH_DIVISOR).toInt()
        val layoutParams =
            ViewGroup.LayoutParams(
                cardWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        cardView.layoutParams = layoutParams
        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(
        viewHolder: ViewHolder,
        item: Any?,
    ) {
        val cardView = viewHolder.view as CustomImageCardView
        val contentItem = item as? ContentItem

        if (contentItem == null) {
            cardView.titleText = ""
            cardView.contentText = ""
            cardView.mainImageView?.load(R.drawable.placeholder_image) {
                scale(Scale.FIT)
            }
            return
        }

        cardView.titleText = contentItem.title
        cardView.contentText = ""

        val imageUrl = contentItem.imageUrl
        if (!imageUrl.isNullOrBlank()) {
            cardView.mainImageView?.load(imageUrl) {
                placeholder(R.drawable.placeholder_image)
                error(R.drawable.placeholder_image)
                crossfade(true)
                scale(Scale.FILL)
                size(Size(1280, 720)) // <- PERFORMANS SORUNU 1
                allowHardware(false) // <- PERFORMANS SORUNU 2
                allowRgb565(true)
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
'''

**`CardPresenter.kt` İçin Yapılacak Değişiklikler:**

1.  **Donanım Bitmap'lerini Etkinleştir:**
    *   **Sorun:** `allowHardware(false)` ayarı, resimlerin GPU belleği yerine CPU belleğinde (RAM) işlenmesine neden oluyor. Bu, resimlerin ekrana çizilmesi sırasında sürekli olarak bellek transferi gerektirir ve özellikle TV gibi sınırlı kaynaklara sahip cihazlarda ciddi takılmalara yol açar.
    *   **Çözüm:** Bu satırı `allowHardware(true)` olarak değiştir. Bu, resimlerin doğrudan grafik işlemcisi (GPU) tarafından yönetilmesini sağlar, bu da çizim işlemlerini büyük ölçüde hızlandırır ve takılmaları önler. Bu, en önemli değişikliktir.

2.  **Resim Boyutunu Dinamik ve Verimli Hale Getir:**
    *   **Sorun:** `size(Size(1280, 720))` komutu, ekranda çok daha küçük görünecek bir kart için bile hafızaya 1280x720 boyutunda bir resmin yüklenmesine neden olur. Bu, gereksiz bellek tüketimine ve GPU üzerinde fazladan ölçeklendirme yüküne yol açar.
    *   **Çözüm:** Sabit boyut yerine, `onCreateViewHolder` içinde hesaplanan `cardWidth` değerini kullanarak resim isteği yap. Yükseklik için ise kartların en-boy oranını (genellikle 16:9 veya 2:3) koruyacak bir hesaplama yap. Örneğin, 16:9 oranı için yükseklik `(cardWidth * 9) / 16` olabilir. Bu, hem bellek kullanımını optimize eder hem de işlemci yükünü azaltır.

**İstenen Sonuç Kod (`CardPresenter.kt`):**

'''kotlin
package com.pnr.tv.ui.browse

import android.view.ViewGroup
import androidx.leanback.widget.Presenter
import coil.load
import coil.size.Scale
import coil.size.Size
import com.pnr.tv.Constants
import com.pnr.tv.R
import com.pnr.tv.model.ContentItem
import kotlin.math.roundToInt

class CardPresenter : Presenter() {
    
    private var cardWidth: Int = 0
    private var cardHeight: Int = 0

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val cardView = CustomImageCardView(parent.context)

        // Kart boyutunu sadece bir kez hesapla
        if (cardWidth == 0) {
            val screenWidth = parent.context.resources.displayMetrics.widthPixels
            cardWidth = (screenWidth / Constants.CARD_WIDTH_DIVISOR).toInt()
            // 16:9 en-boy oranı varsayımıyla yüksekliği hesapla
            cardHeight = (cardWidth * 9.0 / 16.0).roundToInt()
        }

        val layoutParams = ViewGroup.LayoutParams(cardWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
        cardView.layoutParams = layoutParams

        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(
        viewHolder: ViewHolder,
        item: Any?,
    ) {
        val cardView = viewHolder.view as CustomImageCardView
        val contentItem = item as? ContentItem

        if (contentItem == null) {
            cardView.titleText = ""
            cardView.contentText = ""
            cardView.mainImageView?.load(R.drawable.placeholder_image) {
                scale(Scale.FIT)
            }
            return
        }

        cardView.titleText = contentItem.title
        cardView.contentText = ""

        val imageUrl = contentItem.imageUrl
        if (!imageUrl.isNullOrBlank()) {
            cardView.mainImageView?.load(imageUrl) {
                placeholder(R.drawable.placeholder_image)
                error(R.drawable.placeholder_image)
                crossfade(true)
                scale(Scale.FILL)
                // DEĞİŞİKLİK 1: Dinamik ve verimli boyut kullan
                size(Size(cardWidth, cardHeight))
                // DEĞİŞİKLİK 2: Donanım hızlandırmayı etkinleştir
                allowHardware(true) 
                // Bu ayar bellek için iyi, kalsın
                allowRgb565(true)
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
'''

---

### **Dosya 2: `app/src/main/java/com/pnr/tv/ui/livestreams/GridListRowPresenter.kt`**

Bu dosya, satırların nasıl düzenleneceğini belirler. Mevcut hali, her satır için verimsiz bir şekilde view araması yapıyor.

**Mevcut Kod:**

'''kotlin
package com.pnr.tv.ui.livestreams

import android.view.View
import android.view.ViewGroup
import androidx.leanback.widget.HorizontalGridView
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.RowPresenter

class GridListRowPresenter : ListRowPresenter() {
    init {
        shadowEnabled = true
    }

    override fun createRowViewHolder(parent: ViewGroup): RowPresenter.ViewHolder {
        val viewHolder = super.createRowViewHolder(parent)
        viewHolder.view.post {
            val gridView = findGridView(viewHolder.view)
            if (gridView != null) {
                gridView.setPadding(0, 0, 0, 0)
            }
        }
        return viewHolder
    }

    // <- PERFORMANS SORUNU 3
    private fun findGridView(view: View?): HorizontalGridView? {
        if (view == null) return null
        
        val gridView = view.findViewById<HorizontalGridView>(androidx.leanback.R.id.row_content)
        if (gridView != null) {
            return gridView
        }
        
        if (view is HorizontalGridView) return view

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                val result = findGridView(child)
                if (result != null) return result
            }
        }
        return null
    }
}
'''

**`GridListRowPresenter.kt` İçin Yapılacak Değişiklikler:**

1.  **Özyineli (Recursive) View Aramasını Kaldır:**
    *   **Sorun:** `findGridView` metodu, `HorizontalGridView`'ı bulmak için önce standart ID ile deneme yapıyor, bulamazsa tüm view hiyerarşisini özyineli olarak tarıyor. Bu tarama işlemi, her satır oluşturulduğunda çalışır ve view ağacı ne kadar karmaşıksa o kadar yavaşlar. Bu, gereksiz bir performans maliyetidir.
    *   **Çözüm:** Leanback kütüphanesinin `ListRow`'u için `HorizontalGridView`'ın ID'si her zaman `androidx.leanback.R.id.row_content`'dir. Bu nedenle, `findViewById` ile yapılan direkt erişim her zaman çalışmalıdır. Özyineli fallback (yedek) kodunu tamamen kaldırarak bu fonksiyonu basitleştir ve daha performanslı hale getir.

**İstenen Sonuç Kod (`GridListRowPresenter.kt`):**

'''kotlin
package com.pnr.tv.ui.livestreams

import android.view.View
import android.view.ViewGroup
import androidx.leanback.widget.HorizontalGridView
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.RowPresenter

class GridListRowPresenter : ListRowPresenter() {
    init {
        shadowEnabled = true
    }

    override fun createRowViewHolder(parent: ViewGroup): RowPresenter.ViewHolder {
        val viewHolder = super.createRowViewHolder(parent)

        // DEĞİŞİKLİK: findViewById direkt olarak burada ve daha güvenli bir şekilde çağrılabilir.
        val gridView = viewHolder.view.findViewById<HorizontalGridView>(androidx.leanback.R.id.row_content)
        
        // view.post'a gerek kalmadan doğrudan ayarlanabilir, çünkü view holder oluşturuldu.
        if (gridView != null) {
            gridView.setPadding(0, 0, 0, 0)
        }
        
        return viewHolder
    }

    // DEĞİŞİKLİK 3: Verimsiz özyineli arama metodu kaldırıldı.
    // Artık bu metoda ihtiyaç yok.
}
'''

---

**Özet:** Bu değişiklikler, uygulamanın grafik ve bellek kaynaklarını çok daha verimli kullanmasını sağlayacak. Sonuç olarak, özellikle donanımı zayıf olan Android TV cihazlarında bile listeler arasında gezinirken akıcı bir kullanıcı deneyimi elde edilmelidir.

### **Prompt Sonu**