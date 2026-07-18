# COC 部落战/联赛数据管理工具

部落冲突（Clash of Clans）部落战与联赛数据管理 Android 应用。支持 JSON 战报导入、成员进攻统计、未进攻人员公示、WebDAV 云端同步。

## 功能

- **JSON 战报导入** — 粘贴 JSON 或选择文件，自动匹配花名册
- **部落战/联赛管理** — 支持部落战和联赛（每月 1~2 场，每场 7 轮）
- **进攻统计** — 三星率、参战率、有效进攻率、未进攻人员公示
- **花名册** — 模糊匹配建议，统一管理成员名单
- **数据导出** — 单场导出 / 全量备份为 JSON
- **云端同步** — WebDAV 上传/下载备份（支持坚果云）
- **成员进攻编辑** — 详情页修改每位成员的进攻状态和摧毁率

## 技术栈

| 层面 | 方案 |
|------|------|
| 语言 | Kotlin 2.0.21, JVM 21 |
| UI | Jetpack Compose + Material 3 |
| 数据库 | Room + KSP |
| JSON | Gson |
| 架构 | MVVM + Repository（无 DI 框架） |
| WebDAV | HttpURLConnection（零额外依赖） |

## 构建

```bash
# 环境
export JAVA_HOME='<Android Studio 路径>/jbr'
export PATH="$JAVA_HOME/bin:$PATH"

# 编译
./gradlew :COCtools:assembleDebug

# APK 输出
COCtools/build/outputs/apk/debug/COCtools-debug.apk
```

## 项目结构

```
COCtools/src/main/java/com/cocwar/
├── CocWarApplication.kt          # Application 入口
├── data/
│   ├── db/WarDatabase.kt         # Room 数据库 + DAO + 迁移
│   ├── model/WarModels.kt        # DTO + 领域模型
│   ├── parser/WarJsonParser.kt   # JSON 解析器
│   ├── repository/WarRepository.kt # 数据仓库
│   ├── samples/SampleDataProvider.kt # 示例数据
│   └── sync/                     # WebDAV 同步
│       ├── WebDavClient.kt
│       └── SyncConfig.kt
├── di/WarViewModel.kt            # ViewModel 创建
├── domain/StatsCalculator.kt     # 统计算法
└── ui/
    ├── MainActivity.kt           # 导航入口
    ├── components/Components.kt  # 通用组件
    ├── detail/                   # 详情页（含编辑）
    ├── eventlist/                # 事件列表
    ├── importflow/               # 导入流程
    ├── members/                  # 成员管理
    ├── stats/                    # 统计页面
    ├── sync/                     # 云端同步页面
    ├── util/Labels.kt            # 中文标签 + 名称解析
    └── theme/Theme.kt            # Material 3 主题
```
