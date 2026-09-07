# 豆包大模型识图 → CSV 导入：快速验证报告

> 目标：验证「豆包大模型识别部落战报截图 → 输出 CSV → 走现有导入链路」是否可行、能否达到准确度要求。
> 范围：仅验证，不改动 Android 代码。集成是验证通过后的后续任务。

## 1. 验证产物

| 产物 | 位置 | 说明 |
|---|---|---|
| 识图脚本 | `scripts/doubao-ocr.mjs` | 零依赖 Node 脚本：图片 → base64 → 火山 Ark（OpenAI 兼容）→ 严格 CSV 输出 |
| 凭证模板 | `scripts/.env`（gitignore 忽略） | `DOUBAO_API_KEY` / `DOUBAO_MODEL`（模型名或推理接入点 `ep-xxx`）/ `DOUBAO_BASE_URL` |
| 结构校验测试 | `COCtools/src/test/.../DoubaoOcrCsvValidationTest.kt` | 模型 CSV → `CsvImporter.parse` 可解析、列不错位、可入库 |
| 模拟测试图 | 临时目录 `coc-war-sample.png` | 复刻内置 sample 的 30 人部落战数据渲染的表格截图 |
| ground truth | 临时目录 `coc-war-sample-gt.csv` | 真实数据，用于准确度比对 |
| 比对工具 | 临时目录 `compare_csv.py` | 行数 / 名字精确与模糊 / 排名 / 星数 / 两列摧毁率 / 差异明细 |

## 2. 已验证项（mock 端到端）

用本地 mock 豆包服务（OpenAI 兼容响应，带 ```csv 围栏与前后缀文字，注入 3 处模拟识别误差）跑通：

- **调用链路**：请求构造正确（`Authorization: Bearer`、base64 图片、model、temperature=0.1）；响应解析、CSV 围栏剥离、`--output` 落盘正常。
- **结构兼容（关键）**：模型输出的 `成员名,排名,总星数,进攻1摧毁率,进攻2摧毁率` 30 行 CSV 直接喂给 `CsvImporter.parse`（`slotCount=2`）→ Success，30 成员，rank 与行序对齐，未进攻行 0 星 0 摧毁，错字名字照常入库（`DoubaoOcrCsvValidationTest` 3 用例全绿）。
- **比对工具**：准确检出 3 处注入误差（名字错字 1、摧毁率错 2），指标输出正常。

## 3. 真实数据实测（2025-08-17，45 人部落战）

数据：`C:\Users\flechazo\OneDrive\桌面\test\test1`（10 张 2388×1080 截图，每屏 6 人滚动截屏），正确结果 `result1\result.txt`（45 行，列序 `排名,成员名,总星数,进攻1摧毁率,进攻2摧毁率`；result2 与截图不吻合，未采用）。

方法：`doubao-ocr.mjs` 逐屏识别（本机暂无豆包 Key，复用已配置的千问 `qwen3.5-omni-plus` 兼容端点实测；豆包 Key 就绪后同命令复测）→ 按排名聚合 → 逐列合并（规则：数值合法性 星数∈0..6 / 摧毁率∈0..100，多屏分歧时优先非 0，平局取末屏）→ 与 result1 比对。

| 指标 | 结果 | 判定（≥95%） |
|---|---|---|
| 行数完整性 | 45/45 (100%) | ✅ |
| 排名 | 45/45 (100%) | ✅ |
| 总星数 | 45/45 (100%) | ✅ |
| 进攻1摧毁率 | 45/45 (100%) | ✅ |
| 进攻2摧毁率 | 45/45 (100%) | ✅ |
| 成员名精确 | 40/45 (88.9%) | — |
| 成员名模糊（difflib ≥0.8） | 43/45 (95.6%) | ✅ |

5 处名字差异均为低风险：大小写 `GuoAn→GuOAN`、`MaNgo→Mango`，形近字 `請詶→請訓`、`一/ー、丶/、`，漏字 `余味1→余味`——App 现有花名册模糊匹配（`StringMatcher` Levenshtein）可兜底纠正。

> 说明：单屏识别存在个别数值误差（如混子攻1 100→0、不用醉微醺就好啦 星数 6→12），多屏重叠投票合并后可 100% 消除——集成方案建议保留多屏确认或对异常值（星数>6 等）做合法性校验。

## 4. 真实准确度重跑步骤（含豆包复测）

> **豆包复测状态（2025-08-17）**：用户已提供 `DOUBAO_API_KEY`（`ark-` 开头，已写入 `scripts/.env`，Key 鉴权通过——API 返回 404 而非 401）。
> **阻塞**：火山引擎方舟新账号**不能用模型名直连**，需先在控制台创建**推理接入点**（在线推理 → 创建推理接入点 → 绑定视觉模型如 `doubao-1.5-vision-pro-32k`），得到 `ep-xxx` 后填入 `DOUBAO_MODEL`。当前实测结果基于千问 `qwen3.5-omni-plus` 兼容端点（同脚本同提示词）。

### 4.1 免费替代方案评估（2025-08-17）

| 方案 | 费用 | 实测/评估结论 |
|---|---|---|
| **千问 DashScope**（已配置 Key） | 新用户免费额度 | ✅ **推荐默认识图后端**：实测达标（数值 100%、名字模糊 95.6%）；`doubao-ocr.mjs` 设置 `DOUBAO_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1` + `DOUBAO_MODEL=qwen3.5-omni-plus` 即可切换 |
| **本地 RapidOCR**（PaddleOCR ONNX 版） | 完全免费、离线 | ❌ 实测不达预期：名字错字/粘连（`妄司逸→安司逸`、`混子祭天→混子祭天目`）、数值错识（`89%→￥%89`）、星数需解析 `★` 符号、弹窗布局（左列成员+右侧进攻记录）需额外关联逻辑；不推荐 |
| **SiliconFlow 硅基流动** | 注册送约 ¥14 额度 | 国内直连，提供 Qwen2.5-VL 等开源视觉模型；OpenAI 兼容，注册后把 Key 填入 `scripts/.env` 即可接入（`DOUBAO_BASE_URL=https://api.siliconflow.cn/v1`） |
| **Groq llama-3.2-90b-vision** | 真免费（限速） | OpenAI 兼容；国外服务，国内访问稳定性待测，注册后同样可接入 |
| **Google Gemini 免费层** | 免费 | 需代理与 Google 账号，未评估 |

