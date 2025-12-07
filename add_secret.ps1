# GitHub Secret Ekleme Scripti (PowerShell)
# Bu script TMDB_API_KEY secret'ını GitHub repository'nize ekler

# GitHub Token'ınızı buraya girin (veya environment variable olarak ayarlayın)
$GITHUB_TOKEN = $env:GITHUB_TOKEN
if (-not $GITHUB_TOKEN) {
    Write-Host "GitHub token bulunamadı!" -ForegroundColor Red
    Write-Host "Token'ı şu şekilde ayarlayın: `$env:GITHUB_TOKEN = 'your_token_here'" -ForegroundColor Yellow
    exit 1
}

# Repository bilgileri
$REPO_OWNER = "vbozkaya"
$REPO_NAME = "PNR-TV"  # Repository adınızı buraya yazın

# TMDB API Key'i local.properties'ten oku
$localPropertiesPath = "local.properties"
if (-not (Test-Path $localPropertiesPath)) {
    Write-Host "local.properties dosyası bulunamadı!" -ForegroundColor Red
    exit 1
}

$tmdbApiKey = ""
Get-Content $localPropertiesPath | ForEach-Object {
    if ($_ -match "TMDB_API_KEY=(.+)") {
        $tmdbApiKey = $matches[1]
    }
}

if (-not $tmdbApiKey) {
    Write-Host "TMDB_API_KEY local.properties'te bulunamadı!" -ForegroundColor Red
    exit 1
}

Write-Host "TMDB_API_KEY bulundu: $($tmdbApiKey.Substring(0, 10))..." -ForegroundColor Green

# GitHub Public Key'i al
Write-Host "GitHub public key alınıyor..." -ForegroundColor Yellow
$publicKeyResponse = Invoke-RestMethod -Uri "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/actions/secrets/public-key" `
    -Headers @{
        "Authorization" = "token $GITHUB_TOKEN"
        "Accept" = "application/vnd.github.v3+json"
    }

$keyId = $publicKeyResponse.key_id
$publicKey = $publicKeyResponse.key

Write-Host "Public key alındı. Key ID: $keyId" -ForegroundColor Green

# Secret'ı encrypt et (libsodium kullanarak - basit base64 encoding yeterli değil)
# Not: GitHub API için secret'ı encrypt etmek için libsodium kullanmanız gerekir
# Bu script sadece örnek amaçlıdır, gerçek encryption için libsodium kullanın

Write-Host ""
Write-Host "⚠️  UYARI: Bu script secret encryption için libsodium gerektirir." -ForegroundColor Yellow
Write-Host "En kolay yöntem: GitHub web arayüzünü kullanın!" -ForegroundColor Yellow
Write-Host ""
Write-Host "Veya GitHub CLI kullanın:" -ForegroundColor Cyan
Write-Host "  gh secret set TMDB_API_KEY --body `"$tmdbApiKey`"" -ForegroundColor White
Write-Host ""

# Alternatif: GitHub CLI kullan (eğer kuruluysa)
$ghCli = Get-Command gh -ErrorAction SilentlyContinue
if ($ghCli) {
    Write-Host "GitHub CLI bulundu. Secret ekleniyor..." -ForegroundColor Green
    $process = Start-Process -FilePath "gh" -ArgumentList "secret", "set", "TMDB_API_KEY", "--body", $tmdbApiKey -NoNewWindow -Wait -PassThru
    if ($process.ExitCode -eq 0) {
        Write-Host "✅ Secret başarıyla eklendi!" -ForegroundColor Green
    } else {
        Write-Host "❌ Secret eklenirken hata oluştu!" -ForegroundColor Red
    }
} else {
    Write-Host "GitHub CLI bulunamadı. Lütfen GitHub web arayüzünü kullanın." -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Adımlar:" -ForegroundColor Cyan
    Write-Host "1. https://github.com/$REPO_OWNER/$REPO_NAME/settings/secrets/actions" -ForegroundColor White
    Write-Host "2. 'New repository secret' butonuna tıklayın" -ForegroundColor White
    Write-Host "3. Name: TMDB_API_KEY" -ForegroundColor White
    Write-Host "4. Secret: $tmdbApiKey" -ForegroundColor White
    Write-Host "5. 'Add secret' butonuna tıklayın" -ForegroundColor White
}

