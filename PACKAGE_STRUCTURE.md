# PNR TV Android Project - Package Structure

## Complete Package and Folder Structure

```
com.pnr.tv
│
├── core/
│   ├── base/
│   │   ├── BannerAdDelegate.kt
│   │   ├── BaseActivity.kt
│   │   ├── BaseBrowseFragment.kt
│   │   ├── BaseContentViewModel.kt
│   │   ├── BaseViewModel.kt
│   │   ├── BrowseComponentFactory.kt
│   │   ├── BrowseDataHandler.kt
│   │   ├── BrowseDataObserver.kt
│   │   ├── BrowseFocusDelegate.kt
│   │   ├── BrowseFocusHandler.kt
│   │   ├── BrowseFocusManager.kt
│   │   ├── BrowseLifecycleObserver.kt
│   │   ├── BrowseNavbarHandler.kt
│   │   ├── BrowseSetupDelegate.kt
│   │   ├── BrowseUiHandler.kt
│   │   ├── BrowseViewContainer.kt
│   │   ├── CategoryBuilder.kt
│   │   ├── ContentFavoriteHandler.kt
│   │   ├── ContentFilterDelegate.kt
│   │   ├── CustomCategoriesRecyclerView.kt
│   │   ├── CustomContentRecyclerView.kt
│   │   ├── CustomGridLayoutManager.kt
│   │   ├── PnrTvApplication.kt
│   │   └── ToolbarController.kt
│   └── constants/
│       ├── ContentConstants.kt
│       ├── DatabaseConstants.kt
│       ├── NetworkConstants.kt
│       ├── PlayerConstants.kt
│       ├── TimeConstants.kt
│       └── UIConstants.kt
│
├── db/
│   ├── AppDatabase.kt
│   ├── dao/
│   │   ├── FavoriteDao.kt
│   │   ├── LiveStreamCategoryDao.kt
│   │   ├── LiveStreamDao.kt
│   │   ├── MovieCategoryDao.kt
│   │   ├── MovieDao.kt
│   │   ├── PlaybackPositionDao.kt
│   │   ├── RecentlyWatchedDao.kt
│   │   ├── SeriesCategoryDao.kt
│   │   ├── SeriesDao.kt
│   │   ├── TmdbCacheDao.kt
│   │   ├── UserDao.kt
│   │   ├── ViewerDao.kt
│   │   └── WatchedEpisodeDao.kt
│   ├── entity/
│   │   ├── FavoriteChannelEntity.kt
│   │   ├── LiveStreamCategoryEntity.kt
│   │   ├── LiveStreamEntity.kt
│   │   ├── MovieCategoryEntity.kt
│   │   ├── MovieEntity.kt
│   │   ├── PlaybackPositionEntity.kt
│   │   ├── RecentlyWatchedEntity.kt
│   │   ├── SeriesCategoryEntity.kt
│   │   ├── SeriesEntity.kt
│   │   ├── TmdbCacheEntity.kt
│   │   ├── UserAccountEntity.kt
│   │   ├── ViewerEntity.kt
│   │   └── WatchedEpisodeEntity.kt
│   └── migration/
│       ├── DatabaseMigrations.kt
│       ├── Migration18to19.kt
│       ├── Migrations11to20.kt
│       ├── Migrations1to10.kt
│       └── Migrations21to22.kt
│
├── di/
│   ├── CoilImageLoaderModule.kt
│   ├── DatabaseModule.kt
│   └── NetworkModule.kt
│
├── domain/
│   ├── BuildLiveStreamUrlUseCase.kt
│   ├── GetSeasonEpisodesUseCase.kt
│   └── GetSeriesDetailsUseCase.kt
│
├── extensions/
│   ├── FlowExtensions.kt
│   ├── ImageExtensions.kt
│   ├── StringExtensions.kt
│   ├── ToastExtensions.kt
│   └── ViewExtensions.kt
│
├── model/
│   ├── CategoryItem.kt
│   ├── ContentItem.kt
│   ├── ContentType.kt
│   ├── SeriesSeason.kt
│   └── SortOrder.kt
│
├── network/
│   ├── ApiActions.kt
│   ├── ApiService.kt
│   ├── RateLimiterInterceptor.kt
│   ├── TmdbApiService.kt
│   ├── XtreamApiService.kt
│   └── dto/
│       ├── LiveStreamCategoryDto.kt
│       ├── LiveStreamDto.kt
│       ├── MovieCategoryDto.kt
│       ├── MovieDto.kt
│       ├── SeriesCategoryDto.kt
│       ├── SeriesDto.kt
│       ├── SeriesInfoDto.kt
│       ├── TmdbMovieDetailsDto.kt
│       ├── TmdbSearchResultDto.kt
│       ├── TmdbTvShowDetailsDto.kt
│       └── UserInfoDto.kt
│
├── premium/
│   ├── AdManager.kt
│   ├── BillingConnectionHandler.kt
│   ├── BillingFlowHandler.kt
│   ├── BillingManager.kt
│   ├── BillingPurchaseProcessor.kt
│   ├── PremiumFeatureGuard.kt
│   ├── PremiumManager.kt
│   └── RewardedAdManager.kt
│
├── repository/
│   ├── ApiServiceManager.kt
│   ├── AuthRepository.kt
│   ├── BaseContentRepository.kt
│   ├── ContentRepository.kt
│   ├── FavoriteRepository.kt
│   ├── LiveStreamRepository.kt
│   ├── MovieRepository.kt
│   ├── NetworkPolicyHelper.kt
│   ├── PlaybackPositionRepository.kt
│   ├── RecentlyWatchedRepository.kt
│   ├── Result.kt
│   ├── SeriesRepository.kt
│   ├── TmdbDataMapper.kt
│   ├── TmdbMultilingualFetcher.kt
│   ├── TmdbRepository.kt
│   ├── TmdbSearchHelper.kt
│   ├── TmdbSearchStrategy.kt
│   ├── TmdbTvDataMapper.kt
│   ├── TmdbTvLanguageFetcher.kt
│   ├── TmdbTvRepository.kt
│   ├── TmdbTvSearchStrategy.kt
│   ├── UserRepository.kt
│   └── ViewerRepository.kt
│
├── security/
│   ├── CertificatePinningConfig.kt
│   ├── DataEncryption.kt
│   └── KeystoreManager.kt
│
├── ui/
│   ├── browse/
│   │   ├── BrowseSortHandler.kt
│   │   ├── BrowseStateHelper.kt
│   │   ├── CardPresenter.kt
│   │   ├── CategoryAdapter.kt
│   │   ├── CategoryDiff.kt
│   │   ├── ContentAdapter.kt
│   │   ├── ContentBrowseFragment.kt
│   │   ├── ContentDiff.kt
│   │   ├── CustomImageCardView.kt
│   │   └── SkeletonAdapter.kt
│   │
│   ├── debug/
│   │   └── CrashlyticsDebugActivity.kt
│   │
│   ├── livestreams/
│   │   ├── GridListRowPresenter.kt
│   │   ├── LiveStreamCategoryItemWrapper.kt
│   │   ├── LiveStreamsBrowseFragment.kt
│   │   └── LiveStreamViewModel.kt
│   │
│   ├── main/
│   │   ├── AboutActivity.kt
│   │   ├── MainActivity.kt
│   │   ├── MainFocusHandler.kt
│   │   ├── MainFragment.kt
│   │   ├── MainNavigationCoordinator.kt
│   │   ├── MainNavigationHandler.kt
│   │   ├── MainUpdateHandler.kt
│   │   ├── SessionManager.kt
│   │   ├── SettingsActivity.kt
│   │   └── SplashActivity.kt
│   │
│   ├── movies/
│   │   ├── MovieDetailActivity.kt
│   │   ├── MovieDetailFragment.kt
│   │   ├── MovieDetailUiState.kt
│   │   ├── MovieDetailViewHandler.kt
│   │   ├── MovieDetailViewModel.kt
│   │   ├── MoviePlaybackHandler.kt
│   │   └── MovieViewModel.kt
│   │
│   ├── player/
│   │   ├── adapter/
│   │   │   ├── ChannelListAdapter.kt
│   │   │   └── TrackSelectionAdapter.kt
│   │   ├── component/
│   │   │   └── PlayerControlView.kt
│   │   ├── coordinator/
│   │   │   ├── PlayerPanelCoordinator.kt
│   │   │   ├── PlayerPlaybackCoordinator.kt
│   │   │   ├── PlayerStateCoordinator.kt
│   │   │   └── PlayerVisibilityCoordinator.kt
│   │   ├── dialog/
│   │   │   └── ResumePlaybackDialog.kt
│   │   ├── handler/
│   │   │   ├── ChannelListListener.kt
│   │   │   ├── PlayerFocusHandler.kt
│   │   │   ├── PlayerIntentHandler.kt
│   │   │   ├── PlayerKeyHandler.kt
│   │   │   ├── PlayerListenerHandler.kt
│   │   │   └── PlayerObserverHandler.kt
│   │   ├── manager/
│   │   │   ├── PlayerPlaybackManager.kt
│   │   │   ├── PlayerPlaylistManager.kt
│   │   │   ├── PlayerSeekManager.kt
│   │   │   ├── PlayerStateManager.kt
│   │   │   └── PlayerTrackManager.kt
│   │   ├── panel/
│   │   │   ├── ChannelListPanel.kt
│   │   │   ├── ChannelPanelFocusHandler.kt
│   │   │   ├── ChannelPanelVisibilityHandler.kt
│   │   │   └── PlayerSettingsPanel.kt
│   │   ├── state/
│   │   │   ├── PlayerAction.kt
│   │   │   ├── PlayerStateHelper.kt
│   │   │   ├── PlayerUiState.kt
│   │   │   └── TrackInfo.kt
│   │   ├── PlayerActivity.kt
│   │   └── PlayerViewModel.kt
│   │
│   ├── series/
│   │   ├── EpisodesAdapter.kt
│   │   ├── PlaybackResumeManager.kt
│   │   ├── SeriesDetailActivity.kt
│   │   ├── SeriesDetailFragment.kt
│   │   ├── SeriesDetailListHandler.kt
│   │   ├── SeriesDetailObserverHandler.kt
│   │   ├── SeriesDetailUiState.kt
│   │   ├── SeriesDetailViewHandler.kt
│   │   ├── SeriesDetailViewModel.kt
│   │   ├── SeriesEpisodeParser.kt
│   │   ├── SeriesFavoriteHandler.kt
│   │   ├── SeriesMetadataProvider.kt
│   │   ├── SeriesModels.kt
│   │   ├── SeriesPlaybackHandler.kt
│   │   └── SeriesViewModel.kt
│   │
│   ├── settings/
│   │   ├── AccountSettingsFragment.kt
│   │   ├── GeneralSettingsFragment.kt
│   │   ├── LanguageAdapter.kt
│   │   ├── PremiumAdHandler.kt
│   │   ├── PremiumPurchaseHandler.kt
│   │   ├── PremiumSettingsFragment.kt
│   │   └── SettingsViewModel.kt
│   │
│   ├── shared/
│   │   └── SharedViewModel.kt
│   │
│   ├── users/
│   │   ├── AddUserActivity.kt
│   │   ├── AddUserViewModel.kt
│   │   ├── UserManagementActivity.kt
│   │   ├── UsersListActivity.kt
│   │   ├── UsersListAdapter.kt
│   │   └── UsersListViewModel.kt
│   │
│   └── viewers/
│       ├── SelectViewerDialog.kt
│       ├── ViewersActivity.kt
│       ├── ViewersAdapter.kt
│       └── ViewerViewModel.kt
│
├── util/
│   ├── ads/
│   │   └── MainAdHelper.kt
│   ├── error/
│   │   ├── CircuitBreaker.kt
│   │   ├── ErrorCategory.kt
│   │   ├── ErrorContext.kt
│   │   ├── ErrorHelper.kt
│   │   ├── ErrorLogger.kt
│   │   ├── ErrorSeverity.kt
│   │   ├── HttpErrorCode.kt
│   │   └── Resource.kt
│   ├── network/
│   │   ├── AdvertisingIdHelper.kt
│   │   └── NetworkUtils.kt
│   ├── ui/
│   │   ├── BackgroundManager.kt
│   │   ├── CategoryNameHelper.kt
│   │   ├── LocaleHelper.kt
│   │   ├── SafeImageLoader.kt
│   │   ├── SortPreferenceManager.kt
│   │   └── ViewExtensions.kt
│   ├── validation/
│   │   ├── AdultContentDetector.kt
│   │   ├── AdultContentPreferenceManager.kt
│   │   ├── DataValidationHelper.kt
│   │   └── IntentValidator.kt
│   ├── CrashlyticsHelper.kt
│   ├── LifecycleTracker.kt
│   ├── SecureLogger.kt
│   └── ViewerInitializer.kt
│
└── worker/
    └── TmdbSyncWorker.kt
```

