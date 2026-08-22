# Mihon & Komikku Complete Feature Reference — GameVault Adaptation Base (v2)

Distilled from shallow clones `/data/data/com.termux/files/usr/tmp/opencode/ref/{mihon,komikku}` (state 2026-08).
Supersedes the previous 2-feature reference; integrates its backup deep-dive. Purpose: full inventory of BOTH apps' features with exact paths/settings keys, plus the GameVault adaptation map (§16).

---

## 1. Library screen & management

- Display modes — CompactGrid / ComfortableGrid / List / CoverOnlyGrid("cozy"); grid density portrait/landscape auto=0 — `domain/.../library/model/LibraryDisplayMode.kt` — `pref_display_mode_library`
- Category tabs — system All + user categories pager; item counts toggle; restores last tab — `ui/library/LibraryTab.kt`, `LibraryTabs.kt` — `display_category_tabs=true`, `display_number_of_items=false`
- Per-category display settings — filter/sort/display per category w/ global fallback — `SetSortModeForCategory.kt`, `SetDisplayMode.kt` — `categorized_display=false`
- Sort modes — Alphabetical, LastRead, LastUpdate, UnreadCount, TotalChapters, LatestChapter, ChapterFetchDate, DateAdded, TrackerMean, Random(seeded) ± Asc/Desc; stored in Category.flags — `LibrarySortMode.kt`, applySort in `LibraryViewModel.kt` — `library_sorting_mode=Alphabetical/Asc`; NOTE: no manual drag-order sort upstream
- Filter sheet — tri-state chips Downloaded/Unread/Started/Bookmarked/Completed/IntervalCustom + per-tracker — `LibrarySettingsDialog.kt` — keys `pref_filter_library_*_v2` all DISABLED
- Cover badges — download(false)/unread(true)/local(true)/language(false) counts — `LibraryViewModel.getFavoritesFlow` — `display_{download,unread,local,language}_badge`
- Query DSL search — toolbar text → AST: fields title/author/artist/description/genre/source/notes/language/source_id; comparisons unread/read/total/id/added/fetchinterval/nextupdate with >,<,>=,<=,=; AND/OR/NOT — `mihon/domain/library/model/search/*`, matcher `QueryNodeExtensions.kt`
- Selection mode — tap select, long-press RANGE select, all/invert — `LibraryViewModel.toggleRangeSelection`
- Bulk actions — download presets(1/5/10/25/unread/bookmarked), mark read/unread, change categories tri-state preselect — same VM
- Toolbar — refresh active category vs global update; open RANDOM entry in category — `LibraryTab.kt`
- Continue-reading overlay button on covers (both apps) — `display_continue_reading_button=false`
- Category CRUD + drag reorder — `ui/category/CategoryScreen.kt`, `ReorderCategory.kt`

## 2. Global search & browsing

- Unified search — parallel query of enabled sources (5-thread pool), grouped per source pinned-first, progress n/total, error cards — `globalsearch/SearchViewModel.kt`, `GlobalSearchViewModel.kt`
- Scope filters — All vs PinnedOnly; per-extension filter; hide-empty-results toggle
- Source browse — Popular/Latest/Search tabs, dynamic FilterList dialog, genre chips — `BrowseSourceViewModel.kt`, `SourceFilterDialog.kt`
- In-app WebView of source site — `BrowseSourceScreen.kt`
- Duplicate check on add — normalized-title match → confirm dialog — `GetDuplicateLibraryManga.kt`
- Migration shortcut from long-press result — `SearchViewModel.Dialog.Migrate`
- Upcoming feed — expected next-release dates grouped calendar-style — `mihon/feature/upcoming/UpcomingScreen.kt`

## 3. Sources & extensions

