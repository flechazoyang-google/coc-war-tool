# COC War Tool 发行版日志

## v3.0 (2026-07-29)

战报筛选条件持久化 + 导入名称自动生成优化 + 剪切板导入改为手动触发

- 战报列表的筛选条件（类型/年份/月份）在切换页面或退出应用后保持不丢失
- 导入战报时名称自动生成：年/月取自当前日期，首位 0/1 根据类型自动设置，尾数根据同类型战报自增
- 切换部落战↔联赛类型时完整重新生成名称（而不仅是改首位数字）
- 战报页不再自动检测剪切板，改为右上角粘贴按钮手动触发，无匹配内容时 Toast 提示
- 删除 AI 识别相关代码与依赖

- **APK**: [COCtools-v3.0.apk](./3.0/COCtools-v3.0.apk)
- **Version Code**: 8
- **Gitee Release**: [v3.0](https://gitee.com/yang-genhao/coc-war-tool/releases/tag/v3.0)

---

## v2.2 (2026-07-20)

修复顶部留白问题：移除战报/统计/成员/设置四个底部导航 Tab 内嵌套的 Scaffold 和 TopAppBar，改用紧凑内联标题栏，减少无效留白空间。

- **APK**: [coc-war-tool-v2.2.apk](./2.2/coc-war-tool-v2.2.apk)
- **Git Tag**: `v2.2`
- **Version Code**: 7
- **Gitee Release**: [v2.2](https://gitee.com/yang-genhao/coc-war-tool/releases/tag/v2.2)

---

## v2.1 (2026-07-20)

修复自动更新下载后无法安装的问题：补齐 FileProvider 的 cache-path 配置；下载请求增加 User-Agent 与 APK 文件头校验。

- **APK**: [coc-war-tool-v2.1.apk](./2.1/coc-war-tool-v2.1.apk)
- **Git Tag**: `v2.1`
- **Version Code**: 6
- **Gitee Release**: [v2.1](https://gitee.com/yang-genhao/coc-war-tool/releases/tag/v2.1)

---

## v1.3 (2025-07-20)

Bug 修复与小幅优化

- **APK**: [coc-war-tool-v1.3.apk](./1.3/coc-war-tool-v1.3.apk)
- **Git Tag**: `v1.3`
- **Version Code**: 4
- **Gitee Release**: [v1.3](https://gitee.com/yang-genhao/coc-war-tool/releases/tag/v1.3)

---

## v1.2 (2025-07-18)

Bug修复与小幅优化

- **APK**: [coc-war-tool-v1.2.apk](./1.2/coc-war-tool-v1.2.apk)
- **Git Tag**: `v1.2`
- **Version Code**: 3
- **Gitee Release**: [v1.2](https://gitee.com/yang-genhao/coc-war-tool/releases/tag/v1.2)

---

## v1.1 (2026-07-18)

日常更新与优化

- **APK**: [coc-war-tool-v1.1.apk](./1.1/coc-war-tool-v1.1.apk)
- **Git Tag**: `v1.1`
- **Version Code**: 2
- **Gitee Release**: [v1.1](https://gitee.com/yang-genhao/coc-war-tool/releases/tag/v1.1)

---