```bash
# 1. 配置凭证（一次性）
#    编辑 scripts/.env：
#      DOUBAO_API_KEY=ark-xxxx（已配置）
#      DOUBAO_MODEL=ep-xxxxxx   ← 替换为控制台创建的推理接入点 ID
#    真实战报截图放任意路径（用户提供优先，也可重新生成模拟图）

# 2. 识别（逐屏）
node scripts/doubao-ocr.mjs <截图路径> --output recognition.csv

# 3. 比对（ground truth 与识别 CSV 对齐后）
python compare_csv.py recognition.csv <ground-truth>.csv

# 4. 结构回归（确认新输出仍可导入）
./gradlew :COCtools:testDebugUnitTest --no-daemon \
  --tests "com.cocwar.data.csv.DoubaoOcrCsvValidationTest"
```

## 5. 已知失败模式与兜底设计建议（集成阶段参考）

1. **成员名错字**（mock 已验证工具可检出）：App 现有花名册模糊匹配预览（`StringMatcher` + Levenshtein）正好兜底——识别名与花名册匹配时以花名册为准、命中率低时提示人工核对。
2. **摧毁率个位数误差**（如 93 vs 95）：对统计口径影响极小（均摧毁率偏差 <1%）；可在导入预览界面高亮可疑值（非 0/100 且与花名册历史均差 >10%）供人工确认。
3. **非 CSV 输出 / 围栏残留**：脚本已做围栏剥离与表头定位兜底；集成时对 `CsvImporter` 解析失败给出「识别结果不可解析，请重试或手动粘贴」的可读提示。
4. **未进攻成员**：截图无数据显示时模型须输出 0/0（提示词已约束），`CsvImporter` 解析为 0 星 0 摧毁，与现有口径一致。

