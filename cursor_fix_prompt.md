# Task: Fix Empty Channel List Panel Issue by Implementing Synchronous Data Fetching

## Context & Problem
The `ChannelListPanel` currently opens with an empty list when triggered via DPAD_LEFT.
**Root Cause:** The function `ContentRepository.getLiveStreamsByCategoryIdSync` improperly consumes a reactive `Flow` using `.first { it !is Resource.Loading }`.
**Technical Failure:** Room/Flow often emits an initial empty list (or cache state) immediately. The `.first()` operator captures this initial empty state and cancels the collection before the actual data arrives from the database. This race condition results in an empty UI.

## Required Solution
Refactor the data fetching pipeline to use a direct **Suspend Function (Synchronous)** approach instead of collecting a Flow. Do NOT rely on `Flow.first()` for this operation.

## Implementation Steps

### Step 1: Modify DAO
**File:** `app/src/main/java/com/pnr/tv/db/dao/LiveStreamDao.kt`
*   Add a new suspend method named `getByCategoryIdSync`.
*   Use the exact same SQL query as the existing `getByCategoryId` method.
*   **Crucial:** It must return `List<LiveStreamEntity>` directly, NOT `Flow`.

```kotlin
@Query("SELECT * FROM live_streams WHERE categoryId = :categoryId ORDER BY name ASC")
suspend fun getByCategoryIdSync(categoryId: Int): List<LiveStreamEntity>
```

### Step 2: Update LiveStreamRepository
**File:** `app/src/main/java/com/pnr/tv/repository/LiveStreamRepository.kt`
*   Add a new suspend function named `getLiveStreamsByCategoryIdSync`.
*   It should delegate directly to the new DAO method.

```kotlin
suspend fun getLiveStreamsByCategoryIdSync(categoryId: Int): List<LiveStreamEntity> {
    return liveStreamDao.getByCategoryIdSync(categoryId)
}
```

### Step 3: Fix ContentRepository
**File:** `app/src/main/java/com/pnr/tv/repository/ContentRepository.kt`
*   Locate the existing function `getLiveStreamsByCategoryIdSync(categoryId: Int)`.
*   **Delete** the entire Flow collection logic (`.first { ... }` and `Resource` handling).
*   **Replace** it with a direct call to the new repository method.

```kotlin
suspend fun getLiveStreamsByCategoryIdSync(categoryId: Int): List<LiveStreamEntity> {
    return liveStreamRepository.getLiveStreamsByCategoryIdSync(categoryId)
}
```

## Constraints
*   **DO NOT** modify or delete the existing Flow-based methods (`getLiveStreamsByCategoryId`), as they may be used by other observers.
*   **DO NOT** change the signature of `ContentRepository.getLiveStreamsByCategoryIdSync`.
*   Ensure the solution is strictly synchronous (suspend) from the Repository down to the DAO.