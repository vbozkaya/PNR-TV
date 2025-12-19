# GitHub'a Yayınlama - Hızlı Başlangıç

Bu dosya projenizi GitHub'a yayınlamak için gereken **pratik adımları** içerir.

## ⚡ Hızlı Adımlar

### 1️⃣ Dosyaları Commit Edin

```powershell
# Tüm değişiklikleri staging area'ya ekle
git add .

# İlk commit'i yap
git commit -m "Initial commit: PNR TV Android TV Application"
```

### 2️⃣ GitHub Repository Oluşturun

1. https://github.com/new adresine gidin
2. Repository adı: `PNR-TV` (veya istediğiniz isim)
3. **ÖNEMLİ:** "Initialize with README" seçeneğini **İŞARETLEMEYİN**
4. "Create repository" butonuna tıklayın

### 3️⃣ Remote Ekleyin

```powershell
# KULLANICI_ADINIZ ve REPOSITORY_ADI'ni değiştirin
git remote add origin https://github.com/KULLANICI_ADINIZ/REPOSITORY_ADI.git

# Örnek:
# git remote add origin https://github.com/vbozkaya/PNR-TV.git
```

### 4️⃣ GitHub Secrets Ekleyin

1. GitHub repository > **Settings** > **Secrets and variables** > **Actions**
2. **New repository secret** butonuna tıklayın
3. **Name:** `TMDB_API_KEY`
4. **Secret:** `local.properties` dosyanızdaki TMDB_API_KEY değerini yapıştırın
5. **Add secret** butonuna tıklayın

### 5️⃣ GitHub Pages Aktifleştirin

1. GitHub repository > **Settings** > **Pages**
2. **Source:** `Deploy from a branch` seçin
3. **Branch:** `main`
4. **Folder:** `/docs`
5. **Save** butonuna tıklayın

### 6️⃣ Push Yapın

```powershell
# Branch'i main olarak ayarla
git branch -M main

# GitHub'a push yap
git push -u origin main
```

Eğer kimlik doğrulama hatası alırsanız, Personal Access Token kullanmanız gerekebilir:
- GitHub > Settings > Developer settings > Personal access tokens > Tokens (classic)
- "Generate new token" > "repo" scope'unu işaretleyin
- Token'ı oluşturup, push sırasında password yerine kullanın

### 7️⃣ Doğrulayın

1. **GitHub Actions:** Repository > Actions sekmesinde workflow'un çalıştığını kontrol edin
2. **GitHub Pages:** Settings > Pages'de URL'in aktif olduğunu kontrol edin (birkaç dakika sürebilir)
3. **Sayfayı Test Edin:** `https://KULLANICI_ADINIZ.github.io/REPOSITORY_ADI/privacy-policy.html` adresini tarayıcıda açın

---

## ✅ Kontrol Listesi

- [ ] Git repository başlatıldı (`git init`)
- [ ] Dosyalar commit edildi
- [ ] GitHub repository oluşturuldu
- [ ] Remote eklendi
- [ ] GitHub Secrets'a TMDB_API_KEY eklendi
- [ ] GitHub Pages aktifleştirildi
- [ ] Push yapıldı
- [ ] GitHub Actions çalışıyor
- [ ] GitHub Pages erişilebilir

---

## 📚 Detaylı Rehber

Daha detaylı bilgi için `GITHUB_RELEASE_GUIDE.md` dosyasına bakın.

---

## 🆘 Hızlı Çözümler

**"Permission denied" hatası:**
- Personal Access Token kullanın

**"TMDB_API_KEY bulunamadı" hatası:**
- GitHub Secrets'da secret'ın doğru eklendiğini kontrol edin

**GitHub Pages 404 hatası:**
- Settings > Pages'de branch ve folder'ın doğru seçildiğini kontrol edin
- Birkaç dakika bekleyin