## Package Overview

### Core Packages

**`core/base/`** (24 files)
- Base classes for activities, fragments, and view models
- Browse components and handlers
- Custom views and layout managers
- Application class

**`core/constants/`** (6 files)
- Application-wide constants for content, database, network, player, time, and UI

**`db/`** (32 files)
- Database: 1 file
- DAOs: 13 files
- Entities: 13 files
- Migrations: 5 files

**`di/`** (3 files)
- Dependency injection modules for database, network, and image loading

**`domain/`** (3 files)
- Domain use cases: BuildLiveStreamUrlUseCase, GetSeasonEpisodesUseCase, GetSeriesDetailsUseCase

**`extensions/`** (5 files)
- Kotlin extension functions for Flow, Image, String, Toast, and View

**`model/`** (5 files)
- Domain models: CategoryItem, ContentItem, ContentType, SeriesSeason, SortOrder

**`network/`** (17 files)
- API services: 5 files
- DTOs: 11 files
- Interceptors: 1 file

**`premium/`** (8 files)
- Premium features: billing, ads, and premium management

**`repository/`** (23 files)
- Data access layer repositories for all content types
- TMDB integration and search strategies
- Network and API service management

**`security/`** (3 files)
- Security features: encryption, certificate pinning, keystore management

**`util/`** (25 files)
- `util/ads/`: 1 file
- `util/error/`: 8 files
- `util/network/`: 2 files
- `util/ui/`: 6 files
- `util/validation/`: 4 files
- Root level utilities: 4 files