- Extension stores/repos — add/remove/refresh URLs, deeplink install — `ExtensionStores{Screen,ViewModel}.kt` — `extension_repos`
- Lifecycle — PackageInstaller/Shizuku install, in-process APK load, update-all badge — `ExtensionLoader.kt`, `ExtensionManager.kt`
- Trust system — untrusted signature → prompt; trust=pkgName+versionCode+SHA256 — `TrustExtension` — appState `trusted_extensions`
- NSFW gate — ext metadata flag; hide behind switch — `show_nsfw_source=true`
- Languages/pin/hide — `source_languages`, `hidden_catalogues`, `pinned_catalogues`
- Incognito per-extension — `incognito_extensions`
- Migration flow — entry points from sources/manga/search; multi-target config w/ drag priority, optional deep search, candidate scoring by chapter count, hide unmatched/no-updates; flags bitmask (CHAPTER/CATEGORY/CUSTOM_COVER/NOTES/REMOVE_DOWNLOAD all on) — `mihon/feature/migration/*`, `MigrateMangaUseCase.kt` — `migration_flags`, `migration_deep_search=false`

## 4. Updates

- Periodic job — WorkManager periodic (Off/12/24/48/72h/weekly), foreground progress notification — `data/library/LibraryUpdateJob.kt` — `pref_library_update_interval_key=0`
- Device restrictions — wifi/unmetered/charging; battery-not-low always — `library_update_restriction={wifi}`
- Smart skips — completed, fully-read, not-started, outside release window, fetch-once strategy — `library_update_manga_restriction={ongoing,fully_read,started,outside_release_period}`
- Adaptive fetch interval — learns per-manga cadence from upload history; drives release window + Upcoming screen — `FetchInterval.kt`
- Category include/exclude tri-state — `library_update_categories/_exclude`
- Metadata refresh during update — `auto_update_metadata=false`, title refresh false
- Notifications — grouped per-manga new-chapter notifs + summary; failure notif w/ shareable error log; queue-size warning — `LibraryUpdateNotifier.kt`
- Unseen badge — counter incremented on new chapters, reset opening Updates tab — `library_unseen_updates_count`
- Updates screen — date headers (last 3 months), range selection, live download status per row, actions (download/start-now/cancel/delete/mark read/bookmark), filter sheet tri-state + categories — `UpdatesViewModel.kt`, `UpdatesFilterDialog.kt`

## 5. Downloads

- Queue screen — source-grouped, per-item page progress, FAB pause/resume, cancel-all — `DownloadQueue{Screen,ViewModel}.kt`
- Reorder — drag-drop; move top/bottom item-or-series; sort by upload/chapter — `reorderQueue`
- Queue survives process death — `DownloadStore.kt`
- Concurrency — parallel sources 1-10 (=5), pages per download 1-15 (=5) — `download_parallel_{source,page}_limit`
- Network gate — wifi-only — `download_only_over_wifi=true`
- Formats — CBZ archives(true)/folders, split tall images — `DownloadPreferences.kt`
- Auto-download — while reading ahead (0/2/3/5/10 unread); on library update (`download_new=false`) + unread-only + category sets — `FilterChaptersForDownload`
- Auto-delete — after marked read(false), after Nth-to-last slot(-1), keep bookmarked(false)+excluded categories
- Offline — `DownloadCache.kt`, global downloaded-only mode `pref_downloaded_only=false`, pending deleter batching

## 6. Reader

