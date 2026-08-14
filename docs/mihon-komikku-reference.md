# Mihon & Komikku Feature Reference — GameVault Implementation Base

Reference document distilled from the Mihon and Komikku codebases (shallow clones at
`/data/data/com.termux/files/usr/tmp/opencode/ref/mihon` and `.../ref/komikku`, state as of 2026-08).
Purpose: base for implementing (1) library pull-to-refresh / library update and
(2) customizable backup creation in GameVault.

---

## 1. Library Pull-to-Refresh / Update Gesture

### 1.1 Shared pattern (both apps)

Both apps use the **same component and the same trick**:

- **Composable**: `PullRefresh` — thin wrapper over Material3 `pullToRefresh`
  (`presentation-core/src/main/java/tachiyomi/presentation/core/components/material/PullRefresh.kt`)
  - `rememberPullToRefreshState()` + `Modifier.pullToRefresh(isRefreshing, state, enabled, onRefresh)` in a `Box`, `PullToRefreshDefaults.Indicator` overlaid top-center.
  - Params: `refreshing: Boolean`, `enabled: Boolean`, `onRefresh: () -> Unit`, `indicatorPadding: PaddingValues`, `content`.
- **Fake spinner**: the indicator never tracks the real job. The refresh runs in a
  WorkManager worker, so the UI shows `isRefreshing = true; delay(1.seconds); isRefreshing = false`
  — a cosmetic 1-second spinner:
  - Mihon: `app/src/main/java/eu/kanade/presentation/library/components/LibraryContent.kt:85-90`
  - Komikku: same file, lines 96-101
  - `isRefreshing` is per-page state: `remember(pagerState.currentPage)`.
- **Disabled during multi-select**: `enabled = selection.isEmpty()`.
- **No on/off setting for the gesture** — always enabled otherwise.

### 1.2 Callback chain

1. Gesture completes → `PullRefresh.onRefresh` → `onRefresh()` (returns `Boolean`: did the job start?).
2. `LibraryTab` supplies: `onRefresh = { onClickRefresh(state.activeCategory) }` — **updates only the ACTIVE category/tab**.
   - Mihon: `app/src/main/java/eu/kanade/tachiyomi/ui/library/LibraryTab.kt:208` (handler `:95-106`)
   - Komikku: `:335` (handler `:121-144`)
3. `onClickRefresh` → `LibraryUpdateJob.startNow(context, category)` then snackbar
   (`update_already_running` / `updating_category` / `updating_library`).
   - The toolbar refresh icon uses the same handler; a global-update icon calls it with `null` (full library).
4. **`LibraryUpdateJob.startNow`** — WorkManager `OneTimeWorkRequestBuilder`, unique work
   `"LibraryUpdate-manual"`, tag `LibraryUpdate`, `ExistingWorkPolicy.KEEP`; returns `false` if already running.
   - Komikku passes extra group params (`group`, `groupExtra`) from the SY fork so the scope
     respects the active grouped tab (by source / track status / read status):
     `startNow(context, category, group=state.groupType, groupExtra=...)` (LibraryTab.kt:121-144);
     queue filtering at `LibraryUpdateJob.kt:224-379`.
   - Komikku also chains a sync job when sync is enabled (`SyncDataJob` → update, `:832-854`).
5. **Worker `doWork`** — `LibraryUpdateJob.kt`:
   - Mihon `:94-133`: sets last-updated timestamp, reads category (default -1 = whole library),
     runs `updateChapterList()` as a foreground service (progress notification, `FOREGROUND_SERVICE_TYPE_DATA_SYNC`).
   - Queue assembly `addMangaToQueue` (Mihon `:153-225`, Komikku `:224-379`):
     - category given → filter library by membership; else include/exclude by
       `libraryPreferences.updateCategories` / `updateCategoriesExclude` (empty = all).
     - **Skips** (via `autoUpdateMangaRestrictions` + update strategy): `ONLY_FETCH_ONCE` manga that
       already have chapters; completed manga; fully-read manga; never-started manga; outside release
       period vs fetch window. Defaults (Komikku): skips fully-read, non-completed, started, in-release-period.
   - Update loop `updateChapterList` (Mihon `:235-318`, Komikku `:389-502`): groups by source,
     `Semaphore(5)` → max 5 sources concurrently; per manga: re-check still favorite →
     `updateManga(manga, fetchWindow)` → `UpdateMangaFromRemote` interactor with
     `fetchDetails = autoUpdateMetadata` (default false), `fetchChapters = true`.
     - New chapters → filter for download → auto-download, bump `newUpdatesCount` badge,
       "new updates" notification; failures written to an error file/DB + error notification.
   - **No tracker sync** in Mihon's library update (trackers only on manga screen).

