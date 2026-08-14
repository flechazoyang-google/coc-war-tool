# COC 部落战/联赛数据管理工具

部落冲突（Clash of Clans）部落战与联赛数据管理 Android 应用。支持 JSON 战报导入、成员进攻统计（含月度统计与最佳成员积分评选）、未进攻人员公示、成员花名册管理，并提供 WebDAV 云端同步、悬浮球/录屏截图辅助、版本更新检查等能力。

## 功能

- **JSON 战报导入** — 粘贴 JSON 或选择文件，自动匹配花名册
- **部落战/联赛管理** — 部落战 + 联赛（每月多场，每场 7 轮），含联赛赛季 7 轮聚合视图
- **进攻统计** — 三星率、参战率、有效进攻率、月度统计、最佳成员评选、未进攻人员公示
- **花名册** — 模糊匹配建议、连续缺席场次统计，统一管理成员名单
- **数据管理** — 单场导出 / CSV / 全量备份 JSON，WebDAV 云端同步（支持坚果云）
- **成员进攻编辑** — 详情页修改每位成员的进攻状态与摧毁率
- **辅助工具** — 悬浮球 + 无障碍录屏截屏，截图本地图库浏览
- **版本更新检查** — 内置更新检测（Gitee Release）

## 技术栈

| 层面 | 方案 |
|------|------|
| 语言 | Kotlin 2.1.21，JVM 21（minSdk 30 / targetSdk 35） |
| UI | Jetpack Compose + Material 3（BOM 2025.06.01）+ Navigation Compose |
| 数据库 | Room 2.7.2 + KSP（DB v6，5 个手写 Migration） |
| JSON | Gson 2.11.0（宽松解析，DTO 可空兜底） |
| 架构 | MVVM + Repository（无 DI 框架，`di/warViewModel` 工厂） |
| 安全 | SecurePrefs：AndroidKeyStore + AES/GCM 加密存储 WebDAV 密码 |
| 构建 | Gradle 8.11.1（wrapper）+ AGP 8.9.2 + version catalog（阿里云镜像） |
| 质量 | lint 门禁 + detekt 静态检查 + 153 个 JUnit 单元测试 |

## 构建

```bash
# 编译 Debug APK（模块名是 :COCtools，不是 :app）
./gradlew :COCtools:assembleDebug

# APK 输出
COCtools/build/outputs/apk/debug/COCtools-debug.apk

# 单元测试（纯 JVM，无需设备）
./gradlew :COCtools:testDebugUnitTest
```

> 离线环境（无网络下载 wrapper 发行版）时，可用本地 Gradle 发行版替代：`export JAVA_HOME='<Android Studio 路径>/jbr'` 后直接调用本地 gradle 二进制执行相同任务。

## 项目结构

```
COCtools/src/main/java/com/cocwar/
├── CocWarApplication.kt          # Application 入口（lazy DB + Repository 单例）
├── data/
│   ├── db/WarDatabase.kt         # Room DB v6 + DAO + 5 个 Migration
│   ├── model/WarModels.kt        # DTO（宽松可空）+ 领域模型
│   ├── parser/WarJsonParser.kt   # JSON → ParseResult（永不抛异常）
│   ├── repository/               # WarRepository + BackupCodec + EventNamingRules
│   ├── migrate/DataMigrator.kt   # 旧联赛事件名迁移
│   ├── csv/                      # CSV 编解码 / 导出 / 导入
│   ├── samples/SampleDataProvider.kt # 内置示例数据
│   ├── sync/                     # WebDAV 同步（WebDavClient / SyncConfig / SyncDecider / SecurePrefs）
│   └── update/                   # 版本更新检查（UpdateChecker + UpdateConfig）
├── di/WarViewModel.kt            # @Composable ViewModel 工厂（scoped to NavBackStackEntry）
├── domain/                       # 纯函数统计：StatsCalculator + LeagueSeason
├── service/                      # 后台服务：悬浮球 + 无障碍录屏截屏
└── ui/
    ├── MainActivity.kt           # 底部导航（战报/统计/成员/设置）+ NavHost
    ├── components/               # 通用组件（CocCard、StatTile、徽章、UpdateDialog 等）
    ├── eventlist/  detail/  importflow/  members/  season/  stats/  sync/  settings/
    ├── util/                      # 中文标签、Levenshtein 模糊匹配、筛选持久化
    └── theme/                     # Material 3 主题 + 双主题切换
```

## 文档

- `docs/RULES.md` — 统计口径与命名规则的权威定义（改口径必须先改此处）
- `docs/ROADMAP.md` — 暂缓开发方向
- `docs/UPGRADE.md` — 依赖与 SDK 升级记录
- `releases/RELEASE_LOG.md` — 版本发行日志