Core prefs: `ReaderPreferences.kt`.
- Modes — LTR/RTL/Vertical pagers, Webtoon continuous, Vertical+ snapping; per-manga override — `ReadingMode.kt` — default RTL(2)
- WebGPU high-quality renderer gate — `pref_high_quality_renderer_key=false`
- Orientation lock free/portrait/landscape/reverse — `ReaderOrientation.kt`
- Tap zones — 5 layouts + invert + visual overlay tutorial — `viewer/navigation/*` — `reader_navigation_mode_*=0`
- Volume-key paging ±invert — `reader_volume_keys=false`
- Scale types — Fit/Stretch/FitW/FitH/Original/SmartFit — `pref_image_scale_type_key=1`; zoom start position
- Crop borders paged/webtoon quick-toggle — false/false; landscape zoom, navigate-on-pan true
- Dual-page — split(_webtoon)/rotate/force NEVER, invert variants — all false
- Overlays — custom brightness −75..100 scrim; RGB color filter + blend modes; grayscale; inverted colors — `ReaderContentOverlay.kt`
- Theme Black/Gray/White/Automatic — `pref_reader_theme_key=1`; flash-on-page-change(duration/interval/color)
- Fullscreen/cutout/keep-screen-on — true/true/false
- Chrome — top bar (title/bookmark/webview/share), bottom bar (mode/orientation/crop quick-switch), slider navigator horizontal-or-vertical-edge — `ChapterNavigator.kt`
- Chapter transitions — prev/next interstitials w/ preview + download-next prompt + gap warning — `ChapterTransition.kt` — `always_show_chapter_transition=true`
- Skip options at transitions — read(false)/filtered(true)/dupe(false) — ReaderViewModel chapterList builder
- On finish — mark read + duplicate-numbered cleanup, auto-download ahead, delete-after-N-slots — `updateChapterProgressOnComplete:590`, `downloadNextChapters:496`
- History timer per session (skipped incognito) — `restartReadTimer/updateHistory:614`
- Track push on finish — `updateTrackChapterRead:940` — `pref_auto_update_manga_sync_key=true`
- Per-page actions — save/share/copy/set-as-cover — `ReaderPageActionsDialog.kt`

## 7. History

- FastScroll list, relative-date headers (Today/Yesterday/date) — `HistoryScreen.kt`
- Resume point — next unread else last-read continuation — `GetNextChapters.kt`; global most-recent shortcut in tab
- Search by title (SQL LIKE); reset-all sweep w/ confirm; remove single/whole-manga; add-to-library from history w/ duplicate check — `HistoryViewModel.kt`
- Row model stores last_read + time_read accumulated ms — `history.sq`

## 8. Statistics

- Text StatItem cards, NO charts — Overview(library size, completed, total read time SUM(history.time_read)) / Titles(update-eligible, started, local) / Chapters(total/read/downloaded) / Trackers(tracked count, mean score normalized 10pt) — `StatsViewModel.kt`, `GetTotalReadDuration.kt`

## 9. Tracking

- 11 trackers MAL/AniList/Kitsu/Shikimori/Bangumi/Komga/MangaUpdates/Kavita/Suwayomi/Hikka/MangaBaka — `TrackerManager.kt`, contract `Tracker.kt`
- OAuth browser redirect login; Kitsu/MU user-pass; self-hosted URL+creds — `BaseOAuthLoginActivity.kt`
- Enhanced trackers auto-match silently on add-to-library — `EnhancedTracker.kt`, `AddTracks.bindEnhancedTrackers`
- Push per finished chapter w/ delayed retry WorkManager job — `TrackChapter.kt`, `DelayedTrackingUpdateJob.kt`
- On mark-as-read ALWAYS/ASK(snackbar)/NEVER — `pref_auto_update_manga_on_mark_read=ALWAYS`
- Search binding dialog + status/score/dates editor — `TrackInfoDialog*.kt`
- Score formats per tracker (POINT_100/10/5, STEP_N) — `TrackPreferences.kt`
- Refresh reconciles two-way (local chapters ≤ remote read) — `RefreshTracks.kt`

## 10. Manga detail screen

- Info header — cover dialog(pinch zoom/share/save/edit/delete custom), expandable markdown description, genre chips tap=search long-press=copy — `MangaInfoHeader.kt`, `MarkdownRender.kt`, `MangaCoverDialog.kt`
- Action row — favorite(+category picker+duplicate warn), webview, tracking chip, edit categories, edit predicted next-update, countdown — `MangaActionRow:174`
- Resume FAB Start/Resume → next unread — `MangaScreen.kt:327`
- Chapters — filters TriState unread/downloaded/bookmarked + scanlator multi-select; sort Source/Number/Upload/Alpha ±asc; display name-or-number; "set as default"; missing-chapters gap row; multi-select batch menu; inline download state machine icons — `ChapterSettingsDialog.kt`, `MangaBottomActionMenu.kt`, `ChapterDownloadIndicator.kt`
- Notes tab/editor per manga — `MangaNotesScreen.kt`, `UpdateMangaNotes.kt`

