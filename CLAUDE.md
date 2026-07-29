# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build

```bash
# Set JDK from Android Studio
export JAVA_HOME='<Android Studio path>/jbr'
export PATH="$JAVA_HOME/bin:$PATH"

# Build debug APK (module is :COCtools, NOT :app)
./gradlew :COCtools:assembleDebug

# Or via direct Gradle binary (if wrapper unavailable)
GRADLE='/c/Users/flechazo/.gradle/wrapper/dists/gradle-8.9-bin/<hash>/gradle-8.9/bin/gradle'
"$GRADLE" :COCtools:assembleDebug --no-daemon
```

APK output: `COCtools/build/outputs/apk/debug/COCtools-debug.apk`

There are no tests.

## Architecture

This is a Clash of Clans war/league data management Android app (Kotlin 2.0.21, JVM 21, Jetpack Compose + Material 3, Room + KSP, Gson, MVVM + Repository, no DI framework).

**Entry point chain**: `CocWarApplication` (holds lazy `WarDatabase` + `WarRepository` singletons) → `MainActivity` (NavHost with bottom navigation).

**Package layout** (`com.cocwar`):

| Package | Role |
|---------|------|
| `data/db/WarDatabase.kt` | Room DB (v5), `WarDao`, `RosterDao`, entities (`WarEventEntity`, `MemberEntity`, `MemberRosterEntity`), `Converters`, migrations |
| `data/model/WarModels.kt` | DTOs with nullable fields for lenient JSON parsing + domain models (`Attack`, etc.) |
| `data/parser/WarJsonParser.kt` | `Gson`-based parser: JSON → `ParseResult.Success(ParsedEvent)` or `.Error(msg)`. Never throws on missing keys. |
| `data/repository/WarRepository.kt` | CRUD, sample data (`ensureSamples`/`restoreSamples`), JSON export/import, event name generation, roster management |
| `data/samples/SampleDataProvider.kt` | Built-in 30-player war + 15-player league sample data |
| `data/sync/` | WebDAV cloud sync (`WebDavClient`, `SyncConfig`) |
| `data/ai/` | AI vision recognition via OkHttp (`AiConfig`, `AiPrompts`, `AiService`). API keys stored with `security-crypto`. |
| `data/update/UpdateChecker.kt` | Version update check |
| `domain/StatsCalculator.kt` | Pure functions: `compute()` → `WarStats`, `computeMonthly()` → `List<MemberMonthlyStat>`, `computeRecentMissed()` → `List<RecentMissedRank>` |
| `di/WarViewModel.kt` | `@Composable warViewModel { repo -> SomeViewModel(repo) }` — hand-rolled ViewModel factory, scoped to NavBackStackEntry |
| `service/` | `FloatingBallService` (foreground service + overlay ball for screenshot trigger) + `ScreenCaptureService` (accessibility service for screen capture) |
| `ui/MainActivity.kt` | Bottom nav (战报/统计/成员/设置) + NavHost with routes: `event_list`, `detail/{eventId}`, `import`, `stats`, `member_manage`, `sync`, `ai_config`, `ai_import` |
| `ui/eventlist/` | Event list screen + ViewModel |
| `ui/importflow/` | Import screen (`ImportScreen`, `ImportForm`, `ImportViewModel`) + AI import (`AiImportScreen`) |
| `ui/detail/` | Event detail with Overview/Stats/Members tabs + ViewModel |
| `ui/members/` | Roster management screen + ViewModel |
| `ui/stats/` | Monthly stats screen + ViewModel |
| `ui/sync/` | WebDAV sync screen + ViewModel |
| `ui/settings/` | AI config screen |
| `ui/components/Components.kt` | Reusable: `SectionTitle`, `InfoRow`, `StatTile`, `TypeBadge`, `RoleBadge`, `AttackStatusChip` |
| `ui/util/Labels.kt` | Chinese label helpers: `eventTypeLabel`, `roleLabel`, `resultLabel`, `formatPercent` |
| `ui/util/StringMatcher.kt` | Fuzzy string matching for roster name suggestions |
| `ui/theme/` | `CocWarTheme` (Material 3, dynamic color on Android 12+) |

**Key data flow**: User imports JSON → `WarJsonParser.parse()` → `WarRepository.importEvent()` → Room DB → Flows observed by ViewModels → Compose UI.

**Event naming**: Auto-generated as `SAABBCC` format (S=0 for war/1 for league, AA=year, BB=month, CC=sequence). Parsed back by `WarRepository.parseTypeAndRound()`.

## Conventions

- **Module name is `:COCtools`**, not `:app`. All old docs referencing `:app:assembleDebug` or `app-debug.apk` are wrong.
- Kotlin: `kotlin.code.style=official`, 4-space indent.
- Room uses KSP (`ksp("androidx.room:room-compiler:2.6.1")`), never kapt.
- JSON parsing: DTO fields are all nullable with safe defaults. `WarJsonParser` is lenient.
- Error handling: `ParseResult` sealed interface (Success/Error). Repository operations throw on DB errors (no try/catch in repo).
- ViewModels: Simple classes taking `WarRepository` in constructor. Use `warViewModel { repo -> ... }` instead of `ViewModelProvider.Factory`.
- UI: All user-facing strings are Chinese; code identifiers are English.
- Navigation: `rememberNavController()` → `NavHost` with string routes. Bottom bar uses `popUpTo + saveState/restoreState`.
- `compileSdk = 34`, `core-ktx` pinned to `1.13.1` (Compose BOM 2024.10.01 otherwise pulls 1.15.0 requiring compileSdk 35).
- Dependency repos: Aliyun mirrors first in `settings.gradle.kts` for offline/domestic builds.
- No proxy: `gradle.properties` explicitly clears `http.proxyHost`/`https.proxyHost`.
