Sen uzman bir Android geliştiricisisin. Mevcut projede Google Play Billing entegrasyonuyla ilgili bir sorun yaşanıyor.

**Sorun:**
Kullanıcı "Premium Satın Al / Restore" butonuna tıkladığında, eğer ürünü daha önce satın almışsa, Google Play "Öğe zaten satın alınmış" (Item Already Owned) yanıtını veriyor. Ancak bu yanıt alındıktan sonra uygulama içinde Premium özellikler aktifleşmiyor. Kullanıcı hala "Free" versiyonda kalıyor.

**Görev:**
Bu sorunu analiz et ve düzelt. Aşağıdaki adımları takip et:

1.  **`PremiumManager.kt` dosyasını analiz et:**
    *   Premium durumunun (`isPremium`) nereye ve nasıl kaydedildiğini kontrol et (SharedPreferences, DataStore, vb.).
    *   `setPremiumStatus(true)` metodunun veriyi kalıcı olarak disk'e yazdığından ve `isPremium()` flow'unun bu değişikliği anında yayınladığından emin ol.

2.  **`BillingManager.kt` dosyasını incele:**
    *   `queryPurchases` ve `handlePurchases` metodlarını kontrol et.
    *   Google Play'den dönen satın alma listesinde ürün varsa (`PREMIUM_PRODUCT_ID`), kodun `premiumManager.setPremiumStatus(true)` satırına ulaşıp ulaşmadığını doğrula.
    *   Özellikle `purchase.isAcknowledged` (zaten onaylanmış) durumunda, kodun sadece log yazıp çıkmadığından, **mutlaka** premium durumunu güncellediğinden emin ol.
    *   UI güncellemeleri için Coroutine Scope'ların (Dispatchers.Main vs Dispatchers.IO) doğru kullanıldığını kontrol et.

3.  **`SettingsActivity.kt` dosyasını kontrol et:**
    *   Restore işleminden sonra UI'ın `PremiumManager`'daki değişikliği dinleyip dinlemediğine bak.
    *   Restore butonu tıklandığında yapılan `billingManager.queryPurchases()` çağrısının sonucunu bekleme mantığını (delay vs. flow collection) gözden geçir. `delay` kullanmak yerine reaktif bir yapı (StateFlow dinleme) kurulabilir mi?

**Beklenen Sonuç:**
Kullanıcı Restore/Satın Al butonuna bastığında ve "Zaten Sahip" yanıtı döndüğünde, uygulama bunu yakalamalı, yerel veritabanını/tercihleri "Premium: true" olarak güncellemeli ve UI anında bu değişikliği yansıtmalıdır.