## 11. Backup & restore

- CreateBackupScreen — full screen, SectionCards Library/Settings, LabeledCheckbox entries, Create via SAF CreateDocument(`application/*`) suggested name — `CreateBackupScreen.kt`
- BackupOptions — Mihon 10 fields / KMK 12 (append slots 10-11): libraryEntries, categories, chapters, tracking, history, readEntries, appSettings, extensionStores, sourceSettings, privateSettings(FALSE=sensitive/tokens stripped), +customInfo, +savedSearchesFeeds; `canCreate()` OR-guard; serialization positional BooleanArray APPEND-ONLY — `BackupOptions.kt`
- BackupCreateJob — CoroutineWorker foreground progress notif; manual unique `"BackupCreator:manual"` KEEP; success/failure notifs; auto uses DEFAULT options — `BackupCreateJob.kt`
- BackupCreator — MAX_AUTO_BACKUPS=4 deletes older FILENAME_REGEX in autobackup dir; assembly Backup(...); write kotlinx ProtoBuf → okio gzip `.tachibk`; post-validate via BackupFileValidator(delete on exception) — `BackupCreator.kt`
- Format — single gzipped protobuf `{appId}_yyyy-MM-dd_HH-mm.tachibk`; gzip magic sniff; legacy JSON rejected; NO encryption (privateSettings only scopes content); proto numbers 1 manga,2 categories,101 sources,104 prefs,105 sourcePrefs,106 extStores,600 savedSearches(KMK),610 feeds(KMK); BackupManga carries category ORDER INDICES not ids; BackupPreference sealed typed {key,value} — `models/Backup*.kt`
- Restore — RestoreOptions mirror (5: libraryEntries/categories/appSettings/extensionStores/sourceSettings) + RestoreBackupScreen; restore job foreground notif + error log; restorers mirror creators 1:1 — `RestoreOptions.kt`, `RestoreBackupScreen.kt`, `restore/restorers/*`
- Preferences dump — createApp all PreferenceStore minus `__APP_STATE_`; createSource per-source keyed; private strips `__PRIVATE_` — `PreferenceBackupCreator.kt`
- Auto-backup — periodic setupTask requiresBatteryNotLow exponential backoff, interval Off/6/12/24/48/168 default **12h** — `BackupPreferences.backup_interval=12`

## 12. Sync & connections (Komikku only)

- 3 backends WebDav / Google Drive / SyncYomi — `data/sync/service/*.kt`, absent in Mihon
- Prefs — host/apikey/interval/service + triggers (on chapter read/open/app start/resume, all false) + per-domain payload toggles (all true incl. privateSettings/customInfo/savedSearchesFeeds) — `SyncPreferences.kt`
- UI — SettingsConnectionScreen + SyncSettingsSelector + trigger options screens
- Discord Rich Presence — RPC status/incognito/buttons/timestamps — `ConnectionsPreferences.kt` — `pref_enable_discord_rpc=false`

## 13. Settings app & security

- Sections — Appearance / Library / Reader / Downloads / Tracking / Browse / Data&storage / Security&privacy / Advanced (+about/debug dirs) — `presentation/more/settings/screen/Settings*Screen.kt`
- Appearance — theme system light/dark/auto-dark, palettes, AMOLED black, custom theme color picker (KMK `AppCustomThemeColorPickerScreen.kt`), date relative format, app language
- Security — biometric app lock (`use_biometric_lock=false`), secure-screen block screenshots ALWAYS/INCOGNITO(`secure_screen_v2=INCOGNITO`); KMK per-category biometric times — `BiometricTimesScreen.kt`
- Data&storage — storage locations, cache locations per-type clear, disk stats — `StorageLocationScreen.kt`
- Advanced — clear cookies/cache/webview data, crash logs ACRA-style debug info, analytics opt-in toggle (`privacyPreferences.analytics` referenced in App.kt:161)