**`worker/`** (1 file)
- Background workers

### UI Feature Packages

**`ui/browse/`** (10 files)
- Content browsing with categories, adapters, and state management

**`ui/debug/`** (1 file)
- Debug utilities

**`ui/livestreams/`** (4 files)
- Live stream browsing and viewing

**`ui/main/`** (10 files)
- Main activities: MainActivity, SplashActivity, AboutActivity, SettingsActivity
- Main fragment and navigation components
- Session management

**`ui/movies/`** (7 files)
- Movie detail views, playback handlers, and view models

**`ui/player/`** (29 files)
- Comprehensive player organized into sub-packages:
  - `adapter/`: Channel and track selection adapters (2 files)
  - `component/`: Player control view (1 file)
  - `coordinator/`: Playback, panel, state, and visibility coordinators (4 files)
  - `dialog/`: Resume playback dialog (1 file)
  - `handler/`: Focus, intent, key, listener, and observer handlers (6 files)
  - `manager/`: Playback, playlist, seek, state, and track managers (5 files)
  - `panel/`: Channel list and settings panels with focus handlers (4 files)
  - `state/`: Player action, state helper, UI state, and track info (4 files)
  - Root level: PlayerActivity and PlayerViewModel (2 files)

**`ui/series/`** (15 files)
- Series detail views, episodes management, playback handlers, and metadata providers

**`ui/settings/`** (7 files)
- Settings fragments: Account, General, Premium
- Settings adapters and handlers

**`ui/shared/`** (1 file)
- Shared view models

**`ui/users/`** (6 files)
- User management activities and view models

**`ui/viewers/`** (4 files)
- Viewer management for multi-user support

---

**Total Files**: 248 Kotlin files
