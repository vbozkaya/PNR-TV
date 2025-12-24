# 🛡️ PNR TV Focus & Navigasyon Mimarisi

Bu projede "Reactive/Patching" (Yama) yöntemi yerine **"Deterministic" (Kararlı)** focus yönetimi kullanılmaktadır. Bu yapıyı bozmamak için aşağıdaki kurallar **KIRMIZI ÇİZGİDİR**:

## 🚫 YASAKLAR (Kesinlikle Yapılmayacaklar)
1.  **NO `postDelayed`:** Focus işlemleri için asla `Handler().postDelayed` veya `Timer` kullanılmayacak. Focus, veri hazır olduğu an (`doOnPreDraw` veya `StateRestorationPolicy`) verilmelidir.
2.  **NO Focus Fighting:** Sistemin focus'u nereye koyduğunu sürekli kontrol eden `Loop`'lar veya `Retry` mekanizmaları yasaktır.
3.  **NO Navbar Locking:** Navbar butonlarının `isFocusable` özelliğini sürekli açıp kapatan (toggle) kodlar yazılmayacak. Butonlar her zaman erişilebilir kalmalı, navigasyon mantığı yönlendirmelidir.
4.  **NO Race Conditions:** Premium kontrolü gibi asenkron işlemler, View hiyerarşisi hazır olmadan UI elementlerinin focus özelliğini değiştirmemeli (Flag pattern kullanılmalı).

## ✅ DOĞRULAR (Uygulanacak Yöntemler)
1.  **StateRestorationPolicy:** RecyclerView'larda `PREVENT_WHEN_EMPTY` kullanılacak. Veri gelmeden liste çizilmemeli.
2.  **doOnPreDraw:** Focus verilmesi gerekiyorsa, View'ın çizilmesinin bittiği `doOnPreDraw` callback'inde verilecek.
3.  **Flag Pattern:** Asenkron veriler (örn: Premium durumu), UI hazır değilse bir değişkende (flag) saklanacak, UI yüklendiğinde (`restoreNavbar`) uygulanacak.