### 1.3 Settings governing the update (not the gesture)

Settings → Library → "Library update" group:
`app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsLibraryScreen.kt:112-238`
prefs in `domain/src/main/java/tachiyomi/domain/library/service/LibraryPreferences.kt`:

| Setting | Key | Default | Notes |
|---|---|---|---|
| Automatic updates interval | `pref_library_update_interval_key` | Off | schedules the periodic job; not the gesture |
| Device restrictions (WiFi/unmetered/charging) | `autoUpdateDeviceRestrictions` | WiFi | **auto runs only**; manual gesture ignores |
| Categories include/exclude | `library_update_categories` / `_exclude` | empty (all) | scope of full-library updates |
| Refresh metadata (fetch details) | `auto_update_metadata` | false | whether update also refreshes manga details |
| Smart update multiselect | `autoUpdateMangaRestrictions` | (see above) | skip completed/fully-read/not-started/out-of-period |
| Dynamic-category update mode (Komikku/SY) | `group_library_update_type` | GLOBAL | `GLOBAL / ALL_BUT_UNGROUPED / ALL` |
| Unread count badge | `library_show_updates_count` | true | new-update tab badge |

The same `PullRefresh` pattern is used on the Updates, Manga, Extensions and Feed screens
(with real state on the Manga screen: `viewModel.fetchAllFromSource`).

---

## 2. Backup Creation — Customizable

### 2.1 UI flow (identical in both apps)

- Entry: Settings → Data and storage → "Backup and restore" → segmented button **"Create backup"**
  (`SettingsDataScreen.kt` Mihon `:224-228` / Komikku `:261-302`) → navigates to **CreateBackupScreen**
  (a full screen, not a dialog):
  `app/src/main/java/eu/kanade/presentation/more/settings/screen/data/CreateBackupScreen.kt`
- Body: two `SectionCard`s — **"Library"** (`libraryOptions`) and **"Settings"** (`settingsOptions`);
  each entry is a `LabeledCheckbox(label, checked, onCheckedChange, enabled)`.
- Bottom action **"Create"**, enabled only when `state.options.canCreate()`.
- On click: guard `BackupCreateJob.isManualJobRunning` → SAF `CreateDocument("application/*")`
  with suggested name `BackupCreator.getFilename()` → take persistable URI permission →
  `viewModel.createBackup(context, uri)` → `BackupCreateJob.startNow(context, uri, options)` → pop screen.
- Screen state: `StateViewModel<State>` holding `options: BackupOptions`.

### 2.2 BackupOptions — full option list

**Mihon (10 fields)** — `app/src/main/java/eu/kanade/tachiyomi/data/backup/create/BackupOptions.kt`:

| # | Field | Default | Exact UI label | Enabled when |
|---|---|---|---|---|
| 0 | `libraryEntries` | true | Library entries | always |
| 1 | `categories` | true | Categories | always |
| 2 | `chapters` | true | Chapters | `libraryEntries` |
| 3 | `tracking` | true | Tracking | `libraryEntries` |
| 4 | `history` | true | History | `libraryEntries` |
| 5 | `readEntries` | true | All read entries | `libraryEntries` |
| 6 | `appSettings` | true | App settings | always |
| 7 | `extensionStores` | true | Extension stores | always |
| 8 | `sourceSettings` | true | Source settings | always |
| 9 | `privateSettings` | **false** | Include sensitive settings (e.g., tracker login tokens) | `appSettings \|\| sourceSettings` |

`canCreate()` = `libraryEntries || categories || appSettings || extensionStores || sourceSettings`.

**Komikku (12 fields)** — adds two, append-compatible slots:
`app/src/main/java/eu/kanade/tachiyomi/data/backup/create/BackupOptions.kt:9-147`

| # | Field | Default | Exact UI label | Enabled when |
|---|---|---|---|---|
| 10 | `customInfo` (SY) | true | Custom entry info | `libraryEntries` |
| 11 | `savedSearchesFeeds` (SY+KMK) | true | Saved Searches & Feeds | always |

Komikku `canCreate()` adds `savedSearchesFeeds` to the OR.

Section split: "Library" = 0-5 + 10; "Settings" = 6-9; Komikku shows 11 under Library.

