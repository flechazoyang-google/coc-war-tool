# COC War Tool 发布规范

> 适用范围：**v4.11.0 起**（与 `E:\agentWork\tools` 工具箱项目统一为同一套发版方式）。
> 分发渠道：**仅 GitHub Releases**（`flechazoyang-google/coc-war-tool`）。
> 七牛云 CDN `release.json`、Gitee 发行版、beta/rc/alpha 阶段**均已停用**。
> 版本号：SemVer `主.次.修`（只发正式版，不带 `-beta` 等后缀）。

---

## 一、一句话流程

```powershell
# 1. 写好 releases/v4.11.0/RELEASE_NOTE.md
# 2. 一条命令发版
.\scripts\release.ps1 -Version 4.11.0
```

脚本自动完成：改 `versionCode/versionName` → 单测 → `clean + assembleRelease`（签名）→
校验 APK 版本号与签名 → 复制到 `releases/v4.11.0/COCtools-v4.11.0.apk` → 更新 `RELEASE_LOG.md` →
commit + tag + push（GitHub 必需，其余远端尽力而为）→ `gh release create` 附 APK → 触发个人网站数据更新。

预览不落盘：

```powershell
.\scripts\release.ps1 -Version 4.11.0 -DryRun
```

> 脚本会校验：当前在 `master` 分支、工作区干净、`keystore.properties` 与 keystore 文件存在、
> 存在指向 `github.com` 的远端（默认 `github` 远端，缺失时脚本会提示 `git remote add` 命令）。

---

## 二、版本号规则

| 项 | 规则 |
|---|---|
| `versionName` | SemVer `主.次.修`，如 `4.11.0`；只发正式版，不带 `-beta` |
| `versionCode` | **每次对外构建 +1**，绝不回退（否则 Android 拒绝覆盖安装） |
| tag | `v<versionName>`，如 `v4.11.0` |
| 修改位置 | `COCtools/build.gradle.kts` 的 `defaultConfig`（由脚本自动改） |

> 当前：`4.10.0`（code 39）→ 下一个正式版 `4.11.0`（code 40）。

---

## 三、本地归档结构

```
releases/
├── RELEASE_LOG.md                     # 累计发布日志（进 git）
└── v4.11.0/
    ├── COCtools-v4.11.0.apk           # 本地留存（*.apk 已被 .gitignore 忽略）
    └── RELEASE_NOTE.md                # 版本说明（进 git）
```

- 目录名 = tag 名（`v4.11.0`），APK 名 = `COCtools-v<版本>.apk`
- **APK 不进 git**，只提交 `RELEASE_NOTE.md` 与 `RELEASE_LOG.md`
- 历史版本目录永久保留，便于回滚与追溯
- 旧格式目录（`releases/4.10.0/stable|beta/N/`、`releases/1.0/` 等）保留原样，不再新增

---

## 四、`RELEASE_NOTE.md` 模板

```markdown
# COC War Tool v4.11.0

发布日期：YYYY-MM-DD
Version Code：40

## 变更内容

- ✨ 新功能
- 🐛 修复
- 🛠 工程改动

## 校验

- 单测：N 用例 / 0 失败
- 构建：assembleRelease BUILD SUCCESSFUL
- 实机：安装 release 包冒烟通过
```

内容会**原样**作为 GitHub Release 的说明，因此写得面向用户（不要写内部实现细节）。

---

## 五、App 内的「检查更新」

| 项 | 值 |
|---|---|
| 数据源 | `https://api.github.com/repos/flechazoyang-google/coc-war-tool/releases/latest` |
| 仓库坐标 | `COCtools/build.gradle.kts` 的 `buildConfigField("String", "UPDATE_REPO", ...)` |
| 比对方式 | `tag_name` 去掉 `v` 后与 `BuildConfig.VERSION_NAME` 逐段数值比较 |
| 触发时机 | 启动时自动 +「设置 → 更新 → 检查更新」手动 |
| 下载 | Release 里第一个 `.apk` 资产的 `browser_download_url` |

