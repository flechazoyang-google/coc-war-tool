# 依赖与 SDK 升级记录

> 状态：**已执行（2026-08，联网环境）**。目标 SDK 35 与主要依赖已升级并通过编译 + 131 单元测试 + lint 门禁。

## 已升级版本（`gradle/libs.versions.toml`）

| 组件 | 升级前 | 升级后 | 说明 |
|---|---|---|---|
| compileSdk / targetSdk | 34 | **35** | 满足 Google Play 2025-08 起 targetSdk 35+ 要求 |
| AGP | 8.7.0 | **8.9.2** | 需 Gradle 8.11.1+ |
| Gradle (wrapper) | 8.10.2 | **8.11.1** | wrapper `distributionUrl` 同步 |
| Kotlin (+ compose 插件) | 2.0.21 | **2.1.21** | 编译器与 Compose 插件版本必须一致 |
| KSP | 2.0.21-1.0.28 | **2.1.21-2.0.2** | 与 Kotlin 2.1.21 配套 |
| Compose BOM | 2024.10.01 | **2025.06.01** | material3 由 BOM 管理（1.3.2），移除显式覆盖 |
| Room | 2.6.1 | **2.7.2** | 数据库 schema 迁移链（1→2→3→…）未变 |
| core-ktx（解除 constraints 锁） | 1.13.1 | **1.16.0** | 与 compileSdk 35 配套，删除 constraints 块 |
| activity-compose | 1.9.3 | **1.10.1** | — |
| lifecycle | 2.8.7 | **2.9.0** | — |
| navigation-compose | 2.8.4 | **2.9.0** | — |
| coroutines-android | 1.8.1 | **1.10.2** | — |
| security-crypto | 1.1.0-alpha06 | **移除** | 替换为自实现 `SecurePrefs`(AndroidKeyStore + AES/GCM),见下 |

## 升级步骤回顾（供其他环境复现）

1. SDK 组件：`sdkmanager "platforms;android-35" "build-tools;35.0.0"`
   （注意设置 `ANDROID_USER_HOME` 为可写目录，否则 manifest 缓存写入只读的 `~/.android` 会失败）
2. 改 `gradle/libs.versions.toml` + `COCtools/build.gradle.kts`（compileSdk/targetSdk 35、删除 core constraints）
3. `gradle/wrapper/gradle-wrapper.properties`：`gradle-8.11.1-bin.zip`
4. `./gradlew :COCtools:compileDebugKotlin` 验证 → 全量测试 → lint

## 已知待办

- **security-crypto 已替换（2026-08）**：`data/sync/SecurePrefs.kt` 用 AndroidKeyStore + AES/GCM
  自实现加密存储（密文格式 `Base64(IV):Base64(ciphertext)`），移除 `androidx.security:security-crypto`
  （Google 已停维护）。存量兼容：旧 security-crypto 密文无法解密 → 密码返回空，用户重输后覆盖；
  老明文 `coc_webdav_prefs.webdav_pass` 自动迁移到新加密存储。**加解密往返需真机验证**（AndroidKeyStore
  依赖设备，本地 JVM 不可测）。
- 升级后新出现的 deprecation 警告（`EventListScreen` 的 `LocalClipboardManager` → `LocalClipboard`、
  `WarJsonParser.setLenient`、服务类 `TYPE_PHONE`/`getRealMetrics`）为既有代码对新 API 的适配，非阻塞。

## 回归清单（升级后）

- [x] `./gradlew :COCtools:testDebugUnitTest` 全绿（131 用例）
- [x] `./gradlew :COCtools:lintDebug` 通过（abortOnError 门禁）
- [x] `./gradlew :COCtools:compileDebugKotlin` 通过
- [ ] 真机回归：备份导出→导入往返、WebDAV 同步、悬浮球截图与权限引导、设置-更新测试计划开关