## 6. 结论

- **可行**：豆包（火山 Ark）OpenAI 兼容接口 + 严格 CSV 提示词，输出可直接复用现有 `CsvImporter` 导入链路，结构兼容性已实证（30 行 mock 全量解析、列不错位、可入库；45 行真实数据同构）。
- **准确度达标**：真实数据实测（45 人，千问 `qwen3.5-omni-plus` 兼容端点）数值类 100%、成员名模糊 95.6%，达到计划判定标准（≥95%）。豆包本体 Key 就绪后按第 4 节命令复测确认。
- **建议**：启动 Android 集成（导入页「截图识别」入口 + `HttpURLConnection` 调用 + `SecurePrefs` 存 Key + 花名册模糊匹配兜底 + 星数合法性校验/多屏确认）。

## 7. 集成落地（v7，已完成）

- **`data/ocr/`**：`OcrConfig`（SecurePrefs 加密存 Key，默认千问端点）、`OcrClient`（HttpURLConnection + 可注入 factory，错误映射）、`OcrCsvExtractor`（围栏剥离）、`OcrValidation`（星数 0-6/摧毁率 0-100 校验）、`OcrPrompts`（与验证脚本一致）
- **设置 → 识图设置**：API Key 输入（明文/密文切换，AndroidKeyStore 加密）、BaseURL/模型高级项（可换豆包/SiliconFlow）
- **导入页「截图识别」**：选图 → 压缩（最长边 1600 + JPEG 80）→ 识别 → CSV 填入 → 复用现有解析/花名册匹配预览/保存链路；识别中 loading、错误分级提示、可疑数值警告
- 测试：ocr 21 用例全绿，全量 193 用例全绿，debug APK 构建成功
- 待办（后续迭代）：多屏投票合并（验证证明可消除单屏误差）、真实设备端到端手测

## 8. 提示词 v2（2026-09-07，针对 §3 实测错误）

针对 §3 真实 45 人样本里的两类误差——名字错字（5 处，大小写/形近字/漏字，88.9% 精确 / 95.6% 模糊）与单屏数值跳变（星数 6→12、摧毁率 100→0）—— 重写 `OcrPrompts.kt` 与 `scripts/doubao-ocr.mjs`，两侧保持同口径。

**结构化分段（顺序固定）**：角色 → 输出格式（严格 CSV）→ 本次赛事（war/league/auto）→ 字段口径 → 自洽性检查 → 成员名转录 → 部落在册成员名单 → 名单匹配规则 → 易混名字提醒。

**v2 相对 v1 的新增点**：

1. **花名册注入**：以「张三、李四、…」紧凑格式写入（每行 6 个名字），上限 60 人；提示词明确"名字错字回写到名单写法"——把第 1 道闸口提前到模型内部。
2. **易混名提醒**：自动从花名册挑出相似对（`isLikelySameName` 阈值 ≥0.5，等长一字之差兜底），最多 12 对，逐字提示——直接把最易错的对照写进 prompt，减少第 1 类错误漏出。
3. **赛事模式参数**：调用方显式传 `war` / `league` / `auto`；`league` 时提示词强制「进攻2摧毁率」列留空，`war` 时强制两列都填——避免列错位。
4. **数值自洽性检查**：写进 prompt——100%⇔3 星、≥50% 至少 1 星、0 星<50%、总星=两次之和且≤6/≤3、摧毁率只能在 0-100 整数；提示模型在输出前自纠第二类跳变。

**A/B 复测命令**（脚本现已支持 `--roster` / `--mode` / `--legacy`，配置有效 Key 后可一键对比新旧提示词）：

```bash
# v2：含花名册 + 易混名 + 部落战模式
node scripts/doubao-ocr.mjs <截图.png> \
  --roster "张三,李四,王五,赵六" --mode war \
  --output ab_new.csv

# v1（旧提示词）：做对照
node scripts/doubao-ocr.mjs <截图.png> --legacy --output ab_old.csv
```