发布后**无需改任何配置**——`gh release create` 一建，App 下一次检查即可发现。
`releases/latest` 只返回正式版，因此「加入测试计划」开关与预览版通道已移除。

---

## 六、签名与 keystore

| 项 | 值 |
|---|---|
| keystore | `keystore/coc-release.jks`（`.gitignore` 排除） |
| 凭据 | `keystore.properties`（`.gitignore` 排除） |
| 别名 | `cocwar` |
| 配置位置 | `COCtools/build.gradle.kts` 的 `signingConfigs.release` |

> ⚠️ **迁移提醒（只此一次）**：v4.10.0 及更早的安装包用的是 **debug keystore** 签名。
> 从第一个使用 `coc-release.jks` 的版本开始，**已装用户无法覆盖安装**，必须卸载旧版后重装
> （否则系统报「应用未安装 / 签名不一致」）。该版本的 `RELEASE_NOTE.md` 必须写明这一点。
>
> ⚠️ **keystore 必须备份**（密码同步备份）：丢失后无法再给已装用户推送可覆盖安装的更新，
> 只能让用户卸载重装。

---

## 七、网站联动

- 网站仓库：`flechazoyang-google/personal-website`（GitHub Pages）
- `scripts/update-projects.js` 通过 GitHub API 拉取最新 Release，写入 `projects.json` 的版本号与 APK 下载链接
- `.github/workflows/update-projects.yml` 每 6 小时自动跑一次；发布脚本会用
  `gh workflow run update-projects.yml --repo flechazoyang-google/personal-website` 立即触发
- 触发失败不影响发布，网站最多 6 小时后自动同步

---

## 八、失败与回滚

| 情况 | 处理 |
|---|---|
| 单测/构建失败 | 脚本中止，版本号未提交（工作区可 `git checkout COCtools/build.gradle.kts` 还原） |
| 推送成功但 Release 创建失败 | 手动补：`gh release create v4.11.0 releases/v4.11.0/COCtools-v4.11.0.apk --repo flechazoyang-google/coc-war-tool --notes-file releases/v4.11.0/RELEASE_NOTE.md --latest` |
| 发布后发现严重问题 | ① 不改已有 tag：直接发下一个 `4.11.1` 修复；② 若必须撤包：`gh release delete v4.11.0 --yes` |
| tag 打错 | `git tag -d v4.11.0 && git push github :refs/tags/v4.11.0` |
| Gitee 镜像推送失败 | 不影响发布（GitHub 为主渠道）；手动 `git push origin master --tags` |

> **永远不要**删除或替换已经发布过的 `versionCode`：已装用户将无法升级。

---

## 九、发布前检查清单

- [ ] 功能已在真机/模拟器冒烟（导入、统计、花名册、同步、悬浮球/截屏）
- [ ] `releases/v<版本>/RELEASE_NOTE.md` 已写好，面向用户可读
- [ ] `gh auth status` 正常，对 `flechazoyang-google/coc-war-tool` 有 `repo` 权限
- [ ] `keystore/coc-release.jks` 与 `keystore.properties` 存在且已备份（**丢失即无法升级**）
- [ ] 工作区干净、在 `master` 分支
- [ ] 若本次是首个新签名版本：版本说明已注明「需卸载旧版重装」

---

## 十、旧流程（已停用，仅存档）

| 旧做法 | 现状 |
|---|---|
| `./gradlew :COCtools:assembleDebug` 出 debug 包 | 改为 `assembleRelease`（R8 + 签名） |
| 七牛云 CDN 上传 APK + 合并 `release.json` | 停用；App 改读 GitHub Releases API |
| Gitee 发行版（tag + 说明，无附件） | 停用；改为 GitHub Release 附 APK |
| `releases/<版本>/stable|beta/<N>/` 目录 | 新发版改用 `releases/v<版本>/` |
| `.qoder/skills/release`（qiniu 上传脚本） | 已删除，发版统一走 `scripts/release.ps1` |
