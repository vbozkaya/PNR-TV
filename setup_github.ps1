# GitHub Repository Kurulum Scripti
# Bu script projenizi GitHub'a bağlamak için gerekli adımları otomatikleştirir

Write-Host "🚀 GitHub Repository Kurulum Başlatılıyor..." -ForegroundColor Cyan
Write-Host ""

# Git kurulu mu kontrol et
try {
    $gitVersion = git --version
    Write-Host "✅ Git bulundu: $gitVersion" -ForegroundColor Green
} catch {
    Write-Host "❌ Git bulunamadı! Lütfen Git'i kurun: https://git-scm.com/downloads" -ForegroundColor Red
    exit 1
}

# Git repository var mı kontrol et
if (Test-Path ".git") {
    Write-Host "⚠️  Git repository zaten mevcut." -ForegroundColor Yellow
    $continue = Read-Host "Devam etmek istiyor musunuz? (y/n)"
    if ($continue -ne "y") {
        exit 0
    }
} else {
    Write-Host "📦 Git repository oluşturuluyor..." -ForegroundColor Yellow
    git init
    Write-Host "✅ Git repository oluşturuldu" -ForegroundColor Green
}

# Dosyaları ekle
Write-Host ""
Write-Host "📝 Dosyalar staging area'ya ekleniyor..." -ForegroundColor Yellow
git add .

# Commit yap
Write-Host ""
Write-Host "💾 İlk commit yapılıyor..." -ForegroundColor Yellow
$commitMessage = "Initial commit: PNR TV Android TV Application"
git commit -m $commitMessage

if ($LASTEXITCODE -eq 0) {
    Write-Host "✅ Commit başarılı" -ForegroundColor Green
} else {
    Write-Host "⚠️  Commit yapılamadı (dosya değişikliği yok olabilir)" -ForegroundColor Yellow
}

# GitHub repository bilgilerini al
Write-Host ""
Write-Host "🔗 GitHub Repository Bilgileri" -ForegroundColor Cyan
Write-Host "─────────────────────────────────────────" -ForegroundColor Gray
$repoOwner = Read-Host "GitHub kullanıcı adınız (örn: vbozkaya)"
$repoName = Read-Host "Repository adı (örn: PNR-TV)"

# Remote ekle
Write-Host ""
Write-Host "🌐 GitHub remote ekleniyor..." -ForegroundColor Yellow
$remoteUrl = "https://github.com/$repoOwner/$repoName.git"
Write-Host "Remote URL: $remoteUrl" -ForegroundColor Gray

# Mevcut remote var mı kontrol et
$existingRemote = git remote get-url origin 2>$null
if ($existingRemote) {
    Write-Host "⚠️  Mevcut remote bulundu: $existingRemote" -ForegroundColor Yellow
    $replace = Read-Host "Değiştirmek istiyor musunuz? (y/n)"
    if ($replace -eq "y") {
        git remote remove origin
        git remote add origin $remoteUrl
        Write-Host "✅ Remote güncellendi" -ForegroundColor Green
    }
} else {
    git remote add origin $remoteUrl
    Write-Host "✅ Remote eklendi" -ForegroundColor Green
}

# Branch adını ayarla
Write-Host ""
Write-Host "🌿 Branch adı ayarlanıyor..." -ForegroundColor Yellow
git branch -M main
Write-Host "✅ Branch 'main' olarak ayarlandı" -ForegroundColor Green

# Özet
Write-Host ""
Write-Host "═══════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "✅ Kurulum Tamamlandı!" -ForegroundColor Green
Write-Host "═══════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""
Write-Host "📋 Sonraki Adımlar:" -ForegroundColor Yellow
Write-Host ""
Write-Host "1. GitHub'da repository oluşturun:" -ForegroundColor White
Write-Host "   https://github.com/new" -ForegroundColor Gray
Write-Host "   Repository adı: $repoName" -ForegroundColor Gray
Write-Host "   UYARI: 'Initialize with README' secenegini ISARETLEMEYIN" -ForegroundColor Red
Write-Host ""
Write-Host "2. Repository oluşturduktan sonra şu komutu çalıştırın:" -ForegroundColor White
Write-Host "   git push -u origin main" -ForegroundColor Cyan
Write-Host ""
Write-Host "3. GitHub Secrets ekleyin:" -ForegroundColor White
Write-Host "   https://github.com/$repoOwner/$repoName/settings/secrets/actions" -ForegroundColor Gray
Write-Host "   Secret adı: TMDB_API_KEY" -ForegroundColor Gray
Write-Host "   Secret değeri: local.properties dosyanızdaki TMDB_API_KEY" -ForegroundColor Gray
Write-Host ""
Write-Host "🎉 Hazırsınız!" -ForegroundColor Green

