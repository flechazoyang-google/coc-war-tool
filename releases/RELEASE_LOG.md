# COC War Tool 发行版日志

## v3.9 (2026-08-04)

Bug 修复 + 数据一致性整修

- **Bug 修复**：联赛示例数据进攻槽位由 2 修正为 1（与联赛规则一致）；修复 DB 迁移 v1→v2 的级联删除导致成员数据丢失；修复 JSON 中重复 rank 致成员被静默覆盖；备份恢复增加非空校验——仅含花名册不含战报的备份拒绝导入、花名册仅当备份明确包含时才替换；截图列表 fallback 项不再误删 MediaStore 其他图片；截图取消后快速重请求不再触发双重截图；`UpdateChecker` 修复空指针与连接泄漏；WebDAV URL 尾部斜杠自动归一化；`StringMatcher.bestMatch` 完全匹配不再返回 null；`"%.1f%%".format()` 全局固定为 `Locale.US` 避免区域设置导致小数点变成逗号；导入后切换战报类型时自动重填进攻槽位并修复名称破坏问题；成员编辑改为重取最新 DB 数据避免快照竞态；事件分享 JSON 导出增加异常捕获
- **数据一致性**：编辑进攻的摧毁率时不再用启发式重算星数（星数来自游戏原始数据，摧毁率无法可靠推导 1 星/2 星）；导出 JSON 与备份格式新增 `rank` 字段保持双程往返一致；战报详情页成员职位改为实时映射花名册（消除与统计页的角色不一致）；进攻率超过 100% 时自动 clamp 到 100%；解析器去重同一 `attack_order` 的重复进攻记录；备份花名册替换仅当备份明确含 `roster` 数组时生效避免意外清空；迁移 v2→v3 新增历史 `totalStars` 回填逻辑
- **统计刷新**：统计页顶部新增刷新按钮，从其他页面编辑数据后一键同步

