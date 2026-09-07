# COC War Tool 发布流程（v2 — 仅 beta 公开测试版 + 正式版）

> 适用范围：从 v4.10.0 起。阶段仅保留两种：**beta 公开测试版** 与 **正式版**。
> 不再发布 alpha / rc / preview。
> 分工：AI 负责「编译打包 + 命名归档 + 生成 release.json 片段 + Gitee 发行版（无 APK 附件）」；
> 你负责「把 APK 上传到 CDN + 合并 release.json（使 App 内检查更新生效）」。

---

## 0. 角色分工（一句话）

| 步骤 | 谁做 | 产出 |
|------|------|------|
| 编译、命名、放本地目录 | AI | `releases/<版本>/COCtools-v<版本>[-beta.N].apk` + 别名文件 |
| 生成 release.json 片段 | AI | `releases/<版本>/release.json`（供你粘贴） |
| Gitee 发行版（仅说明） | AI | Gitee Release（tag + 更新说明，**无 APK 附件**） |
| 上传 APK 到 CDN | 你（手动） | 覆盖 `COCtools-beta.apk` / `COCtools-stable.apk` |
| 合并 release.json 到 CDN | 你（手动） | `https://cdn.flechazo.icu/release.json` 生效 |

> 原因：App 内「检查更新」读取 CDN 上的 `release.json`，其中 `url` 指向 CDN 上的 APK。
> 只有你上传后才知道最终 CDN 地址，因此「上传 + 更新 release.json」必须由你完成；
> AI 只把 APK 编译好、命名、归档到本地，并生成可直接粘贴的 `release.json` 片段。

---

## 1. 版本号与 versionCode 规则

- 版本号（SemVer）：`主.次.修` + 阶段后缀。
  - beta：`4.10.0-beta.1`、`4.10.0-beta.2` …
  - 正式版：无后缀，如 `4.10.0`、`4.11.0`。
- **阶段只有两种**：`beta`（公开测试版）、正式版（无后缀）。alpha / rc / preview 不再使用。
- **versionCode**：每个「对外发布的构建」（无论 beta 还是正式版）递增 +1。
  - 当前：`38` = `4.10.0-beta.1`
  - 下一正式版 `4.10.0` → `39`
  - 下一 beta `4.11.0-beta.1` → `40`
  - 规则目的：保证新包 `versionCode` 永远 > 已装包，Android 才允许覆盖安装 / App 才判定为「有更新」。
- 修改位置：`COCtools/build.gradle.kts` 的 `versionCode` 与 `versionName`。

---

## 2. 构建与本地命名

构建命令（项目根目录，优先 wrapper；离线用本地 gradle 发行版）：

```bash
# 在线
./gradlew :COCtools:assembleDebug --no-daemon
# 离线兜底
unset ACC_PRODUCT_CONFIG_V3 2>/dev/null
GRADLE='/c/Users/flechazo/.gradle/wrapper/dists/gradle-8.9-bin/<hash>/gradle-8.9/bin/gradle'
"$GRADLE" :COCtools:assembleDebug --no-daemon
```

产物：`COCtools/build/outputs/apk/debug/COCtools-debug.apk`

### 2.1 本地归档命名（版本化，便于历史追溯，不被 git 跟踪）

目录：`releases/<版本>/`（例：`releases/4.10.0/`）

| 类型 | 归档文件名 | 例子 |
|------|-----------|------|
| beta | `COCtools-v<版本>-beta.<N>.apk` | `releases/4.10.0/COCtools-v4.10.0-beta.1.apk` |
| 正式版 | `COCtools-v<版本>.apk` | `releases/4.10.0/COCtools-v4.10.0.apk` |

### 2.2 CDN 上传别名文件（你上传到 CDN 的那个，**固定路径覆盖**）

| 类型 | 别名文件 | 上传到的 CDN 路径 |
|------|----------|------------------|
| beta | `releases/<版本>/COCtools-beta.apk` | `https://cdn.flechazo.icu/COCtools-beta.apk` |
| 正式版 | `releases/<版本>/COCtools-stable.apk` | `https://cdn.flechazo.icu/COCtools-stable.apk` |

> 说明：`release.json` 里的 `url` 指向**固定别名**（每次发版覆盖同一文件，`url` 不变）。
> 沿用现有方案，无需为每次发版改 CDN 路径。

