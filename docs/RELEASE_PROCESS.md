# COC War Tool 发布流程（v3 — 目录结构 + 分工）

> 适用范围：从 v4.10.0 起。阶段仅保留两种：**beta 公开测试版** 与 **正式版**。
> 不再发布 alpha / rc / preview。
> **分工**：AI 只负责「编译打包 + 命名 + 放到对应路径（含 markdown 版本说明）」；
> **Gitee 发行版与 CDN 上传（含合并 release.json）全部由你手动完成**。

---

## 0. 角色分工（一句话）

| 步骤 | 谁做 | 产出 |
|------|------|------|
| 编译、命名、放本地目录 + 写版本说明 | AI | `releases/<版本>/{stable,beta/N}/*` + `RELEASE_NOTE.md` |
| 生成 release.json 片段（供你粘贴） | AI | `releases/<版本>/release.json` |
| Gitee 发行版 | 你（手动） | Gitee Release（tag + 更新说明，**无 APK 附件**） |
| 上传 APK 到 CDN | 你（手动） | 以**版本化文件名**上传（与本地包同名，如 `COCtools-v4.10.0.apk`） |
| 合并 release.json 到 CDN | 你（手动） | `https://cdn.flechazo.icu/release.json` 生效 |

> AI **不再**创建 Gitee 发行版、不再上传安装包。AI 把包打好、命名、归档到本地对应路径，
> 并生成可直接粘贴的 `release.json` 片段；你再手动做 Gitee 发行版 + CDN 上传。

---

## 1. 目录结构（本地归档）

```
releases/<版本>/
├── stable/
│   ├── COCtools-v<版本>.apk     # 正式版安装包（如 COCtools-v4.10.0.apk）
│   └── RELEASE_NOTE.md          # 正式版版本说明（markdown）
├── beta/
│   ├── 1/
│   │   ├── COCtools-v<版本>-beta.1.apk   # 第 1 个测试版
│   │   └── RELEASE_NOTE.md               # 该测试版说明
│   ├── 2/
│   │   ├── COCtools-v<版本>-beta.2.apk   # 第 2 个测试版（正式版发布前可有多轮）
│   │   └── RELEASE_NOTE.md
│   └── …                        # 数字 = 测试版编号，一个序号代表一个测试版
└── release.json                 # 供你合并到 CDN 的片段（beta/stable 两通道）
```

约定：
- `stable/` 只放正式版安装包 + 说明。
- `beta/<N>/` 放第 N 个测试版安装包 + 说明；正式版发布前可能迭代多个测试版（1、2、3…）。
- `release.json` 位于版本目录顶层（不是 stable/ 或 beta/ 内部）。
- `*.apk` 被 `.gitignore` 忽略，仅本地留存；目录结构（含 `RELEASE_NOTE.md`）可进 git 追溯。

> 历史版本目录（如 `releases/1.0/`、`releases/4.9.0/` 等）仍为旧的扁平格式，
> 本流程仅约束 **v4.10.0 起的新发版**；如需回填旧目录可另行处理。

---

## 2. 版本号与 versionCode 规则

- 版本号（SemVer）：`主.次.修` + 阶段后缀。
  - beta：`4.10.0-beta.1`、`4.10.0-beta.2` …
  - 正式版：无后缀，如 `4.10.0`、`4.11.0`。
- **阶段只有两种**：`beta`（公开测试版）、正式版（无后缀）。alpha / rc / preview 不再使用。
- **versionCode**：每个「对外发布的构建」（无论 beta 还是正式版）递增 +1。
  - 当前：`38` = `4.10.0-beta.1`，`39` = `4.10.0`
  - 下一 beta `4.11.0-beta.1` → `40`
  - 规则目的：保证新包 `versionCode` 永远 > 已装包，Android 才允许覆盖安装 / App 才判定为「有更新」。
- 修改位置：`COCtools/build.gradle.kts` 的 `versionCode` 与 `versionName`。

---

## 3. AI 构建与本地放置（核心职责）

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

### 3.1 复制到对应路径 + 写版本说明

| 类型 | 安装包路径 | 说明路径 |
|------|-----------|----------|
| 正式版 | `releases/<版本>/stable/COCtools-v<版本>.apk` | `releases/<版本>/stable/RELEASE_NOTE.md` |
| 测试版 N | `releases/<版本>/beta/<N>/COCtools-v<版本>-beta.N.apk` | `releases/<版本>/beta/<N>/RELEASE_NOTE.md` |

示例（正式版 4.10.0）：