- **APK**: [COCtools-v3.9.apk](./3.9/COCtools-v3.9.apk)
- **Version Code**: 17
- **Gitee Release**: [v3.9](https://gitee.com/yang-genhao/coc-war-tool/releases/tag/v3.9)

---

## v3.8 (2026-08-02)

精简 JSON 数据结构 + 花名册职位管理 + 统计排名页简化

- **新精简 JSON 格式**：战报导入/导出/备份全面切换为精简结构——成员仅 `player_name`/`total_stars`/`attacks`，进攻仅 `attack_order`/`destruction_percentage`；`status` 由摧毁率推导（0=未进攻），`rank` 按数组顺序自动编号，**职位一律以花名册为准**；本地数据库自动迁移（v5→v6），旧数据与旧格式备份仍可无感导入（Gson 自动忽略废弃键）
- **花名册职位管理**：成员页可为每人设置 首领/副首领/长老/成员（默认成员），职位在花名册统一维护，战报详情、统计、本月最佳的职位显示实时同步；职位徽标改为「色点+文字」，默认成员弱化为低调纯文字
- **统计排名页简化**：排名视图仅显示 名次/昵称/参战/星数/三星率 五列，去除卡片拥挤信息；点击成员弹出详情弹窗，以表格逐场列出本月各场战报的两次进攻摧毁率与星数（联赛自动适配单次进攻）
- **JSON 格式示例更新**：工具页「JSON 数据格式」弹窗同步为精简结构并附新语义说明

- **APK**: [COCtools-v3.8.apk](./3.8/COCtools-v3.8.apk)
- **Version Code**: 16
- **Gitee Release**: [v3.8](https://gitee.com/yang-genhao/coc-war-tool/releases/tag/v3.8)

---

删除可撤销 + 导出文件化 + 本月最佳精简 + 设置滑杆收起

- **删除可撤销**：战报与花名册成员删除后立即生效，底部弹出「已删除 · 撤销」Snackbar（约 4 秒），点撤销完整恢复（战报含全部成员进攻记录，成员重新加入名单），替换原先「不可撤销」的确认弹窗，防止误触
- **导出文件化 + 备份导入**：工具页「导出所有数据」改为通过系统文件选择器保存为 JSON 文件（原为文本分享）；新增「从备份导入」——选择备份文件先校验再完整还原（含花名册），与 WebDAV 云端备份同格式互通；导出补全 `is_sample` 字段
- **本月最佳精简**：每行仅显示 排名/昵称/得分，昵称按职位着色；前三名授予 上将/中将/少将 徽章；点击昵称弹出得分来源明细弹窗（逐项规则 × 次数 = 小计，合计可对账）
- **设置滑杆收起**：截图工具「滑动步长」「自动清理」默认收起为一行（显示当前值），点击展开滑杆，节省纵向空间

- **APK**: [COCtools-v3.7.apk](./3.7/COCtools-v3.7.apk)
- **Version Code**: 15
- **Gitee Release**: [v3.7](https://gitee.com/yang-genhao/coc-war-tool/releases/tag/v3.7)

---

## v3.6 (2026-08-01)

外观选择紧凑化 + 本月最佳积分制重构

- **外观紧凑化**：工具页「外观」主题选择由 5 行整行列表改为单行紧凑色点选择器（ThemeChip），保留双色徽章与点击切换，选中项描边高亮，纵向空间从约 300dp 压缩到约 70dp
- **积分制重构**：本月最佳改为积分制——每颗星 +1、每次 100% 摧毁率 +1、单场满 6 星 +2、参战但空 1 个进攻机会 -3、两次进攻全空 -10、名单成员未参战 -4；评选范围覆盖花名册成员（未参战也会被扣分计入），副标语同步显示 满星/空刀/未进攻/未参与 次数

- **APK**: [COCtools-v3.6.apk](./3.6/COCtools-v3.6.apk)
- **Version Code**: 14
- **Gitee Release**: [v3.6](https://gitee.com/yang-genhao/coc-war-tool/releases/tag/v3.6)

---

## v3.5 (2026-08-01)

满星率口径修复 + 多主题切换 + 职位显示修复

- **满星率修复**：统计总览的满星率改为按「参与人数×3」计算理论最大星数，部落战不再按每人 2 槽高估门槛
- **多主题**：工具页新增「外观·主题选择」，内置 墨册/星夜/烈焰/翡翠/樱花 五套风格色板，每套含浅色与深色两版并跟随系统明暗自动切换，选择本地持久化
- **职位显示修复**：`vice_leader` 等写法正确显示为「副首领」，本月最佳、成员排名、未进攻排行与战报详情同步修正，颜色与副首领一致

- **APK**: [COCtools-v3.5.apk](./3.5/COCtools-v3.5.apk)
- **Version Code**: 13
- **Gitee Release**: [v3.5](https://gitee.com/yang-genhao/coc-war-tool/releases/tag/v3.5)

---

## v3.4 (2026-08-01)

统计页重构：类型一级筛选 + 总览满星率与图表化

- **总览**：总星数改为满星率——单场获得总星数达到理论最大值（可用攻击槽位×3）即计为满星，满星率 = 满星场次占比
- **筛选重构**：类型作为一级筛选、视图作为二级筛选；部落战可选 总览/排名/预警/本月最佳，联赛仅 总览/预警
- **总览按类型独立**：部落战只看部落战数据、联赛只看联赛数据，不再固定合并全量
- **图表化**：新增 Canvas 自绘折线图（每场星数趋势）与雷达图（进攻率/三星率/均摧毁/星率/满星率），零第三方依赖
- 切换类型时若当前视图不属于该类型，自动回落到总览

- **APK**: [COCtools-v3.4.apk](./3.4/COCtools-v3.4.apk)
- **Version Code**: 12
- **Gitee Release**: [v3.4](https://gitee.com/yang-genhao/coc-war-tool/releases/tag/v3.4)

---

## v3.3 (2026-07-31)

统计口径统一 + 本月最佳独立视图 + 一批隐蔽 Bug 修复 + 截图体验优化

- **统计页**：类型筛选两态化（部落战/联赛独立，移除「全部」），旧版本恢复值自动收敛防崩溃
- **总览**：固定同时展示部落战与联赛全量数据，不受类型筛选影响
- **本月最佳**：独立为第 4 个视图，展示全部成员积分（按得分降序），积分制仅统计部落战
- **统计口径统一**：进攻率分母改为参战槽位数（部落战×2/联赛×1），官方 API 数据不再恒为 100%；三星率三处口径统一为已使用进攻；成员职务取最近一次参与事件
- **导入**：轮次从名称解析（不再丢失）；保存时名单写入与导入串行，避免退出页面丢新成员
- **WebDAV**：密码改用 EncryptedSharedPreferences 加密存储（自动迁移旧明文），并加入备份排除规则防云端泄漏；下载恢复增加内容校验、二次确认与完整还原
- **截图**：悬浮球点击不再弹遮挡 Toast，移除截图进度浮层（不会被截进画面）；前台通知提供「取消截图」入口；滚动到底后重复截图不再保存（含已保存的重复页自动删除）；PNG 压缩移到后台线程
- **悬浮球**：启动时序修复（先进入前台再检查权限，避免崩溃）；吸附左/右边缘真实生效；拖动边界保护；多指触摸不再误触发
- **数据库**：移除升级时静默删库的兜底，迁移改为幂等检查
- **其他**：攻击编辑改为单次原子写入（消除约 50% 概率的状态竞态）；JSON 解析移到后台线程；版本号比较支持预发布后缀；Android 13+ 请求通知权限；无障碍未开启时点击悬浮球直接跳转设置页

- **APK**: [COCtools-v3.3.apk](./3.3/COCtools-v3.3.apk)
- **Version Code**: 11
- **Gitee Release**: [v3.3](https://gitee.com/yang-genhao/coc-war-tool/releases/tag/v3.3)

---

## v3.2 (2026-07-31)

悬浮窗自动启动游戏 + 截图完成弹窗通知 + 本月最佳积分制 + 三星率计算修正

- 开启悬浮球后自动启动部落冲突（支持国际版/昆仑版/腾讯版）
- 截图完成后弹出高优先级通知横幅，点击通知回到 App
- 本月最佳改为积分制（仅统计部落战）：三星率×50 + 星数贡献×30 + 出勤率×20，满分 100
- 三星率计算改为分母包含未进攻次数（used + unused），不打等于放弃机会
- 总览页始终使用全量数据，不受类型筛选影响

- **APK**: [COCtools-v3.2.apk](./3.2/COCtools-v3.2.apk)
- **Version Code**: 10
- **Gitee Release**: [v3.2](https://gitee.com/yang-genhao/coc-war-tool/releases/tag/v3.2)

---

## v3.1 (2026-07-30)

统计页筛选级联化 + 导入成员匹配方式改为下拉选择

- 统计页筛选对话框改为级联结构：选"排名"展开排序方式，选"预警"展开时间段，选"总览"隐藏类型
- 排序方式和预警时间段从页面内移至筛选对话框，页面更简洁
- 导入战报时未匹配成员改用下拉菜单选择处理方式：使用建议 / 从名单中选择 / 作为新成员导入
- 有模糊匹配建议时默认使用建议名，无建议时默认作为新成员导入

- **APK**: [COCtools-v3.1.apk](./3.1/COCtools-v3.1.apk)
- **Version Code**: 9
- **Gitee Release**: [v3.1](https://gitee.com/yang-genhao/coc-war-tool/releases/tag/v3.1)

---

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