**Serialization**: options travel through WorkManager input as a positional **`BooleanArray`**
(`asBooleanArray()` / `fromBooleanArray()`). **Append-only by design** — Komikku extended Mihon's
array by adding slots 10-11 without breaking compatibility.

### 2.3 Job and file writing

- `BackupCreateJob` (CoroutineWorker):
  - `doWork`: manual vs auto (auto uses default `BackupOptions()` — no customization for auto);
    target uri from input or automatic backups dir; foreground progress notification
    (`ID_BACKUP_PROGRESS`, `DATA_SYNC`); success → "backup complete" notification with file;
    failure → error notification + `Result.failure()`.
  - `startNow`: unique work `"BackupCreator:manual"`, `ExistingWorkPolicy.KEEP` (no-op if queued).
  - `setupTask`: periodic auto-backup, `requiresBatteryNotLow`, exponential backoff,
    interval from backup prefs (default **12 h**; options Off/6/12/24/48/168).
- `BackupCreator.backup(uri, options)`:
  - Manual: `UniFile.fromUri` of the SAF-created file.
  - Auto: keeps `MAX_AUTO_BACKUPS = 4`, deletes older `FILENAME_REGEX` matches, writes into `autobackup/`.
  - Assembly: `Backup(backupManga, backupCategories, backupSources, backupPreferences, backupSourcePreferences, backupExtensionStores [, backupSavedSearches, backupFeeds])`.
  - Write: `parser.encodeToByteArray(Backup.serializer(), backup)` (kotlinx-serialization **ProtoBuf**)
    → `file.openOutputStream().sink().gzip().buffer().write(...)` (**okio gzip**), truncating first.
  - Post-write validation via `BackupFileValidator` (decodes and reports missing sources /
    not-logged-in trackers); on exception deletes the file.

### 2.4 File format

- Extension **`.tachibk`**, filename `"{applicationId}_yyyy-MM-dd_HH-mm.tachibk"`.
- **Single gzip-compressed protobuf** — NOT a zip, NO JSON inside. Decoder sniffs gzip magic
  `0x1f8b`; legacy JSON signatures (`{}`/`{"`/`{\n`) are explicitly rejected.
- **No encryption / no password option** (the `privateSettings` checkbox only controls which
  prefs are included, not encryption). UI warns backups "may contain sensitive data".
- Proto field numbers: `1 backupManga`, `2 backupCategories`, `101 backupSources` (100 reserved
  for legacy broken sources), `104 backupPreferences`, `105 backupSourcePreferences`,
  `106 backupExtensionStores`; Komikku adds `600 backupSavedSearches` (SY) and
  `610 backupFeeds` (KMK) — **high numbers for fork-specific payloads = forward/backward compat pattern**.
- `BackupManga` carries: source/url/title/artist/author/description/genre/status/thumbnail/
  dateAdded/viewerFlags/chapterFlags, `chapters`, `categories` (**category order indices**, not ids),
  `tracking`, `history` (url + readAt + readDuration), updateStrategy, excludedScanlators, notes,
  initialized, memo (byte array).
- `BackupPreference` = `{1 key, 2 value}` with sealed variants Int/Long/Float/String/Boolean/StringSet;
  `BackupSourcePreferences` = `{1 sourceKey, 2 prefs}`.

### 2.5 Option → creator mapping

| Option | Creator | What it dumps |
|---|---|---|
| `libraryEntries` | `MangaBackupCreator` | per-manga BackupManga; chapters/categories/tracking/history only if their own options on; `readEntries` adds read-but-not-in-library entries (`getReadMangaNotInLibrary()`) |
| `categories` | `CategoriesBackupCreator` | all non-system categories (+ per-manga category order indices inside MangaBackupCreator) |
| `chapters` | inside MangaBackupCreator | all chapters |
| `tracking` | inside MangaBackupCreator | per-tracker sync rows |
| `history` | inside MangaBackupCreator | url/readAt/readDuration |
| (implicit) | `SourcesBackupCreator` | distinct source id+name of backed-up manga (always) |
| `appSettings` | `PreferenceBackupCreator.createApp` | all PreferenceStore values except `__APP_STATE_` keys; typed Int/Long/Float/String/Boolean/Set<String> |
| `sourceSettings` | `PreferenceBackupCreator.createSource` | per-ConfigurableSource prefs keyed by preferenceKey; empty ones filtered |
| `privateSettings` | filter inside both pref creators | strips `__PRIVATE_` keys (tracker tokens) when false |
| `extensionStores` | `ExtensionStoresBackupCreator` | all extension stores/repos |
| `customInfo` (Komikku) | inside MangaBackupCreator | SY custom entry info |
| `savedSearchesFeeds` (Komikku) | `SavedSearchBackupCreator` + `FeedBackupCreator` | all saved searches; all feed/saved-search entries |

