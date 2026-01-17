# PNR TV Project

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.22-blue.svg)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Android-TV-green.svg)](https://developer.android.com/tv)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

PNR TV - Smart Media Player is a professional media player application optimized specifically for Android TV interfaces. It delivers a smooth, intuitive, and cinematic experience for your video content, allowing you to manage and play your playlists with ease.

This project does not target mobile devices and is entirely focused on TV user experience.

## 📋 Table of Contents

- [Features](#-features)
- [Technologies](#-technologies)
- [Installation](#-installation)
- [Documentation](#-documentation)
- [Contributing](#-contributing)
- [Copyright and Disclaimer](#️-copyright-and-disclaimer)
- [License](#-license)

## ✨ Features

### Content Management
- 🎬 **Movies (VOD):** Movie categories, details, and playback
- 📺 **Series:** Series categories, seasons, episodes, and playback
- 📡 **Live Streams:** Live TV channels and categories
- 🔍 **TMDB Integration:** The Movie Database support for movie and series details

### User Features
- 👤 **Multi-User:** Multiple media service account management
- 🎭 **Viewer Profiles:** Separate viewer profiles for each user
- ⭐ **Favorites:** Save favorite content
- 📚 **Recently Watched:** Track recently watched content
- ⏯️ **Playback Position:** Resume from where you left off

### Other Features
- 🔄 **Offline Support:** Local database for offline operation
- 🎨 **Modern UI:** Optimized interface with Android TV Leanback Library
- 🔐 **Security:** Network security config with custom certificate support
- 📊 **Analytics:** Firebase Analytics and Crashlytics integration

## 🛠️ Technologies

### Language and Platform
- **Language:** Kotlin 1.9.22
- **Platform:** Android TV
- **Min SDK:** 21 (Android 5.0 Lollipop)
- **Target SDK:** 34 (Android 14)

### Architecture
- **Architecture Pattern:** MVVM (Model-View-ViewModel)
- **Dependency Injection:** Hilt (Dagger)
- **Reactive Programming:** Kotlin Coroutines & Flow

### Libraries
- **UI:** AndroidX Leanback, Material Design
- **Database:** Room
- **Network:** Retrofit, OkHttp, Moshi
- **Media:** ExoPlayer (Media3)
- **Image Loading:** Coil
- **Analytics:** Firebase Analytics, Crashlytics
- **Background Tasks:** WorkManager

## 🚀 Installation

For detailed installation guide, see [SETUP_GUIDE.md](SETUP_GUIDE.md).

### Quick Start

1. **Requirements:**
   - Android Studio Hedgehog (2023.1.1) or higher
   - JDK 21
   - Android SDK 34

2. **Clone the Project:**
   ```bash
   git clone <repository-url>
   cd "PNR TV"
   ```

3. **Configuration:**
   - Create `local.properties` file
   - Add `TMDB_API_KEY` (see SETUP_GUIDE.md for details)

4. **Run:**
   - Open the project in Android Studio
   - Wait for Gradle synchronization
   - Run on Android TV emulator or physical device

## 📚 Documentation

Project documentation:

- **API_DOCUMENTATION.md** - API endpoints and request/response formats
- **ARCHITECTURE.md** - Architecture structure, components, and data flow
- **SETUP_GUIDE.md** - Detailed installation and configuration guide
- **CONTRIBUTING.md** - Contributing guide
- **CHANGELOG.md** - Version history and changes

### Project Structure

```
app/src/main/java/com/pnr/tv/
├── db/              # Room Database (Entities, DAOs)
├── di/              # Hilt Modules (Dependency Injection)
├── domain/          # Use Cases, Models
├── network/         # API Services, DTOs
├── repository/      # Data Repositories
├── ui/              # UI Components (Activities, Fragments, ViewModels)
└── util/            # Utility Classes
```

## 🤝 Contributing

If you want to contribute, please read the CONTRIBUTING.md file.

### Contribution Process

1. Fork the project
2. Create a feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## ⚠️ Copyright and Disclaimer

PNR TV does not supply or include any media or content. The app is purely a media player that allows users to play content sources they provide themselves (e.g., playlists, network streams).

PNR TV has no affiliation with any third-party content provider. Users must provide their own content from their own storage or playlists.

We do not endorse the streaming of copyright-protected material without permission of the copyright holder.

The user is solely responsible for the copyright compliance, legality, and compliance with the legislation of their country regarding the content they watch or access.

As developers of this application:
- We accept no responsibility for the content users watch
- We accept no responsibility for copyright violations
- We accept no responsibility for illegal content usage

Users are explicitly advised not to watch any copyrighted and illegal broadcasts.

## 📝 License

This project is licensed under the MIT License. See the LICENSE file for details.

## 🙏 Acknowledgments

- **The Movie Database (TMDB)** - For movie and series data
- **Android TV Leanback Library** - For TV UI components
- **All open-source library developers**

---

**Note:** This project is developed exclusively for the Android TV platform. It does not work on mobile devices.