```bash
V=4.10.0
mkdir -p releases/$V/stable releases/$V/beta/1
cp COCtools/build/outputs/apk/debug/COCtools-debug.apk releases/$V/stable/COCtools-v$V.apk
# 同时撰写 releases/$V/stable/RELEASE_NOTE.md（更新内容 + Version Code）
```

示例（测试版 4.11.0-beta.1，编号 N=1）：

```bash
V=4.11.0; N=1
mkdir -p releases/$V/beta/$N
cp COCtools/build/outputs/apk/debug/COCtools-debug.apk releases/$V/beta/$N/COCtools-v$V-beta.$N.apk
# 同时撰写 releases/$V/beta/$N/RELEASE_NOTE.md
```

> AI 只把安装包放好、写好 `RELEASE_NOTE.md`。安装包在**本地、CDN、release.json 三处使用同一版本化文件名**
> （`COCtools-v<版本>[-beta.N].apk`），无需别名副本——你上传到 CDN 时保持文件名不变即可。

---

## 4. release.json（供你合并到 CDN）

App 读取 `https://cdn.flechazo.icu/release.json`，结构：

```json
{
  "beta":   { "version": "4.10.0-beta.1", "url": "https://cdn.flechazo.icu/COCtools-v4.10.0-beta.1.apk", "body": "公开测试版：花名册重构…" },
  "stable": { "version": "4.10.0",        "url": "https://cdn.flechazo.icu/COCtools-v4.10.0.apk",        "body": "正式版：花名册重构…" }
}
```

- AI 在 `releases/<版本>/release.json` 生成**对应通道片段**（含 `version` + `body`，`url` 用**版本化文件名**，与本地安装包同名）。
- 你上传 APK 后，把该片段**合并进 CDN 上的 `release.json`**：
  - 只保留 `beta` 与 `stable` 两个通道，删除旧的 `alpha` / `rc` / `preview` 键。
  - `url` 与本地安装包文件名保持一致（版本化命名）。
- ⚠️ 不更新 `release.json`，App 内「检查更新」就不会提示新版本（即使 APK 已上传）。

---

## 5. 你手动完成（Gitee 发行版 + CDN 上传）

1. **Gitee 发行版**：在 `https://gitee.com/yang-genhao/coc-war-tool/releases/new`
   手动创建，选择 tag（如 `v4.10.0`），填写更新说明（可取自 `RELEASE_NOTE.md`），
   **不附加 APK**（APK 走 CDN）；说明里附 CDN 下载链接。
2. **上传 APK 到 CDN**（保持版本化文件名不变）：
   - 正式版：把 `releases/<版本>/stable/COCtools-v<版本>.apk` 上传到 `https://cdn.flechazo.icu/COCtools-v<版本>.apk`
   - 测试版：把 `releases/<版本>/beta/<N>/COCtools-v<版本>-beta.N.apk` 上传到 `https://cdn.flechazo.icu/COCtools-v<版本>-beta.N.apk`
3. **合并 release.json**：把 `releases/<版本>/release.json` 整体覆盖到 CDN 上的 `release.json`。

---

## 6. 标准发版步骤（按顺序）

AI 侧：
1. 确认 `COCtools/build.gradle.kts` 的 `versionName` / `versionCode` 正确（见 §2）。
2. 构建：`./gradlew :COCtools:assembleDebug --no-daemon`。
3. 复制到 §3.1 对应路径（stable/ 或 beta/N/）+ 撰写 `RELEASE_NOTE.md`。
4. 生成 `releases/<版本>/release.json` 片段（§4）。
5. `git commit`（version 改动 + 目录结构/说明）+ `git push origin master`（⚠️ 见末尾备注）+ `git tag v<版本>[-beta.N]` + `git push origin <tag>`。
6. **交付**：告知你「包已就绪，路径在 releases/<版本>/…」，由你执行 §5。

你侧：执行 §5（Gitee 发行版 + CDN 上传 + 合并 release.json）。

---

## 备注：git push 绕过 reg.exe 黑名单

本机安全策略将 `reg.exe` 列入程序黑名单，git 凭据管理器（GCM）读注册表时会触发而被杀。
push 时使用以下环境变量绕过（commit / tag 不受影响）：

```bash
export GIT_CONFIG_NOSYSTEM=1
export GCM_DISABLED=true
export GIT_CREDENTIAL_HELPER=
export GIT_TERMINAL_PROMPT=0
git -c credential.helper= push "https://yang-genhao:<token>@gitee.com/yang-genhao/coc-war-tool.git" master
```
