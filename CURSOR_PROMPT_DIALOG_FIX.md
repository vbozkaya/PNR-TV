# Cursor AI Task: Dialog Buton Hizalama Düzeltmesi

**Sorun:**
"Resume Playback" dialogundaki butonlar ("Resume" ve "Start From Beginning") eşit yükseklikte değil. Sağdaki butonun metni iki satıra taştığı için buton dikey olarak uzuyor ve simetriyi bozuyor.

**Hedef:**
Her iki butonun da **yüksekliğini eşitlemek** ve metinleri butonların tam ortasına hizalamak.

**Uygulanacak Değişiklikler (Layout XML):**

İlgili Dialog layout dosyasında (muhtemelen `dialog_resume_playback.xml` veya benzeri) butonların bulunduğu `LinearLayout` içindeki buton özelliklerini şu şekilde güncelle:

1.  **Sabit Yükseklik Ver:** Her iki butona da aynı sabit yüksekliği ver. Metin iki satır olsa bile sığması için biraz yüksek tut.
    *   `android:layout_height="80dp"` (veya tasarıma uygun görünen sabit bir değer)

2.  **Metin Hizalaması:** Metinlerin butonun tam ortasında durduğundan emin ol.
    *   `android:gravity="center"`

3.  **Genişlik ve Ağırlık (Opsiyonel ama Önerilir):** Butonların genişliklerinin de dengeli olması için `weight` kullanabilirsin.
    *   Butonların kapsayıcısı olan `LinearLayout`'a `android:weightSum="2"` ver.
    *   Her iki butona da:
        *   `android:layout_width="0dp"`
        *   `android:layout_weight="1"`
    *   Aralarına boşluk bırakmak için `android:layout_marginEnd="16dp"` (soldaki butona) veya ortalarına boş bir `Space` view ekle.

**Örnek Kod Yapısı:**

```xml
<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:gravity="center"
    android:weightSum="2"> <!-- Butonları eşit genişliğe zorlar -->

    <!-- SOL BUTON -->
    <Button (veya AppCompatButton/TextView)
        android:id="@+id/btn_resume"
        android:layout_width="0dp"
        android:layout_height="80dp"  <!-- SABİT YÜKSEKLİK -->
        android:layout_weight="1"
        android:layout_marginEnd="8dp"
        android:gravity="center"
        android:text="RESUME FROM POSITION"
        ... />

    <!-- SAĞ BUTON -->
    <Button (veya AppCompatButton/TextView)
        android:id="@+id/btn_start_over"
        android:layout_width="0dp"
        android:layout_height="80dp"  <!-- AYNI SABİT YÜKSEKLİK -->
        android:layout_weight="1"
        android:layout_marginStart="8dp"
        android:gravity="center"
        android:text="START FROM\nBEGINNING"
        ... />

</LinearLayout>
```

**Özet:** Lütfen butonların `layout_height` değerlerini sabitle (örn: `80dp`) ve `gravity="center"` olduğundan emin ol. Böylece metin kaç satır olursa olsun butonlar simetrik duracaktır.