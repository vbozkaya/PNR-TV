# ANDROID TV GHOST FOCUS & FOCUS BOUNCE FIX

Sen Expert bir Android Geliştiricisisin. Aşağıdaki talimatları `BaseBrowseFragment.kt` dosyasında, **yorum yapmadan ve başka hiçbir şeye dokunmadan** uygula.

**SORUN:**
`submitList` callback içinde `post { requestFocus() }` kullanılmasına rağmen, focus Grid üzerinde tutunamıyor ve "Default Focus" olan Kategori listesine geri sekiyor (Focus Bounce). Kullanıcı "Görünmez Focus" sorunu yaşıyor çünkü sistem odağı Grid'de sanırken aslında odak Kategorilere kaçmış oluyor.

**HEDEF:**
1.  `post` yerine `doOnPreDraw` kullanarak, focus işlemini View'ın çizim anına senkronize etmek.
2.  Kategori listesinin (`observeCategories`), eğer içerik restore edilecekse odağı çalmasını engellemek.

---

### 🛠️ ADIM 1: `observeContents` Düzenlemesi (Timing Fix)

`observeContents` içindeki `submitList` bloğunu şu şekilde güncelle (`post` yerine `doOnPreDraw` kullanımı):

```kotlin
                    contentAdapter.submitList(contents) {
                        // [FIX]: Bekleyen bir restore işlemi varsa uygula
                        val pendingPos = pendingRestorePosition
                        if (pendingPos != null && pendingPos < contentAdapter.itemCount) {
                             Log.d("FOCUS_DEBUG", "🚀 [BASE] Pending Restore (doOnPreDraw) hazırlanıyor - Position: $pendingPos")
                             
                             // Scroll yap
                             val layoutManager = contentRecyclerView.layoutManager as? androidx.recyclerview.widget.GridLayoutManager
                             if (layoutManager != null) {
                                 layoutManager.scrollToPositionWithOffset(pendingPos, 200)
                             } else {
                                 contentRecyclerView.scrollToPosition(pendingPos)
                             }
                             
                             // [CRITICAL FIX]: post yerine doOnPreDraw kullan
                             // View çizilmeden hemen önce focus ver ki boşa düşmesin
                             contentRecyclerView.doOnPreDraw {
                                 val viewHolder = contentRecyclerView.findViewHolderForAdapterPosition(pendingPos)
                                 if (viewHolder != null) {
                                     // Focus'u zorla
                                     viewHolder.itemView.isFocusable = true
                                     viewHolder.itemView.isFocusableInTouchMode = true
                                     if (viewHolder.itemView.requestFocus()) {
                                         Log.d("FOCUS_DEBUG", "🎯 [BASE] Focus BAŞARIYLA verildi - Position: $pendingPos")
                                     } else {
                                         Log.w("FOCUS_DEBUG", "❌ [BASE] Focus isteği REDDEDİLDİ - Position: $pendingPos")
                                     }
                                 } else {
                                     Log.w("FOCUS_DEBUG", "⚠️ [BASE] ViewHolder bulunamadı (doOnPreDraw) - Position: $pendingPos")
                                 }
                                 
                                 // İşlem tamam
                                 pendingRestorePosition = null
                                 viewModel.lastFocusedContentPosition = null
                             }
                        }

                        // Veri hazır - ViewModel'e bildir
                        if (!viewModel.isDataReady && contents.isNotEmpty()) {
                            viewModel.isDataReady = true
                            Log.d("FOCUS_DEBUG", "✅ [BASE] Veri hazır")
                        }
                    }
```

### 🛠️ ADIM 2: `observeCategories` Düzenlemesi (Focus Stealing Guard)

`observeCategories` içindeki `submitList` callback'ine bir kontrol ekle. Eğer içerik tarafında bekleyen bir restore varsa (`viewModel.lastFocusedContentPosition != null`), kategori listesi ASLA ilk kategoriye focus atmasın.

Mevcut kodun son kısmını (else if bloğunu) şöyle güncelle:

```kotlin
                            } else if (categories.isNotEmpty()) {
                                // [FIX]: Eğer içerik tarafında bekleyen bir restore işlemi varsa (pendingRestorePosition veya viewModel'de kayıtlıysa),
                                // Kategori listesi odağı ÇALMAMALI.
                                val hasPendingContentRestore = pendingRestorePosition != null || viewModel.lastFocusedContentPosition != null
                                
                                if (!hasPendingContentRestore) {
                                    // Ana sayfadan geldi veya isInitialLaunch - ilk kategoriye focus ver
                                    focusDelegate?.focusFirstCategory(
                                        categories.first(),
                                        onCategoryClicked = { category -> onCategoryClicked(category) },
                                    )
                                } else {
                                    Log.d("FOCUS_DEBUG", "🛡️ [BASE] Kategori focus'u engellendi çünkü İçerik Restore bekleniyor.")
                                }
                            }
```

Sadece bu değişiklikleri yap. Başka hiçbir koda dokunma.