## 14. Platform misc

- Notification channels (~10): common, library progress/errors, downloader progress/errors, new chapters, backup/restore progress/complete, incognito mode, extensions update — `data/notification/Notifications.kt`
- Launcher shortcuts xml + DeepLinkActivity (search/add URLs) — `AndroidManifest.xml`, `ui/deeplink/DeepLinkActivity.kt`
- Onboarding first-run flow — `presentation/more/onboarding/*` — `onboarding_complete`
- New-update changelog screen (GitHub release fetch) — `NewUpdateScreen.kt`, `GetApplicationRelease`
- Whats-new / Coming-updates screens (KMK) — `WhatsNewScreen.kt`, `ComingUpdatesScreen.kt`
- Library-update errors dedicated screen (KMK) — `LibraryUpdateErrorScreen.kt`

## 15. Komikku deltas index

Data Saver(recompression NONE/BANDWIDTH_HERO/WSRV_NL + excluded sources) · Saved Searches & Feeds(+screens+backup 600/610) · Server sync trio · Update group types GLOBAL/ALL_BUT_UNGROUPED/ALL · library grouping by tag + sort tags CRUD · merged/multi-source manga(SmartSearchMerge,MergedSource) · Related mangas row · Page previews · EHentai enhanced-source stack(source-api exh/**, SettingsEhScreen) · reader extras(page_layout,invert_double_pages,smooth_auto_scroll,bottom_buttons,seekbar variants,EH caching keys) · library update errors screen · Discord RPC · custom theme picker · misc prefs(fetch_metadata_on_add,hide_hidden_categories,sources_tab_categories)

## 16. GameVault adaptation map

### 16.1 Already implemented (do NOT redo)
- Pull-to-refresh concept → auto-update checks worker (b0d7967): interval Off/6/12/24/48/168 default 12h, fetch-window skip, lastChecked column (DB v8)
- Customizable backup (575bb46): group gates wantLibrary/wantCollections/wantHistory/wantTags/wantAppSettings, JSON Gson, SAF export
- Auto-backup rotation (a05717d): periodic worker, tmp+rename atomicity, keep-newest-N, reactive schedule/cancel
- Route-linked play sessions + real History + backup routeIndex round-trip (dc2baa4)

### 16.2 Coherent candidates (adaptation queue)
| Priority | Feature | Source § | Adaptation | Size |
|---|---|---|---|---|
| 1 | Unseen-updates badge + Recently Updated sort | §4 | count games w/ detected newer version unseen; sort axis on lastChecked/version-detected | S-M |
| 2 | RestoreOptions UI parity | §11 | selective-import checkboxes exposing existing group gates | S-M |
| 3 | Open random entry | §1 | toolbar action random in-library game | S |
| 4 | Per-entry update mute | §4/§10 | boolean column per game silencing worker notifications | M(migration v9) |
| 5 | Statistics screen | §8 | cards: total playtime, library size, routes completed, sessions week, top-by-playtime | M |
| 6 | Duplicate detection on add | §2 | f95_url already-in-library confirm dialog | S |
| 7 | Bulk selection actions | §1 | multi-select → add to collection/tag | M |
| 8 | Adaptive fetch interval | §4 | learn per-game release cadence → prioritize checks | L |
| 9 | Biometric app lock | §13 | SecurityScreen addition, F95-content privacy | S-M |
| 10 | Upcoming/expected updates feed | §2 | depends on #8 | M |

### 16.3 Discarded (no coherent mapping)
Reader internals, downloads/chapters machinery, extensions/repos, trackers OAuth suite, cross-source migration, Data Saver, merged manga, feeds/saved-searches, Discord RPC, page previews.
