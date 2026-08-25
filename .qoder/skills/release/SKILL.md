---
name: release
description: "Publish alpha/beta/rc/stable releases for the COC War Tool Android app. Use when the user asks to release, publish, or ship a new version. Handles SemVer version suggestion, changelog, build, git tag, push, and Qiniu Cloud release.json update."
---

# Release Skill

Publish a new release for COC War Tool following Semantic Versioning (SemVer).

## Version Format

`MAJOR.MINOR.PATCH[-STAGE.N]`

| 阶段 | 格式示例 | 中文标签 | 含义 | 适用场景 |
|------|---------|---------|------|---------|
| `-alpha.N` | `4.9.0-alpha.1` | 内部测试版 | 功能开发中，可能不完整 | 内部测试，可能有已知问题 |
| `-beta.N` | `4.9.0-beta.1` | 公开测试版 | 功能已完整 | 面向用户公开测试，收集反馈 |
| `-rc.N` | `4.9.0-rc.1` | 候选版 | 无已知 blocker | 如无问题即转为正式版 |
| (无后缀) | `4.9.0` | 正式版 | 稳定发布 | 推荐使用 |

排序：`alpha.1 < alpha.2 < beta.1 < rc.1 < 正式版`

同阶段可发多个（`.1` → `.2` → `.3`），阶段只能前进不能后退。

**典型发布流程**：

```
4.9.0-alpha.1   ← 功能开发中，内部先用
4.9.0-alpha.2   ← 修了几个 alpha 发现的问题
4.9.0-beta.1    ← 功能冻结，公开测试
4.9.0-rc.1      ← 没发现大问题，准备发布
4.9.0           ← 正式版
```

## Workflow

### 1. Determine release parameters

Read current version from `COCtools/build.gradle.kts` (versionName + versionCode).

**Auto-suggest next version** based on context:

- If user says "发正式版" or no stage specified → suggest removing prerelease suffix (e.g. `4.9.0-rc.1` → `4.9.0`) or bumping minor (`4.8.0` → `4.9.0`)
- If user says "发 alpha/内部测试" → suggest `X.Y.Z-alpha.1` (bump N if same base version already exists)
- If user says "发 beta/公开测试" → suggest `X.Y.Z-beta.1`
- If user says "发 rc/候选版" → suggest `X.Y.Z-rc.1`
- For breaking changes → bump MAJOR; new features → bump MINOR; bug fixes → bump PATCH

**versionCode**: always increment by 1 from current value.

Check `git log --oneline <last_tag>..HEAD` to help determine the right version bump.

Confirm with user before proceeding.

### 2. Update version in build.gradle.kts

Edit `COCtools/build.gradle.kts`:
```
versionCode = <new_code>
versionName = "<new_version>"
```

### 3. Run tests and build

```bash
./gradlew :COCtools:testDebugUnitTest --no-daemon
./gradlew :COCtools:assembleDebug --no-daemon
```

Both must succeed. If tests fail, stop and report.

### 4. Copy APK locally

```bash
cp COCtools/build/outputs/apk/debug/COCtools-debug.apk releases/COCtools-{stage}.apk
```

### 5. Upload APK to Qiniu Cloud

```bash
node .qoder/skills/release/scripts/qiniu-upload.cjs \
  --file "releases/COCtools-{stage}.apk" \
  --key "COCtools-{stage}.apk"
```

**APK 命名规则**：每个阶段只保留最新一个 APK，新上传覆盖旧文件。
- alpha → `COCtools-alpha.apk`
- beta → `COCtools-beta.apk`
- rc → `COCtools-rc.apk`
- stable → `COCtools-stable.apk`

Save the printed download URL for step 6.

### 6. Update release.json on Qiniu Cloud

Derive the changelog from `git log --oneline <last_tag>..HEAD`, then run:

```bash
node .qoder/skills/release/scripts/release-json-upload.cjs \
  --version "v{version}" \
  --url "{qiniu_apk_url}" \
  --body "{changelog}"
```

`--channel` is optional — the script auto-detects:
- Version with `-alpha`/`-beta`/`-rc` → `preview` channel
- Version without suffix → `stable` channel

### 7. Update release log

Prepend a new entry to `releases/RELEASE_LOG.md` after the `# COC War Tool 发行版日志` header.

Format:
```markdown
## v{version} ({date})

{type_label}：{one-line summary}

{bullet list of changes since last release}

- **APK 下载**: [{qiniu_url}]({qiniu_url})
- **Version Code**: {versionCode}

---
```

type_label by stage:
- alpha → "内部测试版：..."
- beta → "公开测试版：..."
- rc → "候选版：..."
- stable → "正式版：..."

### 8. Git commit and tag

```bash
git add COCtools/build.gradle.kts releases/RELEASE_LOG.md
git commit -m "release: v{version} {stage_label}"
git tag v{version}
```

stage_label: "内部测试版" / "公开测试版" / "候选版" / "正式版"

Note: Do NOT commit the APK file to git. It is hosted on Qiniu Cloud.

### 9. Push

```bash
git pull --rebase origin master   # in case remote has new commits
git push origin master --tags
```

If push fails due to remote changes, rebase and retry.

### 10. Report

Tell the user:
- Release published successfully
- Version and stage (e.g. "v4.9.0-alpha.1 内部测试版")
- APK download URL (Qiniu Cloud)
- release.json updated (channel auto-detected + version)