## 9. 功能转向：App 不再调用 AI，只提供提示词（2026-09-07）

**变更**：彻底移除 App 内的 AI 识图调用，识别交由用户在豆包等外部软件完成，App 只生成并复制提示词。

| 类别 | 处理 |
|---|---|
| `OcrClient` / `OcrConfig` / `OcrProvider` | 删除（不再有 API Key、端点、模型配置） |
| `OcrBatchService` / `OcrBatchScreen` / `OcrSettingsScreen` | 删除（批量识图、识图设置下线） |
| `ScreenshotGrouper` / `OcrCsvAggregator` | 删除（截图分组与多屏聚合随批量识图下线） |
| `pending_imports` 表 | 删除，DB v8→v9（`MIGRATION_8_9` DROP TABLE） |
| `OcrPrompts` | **保留并强化**：按花名册动态生成提示词（见 §8） |
| `OcrCsvExtractor` / `OcrValidation` | 保留：外部识别结果粘贴回来时仍要剥围栏 + 校验数值 |
| 悬浮球 / 无障碍截屏 / 截图浏览 | **保留**：用户仍需截图，再拿去外部软件识别 |
| `scripts/doubao-ocr.mjs` | 保留为 App 之外的**提示词验证工具**（`--roster` / `--mode` / `--legacy`），不打包进 APK |

**新流程**：截图（悬浮球） → App 复制提示词（导入页右上⧉ 或 设置 → 识别提示词） → 外部 AI（豆包等）识别 → 复制 CSV → 回 App 导入页粘贴 → 解析预览 → 保存。

**提示词入口**：
- 导入页：右上角复制图标，按当前赛事类型（部落战/联赛）与花名册动态生成；
- 设置 → 识别提示词（`PromptScreen`）：可切换部落战/联赛、查看全文、一键复制。

> §1-§7 为历史验证记录（App 内置识图时期），保留供提示词迭代参考；§8 的提示词 v2 为历史版本，
> 已被 §10 的 v4 取代（v4 移除数值互推、引入「独立观察值」与「-1 看不清标记」）。

## 10. 提示词 v4 + CSV 唯一格式 + -1 标记（2026-09-07）

针对「总星数与摧毁率之间没有直接关系」这一关键口径，以及「看不清不能一味填 0」的需求，
提示词升级为 **v4**，并彻底下线 JSON、统一 CSV：

**v4 相对 v2 的关键变更**：

1. **独立观察值约束（新增）**：明确「总星数来源于图中右侧数字显示、摧毁率来源于左侧进度数字，
   二者没有直接换算关系」——摧毁率 100% 不一定是 3 星、30% 也不一定是 0 星，一律以图中右侧
   数字为准；禁止用摧毁率反推星数或反之。
2. **自洽性检查改为「范围核对」**：删除 v2 里的 `100%⇔3 星`、`≥50% 至少 1 星`、`总星=两次之和`
   等互推规则，只保留范围（星数 0-6/0-3、摧毁率 0-100）与排版（排名连续、不并行不漏行）核对。
3. **看不清填 `-1`（新增）**：数字被遮挡/模糊/反光/截断时填 `-1` 作「待确认」标记，绝不填 0
   冒充看清、绝不猜数；`-1` 在提示词中声明为合法标记、校验时豁免（`OcrValidation` 不判越界）。
4. **CSV 唯一数据格式**：软件不再使用 JSON，导入/导出/备份全部 CSV（备份为 ZIP 多 CSV）；
   提示词强制输出的 CSV 与 `RULES.md §4.15` 单事件格式完全一致。

**`-1` 的软件处理（方案 A）**：`CsvImporter` / `EventBuilder` 原样保留 `-1`（不判越界、不猜测）；
导入预览 `ImportPreviewDialog` 检测到 `-1` 字段时弹二次确认，确认后按 0 入库（库内不保留 `-1`）；
`-1` 不参与部落总星数求和、不参与摧毁率平均。

> 提示词 v4 与 `scripts/doubao-ocr.mjs` 的 `buildPromptV2` 已同步为同口径（`--legacy` 保留 v1 对照）。
