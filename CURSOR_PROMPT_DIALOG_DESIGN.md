# Cursor AI Task: "Resume Playback" Dialog Tasarım İyileştirmesi

**Bağlam:**
Uygulamadaki "Kaldığı Yerden Devam Et" (Resume Playback) dialog penceresi şu anki haliyle okunaklı değil ve görsel olarak düzensiz. Arka planın şeffaf olması ve arkada resim kullanılması metinleri okunmaz kılıyor. `MATCH_PARENT` genişlik ise TV ekranında estetik durmuyor.

**Hedef:**
Bu dialogu Modern Android TV (Material Design) standartlarına uygun, şık, okunaklı ve odaklanılabilir (focusable) bir yapıya kavuşturmak.

**Bulunacak Kod:**
Lütfen projede "Kaldığı Yerden Devam Et" veya "Resume Playback" stringlerini aratarak ilgili Dialog oluşturma kodunu veya XML layout dosyasını bul.

**Tasarım Revizyon Maddeleri:**

Lütfen aşağıdaki değişiklikleri ilgili layout/kod bloğuna uygula:

### 1. Dialog Container (Ana Kapsayıcı)
*   **Genişlik:** `MATCH_PARENT` YERİNE sabit bir genişlik kullan (Örn: `500dp` veya `600dp`). TV ekranında çok yayılmasın.
*   **Konum:** `layout_gravity="center"` ile ekranın tam ortasına yerleştir.
*   **Arka Plan:**
    *   Mevcut "resim içeren şeffaf arka planı" **KALDIR**.
    *   Yerine **Koyu Solid Renk** kullan (Örn: `#F21A1A1A` - %95 opaklıkta koyu gri/siyah).
    *   **Köşe Yuvarlaklığı:** `16dp` radius'lu bir `ShapeDrawable` veya `CardView` yapısı kullan.
    *   **Stroke (Çerçeve):** İsteğe bağlı, çok ince (1dp) ve düşük opaklıkta (#33FFFFFF) beyaz bir çerçeve ekle (Glassmorphism etkisi için).

### 2. İçerik Düzeni
*   **Padding:** İçeriği kenarlardan `32dp` uzaklaştır (ferah görünüm).
*   **Başlık (TextView):**
    *   Renk: Tam Beyaz (`#FFFFFF`).
    *   Boyut: `20sp` veya `22sp`.
    *   Stil: `Bold`.
    *   Hizalama: `center`.
    *   Altına butonlarla arasına `24dp` margin ekle.

### 3. Butonlar
*   **Layout:** Yan yana (`horizontal`), ortalanmış (`gravity="center"`).
*   **Buton Stilleri:**
    *   Varsayılan (Normal) durum: Hafif gri arka plan (`#1AFFFFFF`) ile butonun sınırlarını belli et.
    *   **Focus Durumu (ÖNEMLİ):** Mevcut `navyfocus_selector` yapısını koru ancak butonlar odaklandığında hafifçe büyüsün (`scaleX="1.05"`, `scaleY="1.05"` animasyonu veya selector içinde tanımlıysa kalsın).
    *   **Metin:** Beyaz, okunabilir, `14sp` veya `16sp`.

**Kod Yapısı Örneği (XML Mantığı):**

```xml
<androidx.cardview.widget.CardView
    android:layout_width="550dp"  <!-- Sabit Genişlik -->
    android:layout_height="wrap_content"
    app:cardCornerRadius="16dp"
    app:cardBackgroundColor="#F21A1A1A" <!-- Koyu, Okunaklı Arka Plan -->
    app:cardElevation="8dp">

    <LinearLayout
        android:orientation="vertical"
        android:padding="32dp">

        <TextView
            android:text="Kaldığı Yerden Devam Et"
            android:textSize="22sp"
            android:textColor="#FFFFFF"
            android:textStyle="bold"
            android:gravity="center"
            android:layout_marginBottom="24dp"/>

        <LinearLayout
            android:orientation="horizontal"
            android:gravity="center">
            
            <!-- Butonlar buraya -->
            
        </LinearLayout>
    </LinearLayout>
</androidx.cardview.widget.CardView>
```

**İşlem:**
1.  Dialog'un oluşturulduğu yeri tespit et.
2.  Eğer kod içinden `Dialog` nesnesi ile yapılıyorsa, custom bir layout dosyası oluştur (örn: `dialog_resume_playback.xml`) ve yukarıdaki tasarımı uygula.
3.  Eğer zaten bir XML varsa, yukarıdaki prensiplere göre revize et.
4.  Arka plan resmini (`ImageView`) layout hiyerarşisinden kaldır.

Bu değişikliği yaparken `layout_navbar.xml` veya diğer genel dosyalara dokunma, sadece bu dialog'a odaklan.