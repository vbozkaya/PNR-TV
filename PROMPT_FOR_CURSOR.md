# Görev: Güncelleme Overlay Sorununu Düzeltme

## Sorun Tanımı
Uygulamada güncelleme işlemi yapılırken açılan `loading_overlay`, ekranın üst kısmındaki butonları (Güncelle, Ayarlar vb.) engellemiyor. Kullanıcı overlay aktifken bile arkadaki butonlara erişebiliyor.

Bunun nedeni `app/src/main/res/layout/activity_main.xml` dosyasındaki Z-index sıralama hatasıdır. `loading_overlay` şu anda butonların bulunduğu `ConstraintLayout`'tan **önce** tanımlanmış durumda. Android XML layout'larında son tanımlanan view en üstte çizilir.

## Yapılacak İşlemler

**Dosya:** `app/src/main/res/layout/activity_main.xml`

1.  Mevcut dosya yapısını incele.
2.  `android:id="@+id/loading_overlay"` olan `FrameLayout` bloğunu (içeriğiyle birlikte tamamen) bulunduğu yerden kes.
3.  Bu bloğu, dosyanın en sonuna, root `FrameLayout` kapanış etiketinden (`</FrameLayout>`) hemen önceye yapıştır.
4.  Böylece sıralama şu şekilde olmalı:
    *   `fragment_container` (En altta)
    *   `ConstraintLayout` (Butonları içeren katman - Ortada)
    *   `loading_overlay` (En üstte)

Bu değişiklik sayesinde overlay görünür olduğunda fiziksel olarak diğer tüm öğelerin üzerinde çizilecek ve focus/click olaylarını kesecektir. Eğer kod içinde veya layout'ta bu mantıkla çakışan (örneğin overlay'i özellikle arkaya atmaya çalışan) başka bir özellik varsa kaldır.

Sadece XML dosyasında sıralama değişikliği yapman yeterlidir. Java/Kotlin kodunda herhangi bir değişiklik yapma.