# CLAUDE.md

Guidance for AI agents working in this repository. Repo root is `coc-war-tool/`; the
build toolchain lives one level up in the workspace at `../.toolchain/` (SDK + JDK + Gradle).

## Commands

No `gradlew` wrapper / `gradle/wrapper/` in-tree — always use the toolchain Gradle binary:

```bash
export JAVA_HOME=/home/ygh/projects/coc/.toolchain/jdk-21.0.12+8
export PATH="$JAVA_HOME/bin:$PATH"
export GRADLE_USER_HOME=/home/ygh/projects/coc/.toolchain/gradle-home
GRADLE=/home/ygh/projects/coc/.toolchain/gradle-8.10.2/bin/gradle

# Build debug APK (module is :COCtools, NOT :app)
"$GRADLE" :COCtools:assembleDebug

# Unit tests (plain JUnit, pure-logic only)
"$GRADLE" :COCtools:testDebugUnitTest
```

- APK output: `COCtools/build/outputs/apk/debug/COCtools-debug.apk`
- Android SDK at `../.toolchain/android-sdk` (already set in `local.properties` `sdk.dir`).
- Current version: v4.3 (versionCode 21). Releases tagged `vX.Y` in git; changelog in `releases/RELEASE_LOG.md`.

## Architecture

Clash of Clans war/league data-management Android app (Kotlin 2.0.21, JVM 21, Jetpack Compose + Material 3, Room + KSP, Gson, MVVM + Repository, no DI framework).

**Entry point chain**: `CocWarApplication` (lazy `WarDatabase` + `WarRepository` singletons) → `MainActivity` (NavHost + bottom nav).

**Package layout** (`com.cocwar`):

| Package | Role |
|---------|------|
| `data/db/WarDatabase.kt` | Room DB (**v6**), `WarDao`, `RosterDao`, entities, `Converters`, migrations |
| `data/model/WarModels.kt` | DTOs (nullable fields, lenient) + domain models |
| `data/parser/WarJsonParser.kt` | Gson JSON → `ParseResult.Success(ParsedEvent)` / `.Error(msg)`; never throws on missing keys; fills unused-attack placeholders |
| `data/repository/WarRepository.kt` | CRUD, samples, JSON export/import, SAABBCC event-name generation, roster management |
| `data/migrate/DataMigrator.kt` | League event-name migration fix (旧编码 → 新编码) |
| `data/csv/` | CSV codec/export/import (`CsvCodec`, `CsvExporter`, `CsvImporter`) |
| `data/samples/SampleDataProvider.kt` | Built-in sample war + league data |
| `data/sync/` | WebDAV sync (`WebDavClient`, `SyncConfig`, `SyncDecider`) |
| `data/update/UpdateChecker.kt` | Version update check |
| `domain/StatsCalculator.kt` | Pure stat functions (`compute`, `computeMonthly`, `computeTopMembers`, `computeRecentMissed`, …) — 口径 defined in `docs/RULES.md` |
| `di/WarViewModel.kt` | `@Composable warViewModel { repo -> ViewModel(repo) }` factory scoped to NavBackStackEntry |
| `service/` | `FloatingBallService` (foreground service + overlay ball) + `ScreenCaptureService` (accessibility service) |
| `ui/MainActivity.kt` | Bottom nav (`event_list` / `stats` / `member_manage` / `tools`) + routes: `event_list`, `league_season/{year}/{month}/{match}`, `detail/{eventId}`, `import`, `stats`, `member_manage`, `sync`, `tools` |
| `ui/eventlist/` `ui/detail/` `ui/importflow/` `ui/stats/` `ui/members/` `ui/season/` `ui/sync/` `ui/tools/` | Feature screens + ViewModels (season = CWL 7-round aggregate view; tools = export / screenshot gallery / migration repair; plus `ui/ClipboardImportDialog.kt`) |
| `ui/components/Components.kt` | Reusable composables (`SectionTitle`, `InfoRow`, `StatTile`, badges, …) |
| `ui/util/Labels.kt` `StringMatcher.kt` | Chinese label helpers; fuzzy roster name matching |
| `ui/theme/` | `CocWarTheme` (Material 3, dynamic color on Android 12+) |

**Key data flow**: import JSON → `WarJsonParser.parse()` → `WarRepository.importEvent()` → Room → Flows → ViewModels → Compose.

**Event naming**: `SAABBCC` (S=0 war / 1 league, AA=year, BB=month, CC=sequence; league C1C2 encodes match+round). Parsed by `WarRepository.parseTypeAndRound()`.

## Conventions

- **Module is `:COCtools`**, not `:app` — old docs saying `:app:assembleDebug` / `app-debug.apk` are wrong.
- **`docs/RULES.md` is the authoritative source** for 统计口径 / naming / edge cases: changing any 口径 requires updating RULES.md first, then code (`domain/StatsCalculator.kt`, `data/parser/WarJsonParser.kt`, `data/repository/WarRepository.kt`, `ui/util/Labels.kt`). `docs/ROADMAP.md` lists deferred features — don't start one without aligning 口径 in RULES.md.
- Kotlin `kotlin.code.style=official`, 4-space indent. UI strings are Chinese; identifiers English.
- Room uses KSP (never kapt). `compileSdk = 34`; `core-ktx` pinned to 1.13.1 (Compose BOM 2024.10.01 otherwise pulls 1.15.0, which needs compileSdk 35). `material3` pinned to 1.3.1 (PullToRefreshBox indicator fix, b/343505109).
- JSON: DTO fields nullable with safe defaults; parser never throws. Errors via `ParseResult` sealed interface; repository throws on DB errors (no try/catch in repo).
- ViewModels: plain classes taking `WarRepository`; use `warViewModel { repo -> … }` instead of `ViewModelProvider.Factory`.
- Navigation: `rememberNavController()` + string routes; bottom bar uses `popUpTo` + `saveState/restoreState`.
- Dependency repos: Aliyun mirrors first (`settings.gradle.kts`); `gradle.properties` clears proxy settings. WebDAV password stored via `security-crypto` (EncryptedSharedPreferences).
- Tests: plain JUnit (no Android framework), pure logic only — `StatsCalculatorTest`, `WarJsonParserTest`, `CsvTest`, `SyncDeciderTest`, `LabelsTest` under `COCtools/src/test/`.

## Notes

(留空 — 后续补充)
