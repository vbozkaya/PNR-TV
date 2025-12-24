# Cursor AI Task: Dialog Butonlarını Layout İçine Taşıma

**Sorun:**
"Favoriye Ekle" (Select Viewer) dialogunda, "OK" ve "CANCEL" butonları standart `AlertDialog` butonları olarak eklendiği için, özel tasarım yaptığımız kartın (CardView) dışında ve altında kalıyor. Bu görsel bütünlüğü bozuyor.

**Hedef:**
Butonları standart AlertDialog butonları olmaktan çıkarıp, `dialog_select_viewer.xml` layout dosyasının içine, kartın en altına eklemek. Böylece butonlar da siyah kutunun içinde görünecek.

**Yapılacak İşlemler:**

### 1. Layout Güncellemesi (`dialog_select_viewer.xml`)
Mevcut CardView -> LinearLayout yapısının **en altına** (RecyclerView'dan sonra) yeni bir buton alanı ekle.

```xml
<!-- Önceki kodlar... -->

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/recycler_viewers"
        ... />

    <!-- YENİ BUTON ALANI -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="end"
        android:paddingTop="16dp">

        <Button (veya TextView)
            android:id="@+id/btn_dialog_cancel"
            android:text="CANCEL"
            android:background="?attr/selectableItemBackground"
            android:textColor="#CCFFFFFF"
            android:textStyle="bold"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:padding="12dp"
            android:layout_marginEnd="8dp"
            android:focusable="true"
            android:focusableInTouchMode="true"/>

        <Button (veya TextView)
            android:id="@+id/btn_dialog_ok"
            android:text="OK"
            android:background="@drawable/navyfocus_selector" 
            android:textColor="#FFFFFF"
            android:textStyle="bold"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:paddingStart="24dp"
            android:paddingEnd="24dp"
            android:paddingTop="12dp"
            android:paddingBottom="12dp"
            android:focusable="true"
            android:focusableInTouchMode="true"/>
            
    </LinearLayout>

</LinearLayout>
</androidx.cardview.widget.CardView>
```

### 2. Kotlin Kodu Güncellemesi (Dialog Oluşturulan Yer)
Dialogun oluşturulduğu dosya (muhtemelen `MovieDetailFragment.kt` veya `BaseContentFragment.kt` içinde `showSelectViewerDialog` gibi bir metod):

1.  `AlertDialog.Builder` üzerindeki `.setPositiveButton(...)` ve `.setNegativeButton(...)` çağrılarını **KALDIR**.
2.  Bunun yerine, layout inflate edildikten sonra butonları `view.findViewById` ile bul ve tıklama olaylarını (click listener) orada tanımla.
3.  Cancel butonu -> `dialog.dismiss()`
4.  OK butonu -> Mevcut OK butonu mantığını buraya taşı (seçili viewer varsa favori ekle, yoksa uyarı ver vb.).

**Kod Mantığı Örneği:**

```kotlin
// Layout inflate et
val view = LayoutInflater.from(context).inflate(R.layout.dialog_select_viewer, null)

// Butonları bul
val btnCancel = view.findViewById<View>(R.id.btn_dialog_cancel)
val btnOk = view.findViewById<View>(R.id.btn_dialog_ok)

// Dialogu oluştur (Butonsuz)
val dialog = AlertDialog.Builder(context)
    .setView(view)
    .create()

// Click Listener'ları tanımla
btnCancel.setOnClickListener {
    dialog.dismiss()
}

btnOk.setOnClickListener {
    // Mevcut favori ekleme mantığı buraya
    // ...
    dialog.dismiss()
}

dialog.show()
```

Bu değişiklikle butonlar siyah kartın içine taşınmış ve tasarımla bütünleşmiş olacak.