归档 + 别名复制示例（beta）：

```bash
V=4.10.0
cp COCtools/build/outputs/apk/debug/COCtools-debug.apk releases/$V/COCtools-v$V-beta.1.apk
cp COCtools/build/outputs/apk/debug/COCtools-debug.apk releases/$V/COCtools-beta.apk
```

---

## 3. release.json（App 内更新依赖，由你合并到 CDN）

App 读取 `https://cdn.flechazo.icu/release.json`，结构：

```json
{
  "beta":   { "version": "4.10.0-beta.1", "url": "https://cdn.flechazo.icu/COCtools-beta.apk",   "body": "公开测试版：花名册重构…" },
  "stable": { "version": "4.9.0",         "url": "https://cdn.flechazo.icu/COCtools-stable.apk", "body": "正式版：…" }
}
```

- AI 在 `releases/<版本>/release.json` 生成**对应通道片段**（含 `version` + `body`，`url` 用上面的固定别名）。
- 你上传 APK 后，把该片段**合并进 CDN 上的 `release.json`**：
  - 只保留 `beta` 与 `stable` 两个通道，删除旧的 `alpha` / `rc` / `preview` 键。
  - `url` 保持固定别名不变。
- ⚠️ 不更新 `release.json`，App 内「检查更新」就不会提示新版本（即使 APK 已上传）。

---

## 4. Gitee 发行版（仅说明，无 APK 附件）

通过 Gitee MCP（`mcp__gitee__create_release`）或 Gitee API 创建：

| 字段 | 取值 |
|------|------|
| 仓库 | `yang-genhao/coc-war-tool` |
| tag | `v<版本>[-beta.N]`（例 `v4.10.0-beta.1`） |
| name | 同 tag |
| prerelease | beta = `true`；正式版 = `false` |
| body | 取自 `releases/RELEASE_LOG.md` 对应条目（更新说明） |
| **assets** | **不上传 APK**（APK 改由 CDN 分发）；body 可附 CDN 下载链接 |

> 用户从 Gitee Releases 页看到的是「更新说明 + CDN 链接」，APK 实际从 CDN 下载。
> tag 在发版前先 `git push` 代码与 tag；发行版引用该 tag。

---

## 5. 标准发版步骤（按顺序）

1. 确认 `COCtools/build.gradle.kts` 的 `versionName` / `versionCode` 正确（见 §1）。
2. 在 `releases/RELEASE_LOG.md` **顶部**追加本次条目（含「APK 下载」指向 CDN 别名 + Version Code）。
3. `git commit` 上述变更 → `git push origin master` → `git tag v<版本>[-beta.N]` → `git push origin <tag>`。
4. 构建：`./gradlew :COCtools:assembleDebug --no-daemon`。
5. 复制并重命名（§2.1 + §2.2）。
6. 生成 `releases/<版本>/release.json` 片段（§3）。
7. 创建 Gitee 发行版（§4，tag + 说明，**无附件**）。
8. **交给你**：把 `COCtools-beta.apk` / `COCtools-stable.apk` 上传 CDN（覆盖）+ 合并 `release.json`。
9. 收尾：若上传后才拿到最终 CDN 链接，回填到 Gitee 发行版 body。

---

## 6. 本次执行计划（v4.10.0-beta.1）

现状：
- 代码已在本地 commit `0252ebc`（花名册重构 + 解析容错修复），**待 push**。
- `releases/4.10.0/` 已有 `COCtools-v4.10.0-beta.1.apk` 与 `COCtools-beta.apk`（versionCode 38，无需重新构建）。
- `releases/RELEASE_LOG.md` 已有 v4.10.0-beta.1 条目。

AI 将执行：
1. `git push origin master`（推送 `0252ebc`）。
2. `git tag v4.10.0-beta.1` → `git push origin v4.10.0-beta.1`。
3. 生成 `releases/4.10.0/release.json`（beta 通道片段）。
4. 创建 Gitee 发行版 `v4.10.0-beta.1`（`prerelease=true`，body = RELEASE_LOG 条目，**无 APK 附件**）。
5. 交付：你上传 `releases/4.10.0/COCtools-beta.apk` 到 CDN + 合并 `release.json`。

> 注：若你希望本次只 push 代码、Gitee 发行版稍后再发，或 release.json 完全由你手动维护，请告知，我按你的选择调整。
