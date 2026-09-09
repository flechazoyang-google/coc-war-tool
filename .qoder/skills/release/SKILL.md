---
name: release
description: "Publish a COC War Tool Android release. Use when the user asks to release, publish, or ship a new version. One command: scripts/release.ps1 -Version X.Y.Z (version bump, unit tests, signed release build, archive, git tag/push, GitHub Release)."
---

# Release Skill

发版唯一入口是项目脚本，流程与 `E:\agentWork\tools` 一致：

```powershell
# 1. 写 releases/v4.11.0/RELEASE_NOTE.md（缺失时脚本生成模板并中止）
# 2. 发版
.\scripts\release.ps1 -Version 4.11.0
# 预览
.\scripts\release.ps1 -Version 4.11.0 -DryRun
```

脚本：改版本号（versionCode +1）→ `:COCtools:testDebugUnitTest` → `:COCtools:clean :COCtools:assembleRelease`
（`keystore/coc-release.jks` 签名）→ APK 版本/签名/dex 校验 → 归档 `releases/vX.Y.Z/COCtools-vX.Y.Z.apk`
→ 更新 `releases/RELEASE_LOG.md` → commit + tag + push → `gh release create`（附 APK）→ 触发网站更新。

## 规则

- 版本号 SemVer `主.次.修`，**只发正式版**（无 `-alpha`/`-beta`/`-rc`/`-preview`）
- 渠道**仅 GitHub Releases**（`flechazoyang-google/coc-war-tool`）；七牛云 CDN、`release.json`、
  Gitee 发行版、qiniu 上传脚本**全部停用**
- `versionCode` 只增不减；已发布 tag 不删改
- APK 不进 git；`keystore.properties` / `keystore/` 不进 git 且必须备份

详见 `docs/RELEASE_PROCESS.md`。