Restore mirrors create 1:1 with `RestoreOptions` + `restore/restorers/*`.

---

## 3. Current GameVault State (what maps where)

| Feature | Mihon/Komikku | GameVault today |
|---|---|---|
| Pull-to-refresh in library | `PullRefresh` wrapper + fake 1s spinner + WorkManager fire-and-forget | **Not implemented** — no library update gesture |
| Update scope | active category or whole library; smart-update skips | N/A — GameVault library = saved games; update would re-scrape saved games' details/changelog (F95 `scrapeGame` per game) |
| Backup creation screen | full screen, 2 sections, checkboxes + Create via SAF | `GameVaultBackup.kt` — single JSON (Gson) with fixed fields; **no options UI** |
| Backup format | gzipped protobuf `.tachibk` | plain JSON; note: restore-tolerant for missing fields via Gson |
| GameVault per-game fields worth backing up | — | title, developer, publisher, engine, version, status, ratings, dates, notes, source_type, source_url, f95_url, changelog, devLinks, downloadLinks, tags, collections, routes, play sessions |

---

## 4. Recommended GameVault Implementation Path

### 4.1 Pull-to-refresh ("update library")
1. Adopt Material3 `pullToRefresh` via a small `PullRefresh` wrapper (copy the pattern).
2. On refresh: dispatch a fire-and-forget job (coroutine in a repository, no WorkManager needed at
   this scale) that re-scrapes saved games (`F95ZoneScraper.scrapeGame(url, cookie)` per game,
   limited concurrency ~3-5, refresh details + changelog + devLinks/downloadLinks + status).
3. Fake 1 s spinner while the job runs; badge/snackbar "Updating X games" on completion;
   skip logic: only games whose last check is older than the fetch window.
4. Settings: fetch-window interval + "refresh metadata" toggle are the highest-value knobs.

### 4.2 Customizable backup (Mihon-style)
1. Screen (or dialog) with two sections: **"Library"** (Library entries, Chapters/Changelog,
   History, Categories, Play sessions, Notes) and **"Settings"** (App settings, Source settings,
   Include sensitive settings — PREFER false).
2. `BackupOptions` data class serialized as positional `BooleanArray` (append-only).
3. Writer: single JSON is fine today (Gson already handles missing fields); if forward-compat is
   needed later, switch to gzipped protobuf with high field numbers.
4. SAF `CreateDocument` with generated filename `gamevault_yyyy-MM-dd_HH-mm.json`.
5. Restore screen mirrors options (`RestoreOptions`) — only restore what was backed up.

---

## 5. Key Source Paths (for re-checking)

- PullRefresh wrapper: `presentation-core/src/main/java/tachiyomi/presentation/core/components/material/PullRefresh.kt` (both repos)
- LibraryContent: `app/src/main/java/eu/kanade/presentation/library/components/LibraryContent.kt` (both)
- LibraryTab wiring: `app/src/main/java/eu/kanade/tachiyomi/ui/library/LibraryTab.kt` (Mihon :95-106,208; Komikku :121-144,335)
- LibraryUpdateJob: `app/src/main/java/eu/kanade/tachiyomi/data/library/LibraryUpdateJob.kt`
- Backup options: `app/src/main/java/eu/kanade/tachiyomi/data/backup/create/BackupOptions.kt`
- Create screen: `app/src/main/java/eu/kanade/presentation/more/settings/screen/data/CreateBackupScreen.kt`
- BackupCreator: `app/src/main/java/eu/kanade/tachiyomi/data/backup/create/BackupCreator.kt`
- Creators: `app/src/main/java/eu/kanade/tachiyomi/data/backup/create/creators/*.kt`
- Proto models: `app/src/main/java/eu/kanade/tachiyomi/data/backup/models/Backup.kt` (+ BackupManga.kt, BackupPreference.kt)
- Decoder: `app/src/main/java/eu/kanade/tachiyomi/data/backup/BackupDecoder.kt`
- Backup prefs: `domain/src/main/java/tachiyomi/domain/backup/service/BackupPreferences.kt`