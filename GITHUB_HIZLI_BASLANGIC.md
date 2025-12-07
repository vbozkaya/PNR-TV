# GitHub'a Bağlama - Hızlı Başlangıç

## ⚡ Hızlı Adımlar

### 1. Git Kullanıcı Bilgilerini Ayarlayın

```powershell
# Email ve isminizi ayarlayın
git config --global user.email "your-email@example.com"
git config --global user.name "Your Name"

# Veya sadece bu proje için:
git config user.email "your-email@example.com"
git config user.name "Your Name"
```

### 2. İlk Commit'i Yapın

```powershell
git commit -m "Initial commit: PNR TV Android TV Application"
```

### 3. GitHub'da Repository Oluşturun

1. https://github.com/new adresine gidin
2. Repository adı: `PNR-TV`
3. **"Initialize with README" seçeneğini İŞARETLEMEYİN**
4. **Create repository** butonuna tıklayın

### 4. Repository'yi Bağlayın

```powershell
# GitHub repository URL'inizi kullanın
git remote add origin https://github.com/vbozkaya/PNR-TV.git

# Push edin
git push -u origin main
```

### 5. GitHub Secrets Ekleyin

1. Repository > **Settings** > **Secrets and variables** > **Actions**
2. **New repository secret**
3. Name: `TMDB_API_KEY`
4. Secret: `b38260e06bcb387355ab90a002a59ca5`
5. **Add secret**

## ✅ Tamamlandı!

Artık CI/CD pipeline otomatik çalışacak! 🚀

