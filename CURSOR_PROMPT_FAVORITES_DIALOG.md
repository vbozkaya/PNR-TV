# Cursor AI Task: "Favoriye Ekle" Dialog Tasarım Revizyonu

**Bağlam:**
"Favoriye Ekle" (Select Viewer) dialog penceresi şu an tüm ekranı kaplıyor (`MATCH_PARENT`) ve arka planda yarı saydam bir resim kullanıyor. Bu durum okunaklılığı azaltıyor ve TV ekranında estetik durmuyor. Bu dialogu, "Resume Playback" dialogunda yaptığımız gibi modern, kart tabanlı bir yapıya dönüştüreceğiz.

**İlgili Dosyalar:**
*   `app/src/main/res/layout/dialog_select_viewer.xml` (Ana dialog tasarımı)
*   `app/src/main/res/layout/item_viewer_select.xml` (Liste elemanları)

**Yapılacak Değişiklikler:**

### 1. Dialog Layout (`dialog_select_viewer.xml`)
Lütfen bu dosyayı tamamen revize et:

*   **Ana Kapsayıcı (Root):**
    *   Genişlik: **Sabit Genişlik** (Örn: `500dp`). `MATCH_PARENT` kullanma.
    *   Yükseklik: `WRAP_CONTENT`.
    *   Gravity: `center`.
    *   Arka Plan: **Koyu Solid Renk** (Örn: `#F21A1A1A` - %95 opaklıkta koyu gri/siyah). Resim kullanma.
    *   Köşe Yuvarlaklığı: `16dp` radius (CardView veya ShapeDrawable ile).
    *   Elevation: `8dp`.

*   **Gereksiz Öğeleri Kaldır:**
    *   `dialog_background_image` ID'li `ImageView`'ı **SİL**. Artık arka plan resmine ihtiyacımız yok, düz renk kullanacağız.

*   **İçerik Düzeni (Padding & Margins):**
    *   İçerik padding'i: `24dp` veya `32dp`.
    *   **Başlık (TextView):** Beyaz, Bold, `20sp`, ortalanmış (`gravity="center"`). Altına `16dp` boşluk.
    *   **Mesaj (TextView):** Gri/Beyaz, `16sp`, ortalanmış. Altına `24dp` boşluk.

*   **RecyclerView (`recycler_viewers`):**
    *   Maksimum yükseklik sınırlaması korunsun (veya `wrap_content` + `ConstraintLayout` ile max height constraint ver).

### 2. Liste Elemanları (`item_viewer_select.xml`)
*   Mevcut yapı genel olarak iyi ancak dialog daralacağı için paddingleri kontrol et.
*   Yatay genişlik `match_parent` kalabilir (Card'ın içini doldurması için).
*   Arka plan selector'larının koyu dialog zemininde göründüğünden emin ol (Gerekirse selector renklerini hafif aç veya stroke ekle).

**Kod Yapısı Örneği (Layout XML):**

```xml
<androidx.cardview.widget.CardView
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="500dp" <!-- Sabit Genişlik -->
    android:layout_height="wrap_content"
    app:cardCornerRadius="16dp"
    app:cardBackgroundColor="#F21A1A1A"
    app:cardElevation="8dp">

    <LinearLayout
        android:orientation="vertical"
        android:padding="32dp"
        android:layout_width="match_parent"
        android:layout_height="wrap_content">

        <!-- Başlık -->
        <TextView
            android:id="@+id/dialog_select_viewer_title"
            android:text="Favoriye Ekle"
            android:textSize="22sp"
            android:textStyle="bold"
            android:textColor="#FFFFFF"
            android:gravity="center"
            android:layout_marginBottom="12dp"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"/>

        <!-- Mesaj -->
        <TextView
            android:text="Hangi izleyici için favoriye eklemek istersiniz?"
            android:textSize="16sp"
            android:textColor="#CCFFFFFF"
            android:gravity="center"
            android:layout_marginBottom="24dp"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"/>

        <!-- Liste -->
        <androidx.recyclerview.widget.RecyclerView
            android:id="@+id/recycler_viewers"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="16dp"
            app:layoutManager="androidx.recyclerview.widget.LinearLayoutManager"/>
            
        <!-- NOT: Eğer standart AlertDialog butonları kullanılıyorsa buraya buton eklemeye gerek yok. 
             Ancak özel buton istenirse buraya eklenebilir. -->

    </LinearLayout>
</androidx.cardview.widget.CardView>
```

**Not:** Bu değişiklik sadece XML görselleştirmesini etkilemeli, Java/Kotlin tarafındaki mantığı bozmamalıdır. Sadece `ImageView` referansı kodda varsa ve silinirse hata vermemesi için koddaki ilgili `binding.dialogBackgroundImage` (varsa) satırlarını da temizle veya null check ekle.