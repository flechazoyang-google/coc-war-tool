#!/usr/bin/env node
/**
 * 豆包大模型识图 → CSV 验证脚本（火山引擎 Ark，OpenAI 兼容接口）。
 *
 * 快速验证：把一张部落冲突部落战/联赛战报截图喂给豆包视觉模型，
 * 要求模型严格输出 CSV（成员名,排名,总星数,进攻1摧毁率,进攻2摧毁率），
 * 输出可直接粘贴进 App 的"CSV 数据"导入框（RULES §4.15 单事件格式）。
 *
 * 用法:
 *   node doubao-ocr.mjs <图片路径> [--roster "名字1,名字2"] [--mode war|league|auto]
 *                       [--legacy] [--prompt "自定义提示词"] [--output out.csv] [--json]
 *   --roster  注入在册成员名单（默认不注入）；名单内形近/一字之差的名字会自动生成「易混名提醒」
 *   --mode    war=部落战（两列摧毁率）/ league=联赛（进攻2留空）/ auto=由模型据图判断（默认）
 *   --legacy  用旧版（v1）提示词，便于与 v4 做 A/B 对比
 *
 * 提示词 v4 与 App `data/ocr/OcrPrompts.kt` 同口径（2026-09-07 同步）：结构化分段 +
 * 花名册注入 + 易混名提醒 + 独立观察值 + 范围核对（不做数值互推）；改任一侧都要同步另一侧。
 *
 * 配置（脚本同目录 .env，已被 .gitignore 忽略）:
 *   DOUBAO_API_KEY=xxx          # 火山引擎 Ark API Key（控制台-API Key 管理）
 *   DOUBAO_MODEL=doubao-1.5-vision-pro-32k-250115   # 模型名或推理接入点 ep-xxx
 *   DOUBAO_BASE_URL=https://ark.cn-beijing.volces.com/api/v3   # 默认即可
 *
 * 零依赖：不依赖 npm 包，.env 用手写解析（与 vision skill 的 dotenv 解耦）。
 */

import fs from "node:fs";
import path from "node:path";
import https from "node:https";
import http from "node:http";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

// ---------- .env 加载（零依赖手写解析） ----------
function loadEnv(dir) {
  const file = path.resolve(dir, ".env");
  if (!fs.existsSync(file)) return;
  const lines = fs.readFileSync(file, "utf8").split(/\r?\n/);
  for (const line of lines) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("#")) continue;
    const eq = trimmed.indexOf("=");
    if (eq <= 0) continue;
    const key = trimmed.slice(0, eq).trim();
    let value = trimmed.slice(eq + 1).trim();
    if (
      (value.startsWith('"') && value.endsWith('"')) ||
      (value.startsWith("'") && value.endsWith("'"))
    ) {
      value = value.slice(1, -1);
    }
    if (!(key in process.env)) process.env[key] = value;
  }
}
loadEnv(__dirname);

const BASE_URL = process.env.DOUBAO_BASE_URL || "https://ark.cn-beijing.volces.com/api/v3";
const API_KEY = process.env.DOUBAO_API_KEY || "";
const MODEL = process.env.DOUBAO_MODEL || "";

const HEADER = "成员名,排名,总星数,进攻1摧毁率,进攻2摧毁率";

/** 旧版（v1）提示词：保留供 --legacy 复测 / 兼容旧测试夹具。 */
const LEGACY_PROMPT = `你是一个部落冲突战报数据录入助手。请识别图片中的部落战/联赛战报表格，并严格按以下 CSV 格式输出，不要输出任何其他文字、解释、Markdown 代码围栏或前后缀：

${HEADER}

要求：
1. 第一行必须是表头，之后每行一个成员，按图片中的序号排列；
2. "排名"为图中序号，从 1 开始；
3. "总星数"为该成员本场进攻获得的总星数（部落战两次进攻合计 0-6，联赛一次进攻 0-3）；
4. 摧毁率为整数百分比，去掉 % 符号（如 87 表示 87%），未进攻填 0；
5. 联赛战报的"进攻2摧毁率"列留空；
6. 成员名必须与图中写法一致，禁止改写、加前后缀或省略（若下方附有在册成员名单，按名单匹配规则执行）；
7. 图片中没有成员数据时，只输出表头行；
8. 只输出 CSV 文本本身。`;

// ================ v4 提示词（与 App OcrPrompts.kt 对齐） ================

function sanitize(names) {
  const seen = new Set();
  const out = [];
  for (const n of names) {
    const t = (n || "").trim();
    if (!t || seen.has(t)) continue;
    seen.add(t); out.push(t);
  }
  return out;
}
function levenshtein(a, b) {
  const m = a.length, n = b.length;
  if (!m) return n; if (!n) return m;
  const dp = Array.from({ length: m + 1 }, (_, i) => {
    const row = new Array(n + 1); row[0] = i;
    for (let j = 1; j <= n; j++) row[j] = j;
    return row;
  });
  for (let i = 1; i <= m; i++)
    for (let j = 1; j <= n; j++)
      dp[i][j] = a[i - 1] === b[j - 1] ? dp[i - 1][j - 1] : 1 + Math.min(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1]);
  return dp[m][n];
}
function similarity(a, b) {
  const m = Math.max(a.length, b.length);
  return m ? 1 - levenshtein(a, b) / m : 1;
}
function stripNumberSuffix(name) {
  return name.replace(/\d+$/, "");
}
function isNumberedVariant(a, b) {
  if (a === b) return false;
  const sa = stripNumberSuffix(a), sb = stripNumberSuffix(b);
  return sa.length > 0 && sa === sb;
}
function isLikelySameName(a, b) {
  if (a === b) return false; if (!a || !b) return false;
  if (isNumberedVariant(a, b)) return false; // 编号变体是不同成员，不是错字
  if (similarity(a, b) >= 0.5) return true;
  return a.length === b.length && a.length >= 2 && levenshtein(a, b) === 1;
}
function findConfusables(roster, limit = 12) {
  const pairs = [];
  for (let i = 0; i < roster.length; i++)
    for (let j = i + 1; j < roster.length; j++)
      if (isLikelySameName(roster[i], roster[j])) pairs.push([roster[i], roster[j]]);
  pairs.sort((p, q) => similarity(q[0], q[1]) - similarity(p[0], p[1]));
  return pairs.slice(0, limit);
}

function buildPromptV2(roster, mode) {
  const modeText = mode === "war"
    ? "本次是**部落战**：每名成员有 2 次进攻，「进攻1摧毁率」与「进攻2摧毁率」两列都要填写。"
    : mode === "league"
    ? "本次是**部落对战联赛**：每名成员只有 1 次进攻，「进攻2摧毁率」列必须留空，只填「进攻1摧毁率」。"
    : "先判断截图类型：每人 2 次进攻 = 部落战（两列摧毁率都填）；每人 1 次进攻 = 联赛（「进攻2摧毁率」列留空）。";

  const clean = sanitize(roster);
  let rosterText = "";
  if (clean.length > 0) {
    const chunked = [];
    for (let i = 0; i < clean.length; i += 6) chunked.push(clean.slice(i, i + 6).join("、"));
    const conf = findConfusables(clean);
    rosterText = `\n\n# 部落在册成员名单（共 ${clean.length} 人，用于校正名字错字）\n${chunked.join("\n")}\n\n# 名单匹配规则\n1. 图中名字与名单中某个名字高度相似（形近字/异体字、仅大小写差异、缺字或多字不超过 1 个）时，输出名单中的写法。\n2. 判断不了归属时，按图中写法原样输出；名单只是校正工具，不是候选集。\n3. 禁止臆造名单中不存在、图中也没有的名字；禁止把两个不同成员写成同一个名字。\n4. 末尾数字编号不同 = 不同成员（余味 / 余味1 是两个人，各自都有独立的一行数据），不要相互纠正、不要合并成一个人。`
      + (conf.length ? `\n\n# 易混名字提醒（必须逐字核对）\n下列名字字形或拼写极其接近，是最容易识别错的一组，请对照截图逐个字确认，不要互相串写：\n${conf.map(([x, y]) => `${x} ↔ ${y}`).join("\n")}` : "");
  }

  return `# 角色
你是《部落冲突》战报数据转录助手：把战报截图中的成员列表逐行、逐字转录为 CSV。
准确性第一：看不清就填 -1 作标记，绝不猜测、不填 0 冒充看清。

# 输出格式（严格遵守）
第一行必须是下面这行表头，一字不改：
${HEADER}
随后每行一名成员，恰好 5 列，用英文逗号分隔，列内不要加空格。
只输出 CSV 本身：禁止任何解释、标题、Markdown 代码块围栏、行号或项目符号（排名列本身就是序号）。
图中没有任何成员数据时，只输出表头行。

# 本次赛事
${modeText}

# 字段口径
- 排名：图中该成员的序号，从 1 开始连续递增，不要重排、不要跳号。
- 总星数：直接抄写图右侧显示的数字，部落战 0-6、联赛 0-3。
- 摧毁率：整数百分比，去掉 % 号（87 表示 87%），范围 0-100。
- 未进攻、没有进攻记录：总星数填 0、摧毁率填 0。
- 看不清（数字被遮挡、模糊、反光、被滚动条截断等）：填 -1，作为「待确认」标记，
  交给使用者人工核对；绝不填 0 冒充看清、也绝不猜一个数。

# 关键约束：总星数与摧毁率是独立观察值
总星数来源于图中右侧的数字显示；摧毁率来源于左侧的进度数字，二者没有直接换算关系：
- 摧毁率 100% 不一定是 3 星，摧毁率 30% 也不一定是 0 星，一律按图中右侧数字为准。
- 不要用摧毁率反推星数，也不要用星数反推摧毁率。
- 出现「按摧毁率算星数才对得上」的感觉时，仍以图中可见数字为准。

# 自洽性检查（仅做范围与排版核对，不做数值互推）
- 总星数：正常为 0-6（部落战）/ 0-3（联赛）；-1 是合法的「看不清」标记，保留原样。
- 摧毁率：正常为 0-100；-1 是合法的「看不清」标记，保留原样。
- 排名：从 1 开始连续递增、无重号、无跳号；不连续说明漏人或合并。
- 不要合并相邻两行，也不要漏掉被滚动条截断的半行成员；每名成员在结果中只出现一次。

# 成员名转录
- 逐字抄写图中的名字，保留原字符与大小写，不翻译、不改写、不加空格或标点。
- 忽略头像、职位徽章、等级数字与装饰符号，它们不是名字的一部分。
- 名字末尾的数字是同名成员的区分编号（如 余味 / 余味1 / 余味2 是三名不同成员）：
  必须原样保留，不要当成笔误删除，也不要合并成一个人。${rosterText}`.trim();
}

function parseArgs() {
  const argv = process.argv.slice(2);
  let image = "", rosterRaw = "", mode = "auto", legacy = false;
  let customPrompt = "", output = "", showJson = false;
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === "--prompt" && argv[i + 1]) customPrompt = argv[++i];
    else if (a === "--output" && argv[i + 1]) output = argv[++i];
    else if (a === "--roster" && argv[i + 1]) rosterRaw = argv[++i];
    else if (a === "--mode" && argv[i + 1]) mode = argv[++i];
    else if (a === "--legacy") legacy = true;
    else if (a === "--json") showJson = true;
    else if (!a.startsWith("--") && !image) image = a;
    else if (!a.startsWith("--")) customPrompt = customPrompt ? customPrompt + " " + a : a;
  }
  let prompt = customPrompt;
  if (!prompt) {
    if (legacy) prompt = LEGACY_PROMPT;
    else prompt = buildPromptV2(rosterRaw ? rosterRaw.split(/[,，]\s*/).filter(Boolean) : [], mode);
  }
  return { image, prompt, output, showJson };
}

function resolveImageDataUrl(source) {
  const resolved = path.resolve(source);
  if (!fs.existsSync(resolved)) throw new Error(`文件不存在: ${resolved}`);
  const ext = path.extname(resolved).toLowerCase().replace(".", "");
  const mimeMap = { jpg: "jpeg", jpeg: "jpeg", png: "png", gif: "gif", webp: "webp", bmp: "bmp" };
  const data = fs.readFileSync(resolved);
  return `data:image/${mimeMap[ext] || "jpeg"};base64,${data.toString("base64")}`;
}

function request(payload) {
  const url = new URL(BASE_URL.replace(/\/?$/, "/") + "chat/completions");
  const body = JSON.stringify(payload);
  const transport = url.protocol === "https:" ? https : http;
  return new Promise((resolve, reject) => {
    const req = transport.request(url, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${API_KEY}`,
        "Content-Type": "application/json",
        "Content-Length": Buffer.byteLength(body),
      },
    }, (res) => {
      let data = "";
      res.on("data", (c) => (data += c));
      res.on("end", () => {
        let parsed;
        try { parsed = JSON.parse(data); } catch { return reject(new Error(`响应非 JSON（HTTP ${res.statusCode}）: ${data.slice(0, 500)}`)); }
        if (res.statusCode < 200 || res.statusCode >= 300) {
          const msg = parsed?.error?.message || JSON.stringify(parsed).slice(0, 500);
          return reject(new Error(`API 错误（HTTP ${res.statusCode}）: ${msg}`));
        }
        resolve(parsed);
      });
    });
    req.on("error", reject);
    req.setTimeout(120000, () => { req.destroy(new Error("请求超时（120s）")); });
    req.write(body);
    req.end();
  });
}

/** 从模型输出中剥离 Markdown 代码围栏与首尾空白，提取 CSV 文本。 */
function extractCsv(content) {
  let text = content.trim();
  const fence = text.match(/```(?:csv)?\s*([\s\S]*?)```/i);
  if (fence) text = fence[1].trim();
  // 丢弃表头之前的多余解释行：找表头行起点
  const lines = text.split(/\r?\n/);
  const headerIdx = lines.findIndex((l) => l.includes("成员名") && l.includes("排名"));
  if (headerIdx > 0) text = lines.slice(headerIdx).join("\n");
  return text.trim();
}

async function main() {
  const { image, prompt, output, showJson } = parseArgs();
  if (!image) {
    console.error("用法: node doubao-ocr.mjs <图片路径> [--roster \"名字1,名字2\"] [--mode war|league|auto]");
    console.error("                              [--legacy] [--prompt \"自定义提示词\"] [--output out.csv] [--json]");
    process.exit(2);
  }
  if (!API_KEY) {
    console.error("缺少 DOUBAO_API_KEY：请在 scripts/.env 中配置（参考脚本头部注释）。");
    process.exit(2);
  }
  if (!MODEL) {
    console.error("缺少 DOUBAO_MODEL：请在 scripts/.env 中配置模型名或推理接入点 ep-xxx。");
    process.exit(2);
  }

  const imageUrl = resolveImageDataUrl(image);
  const payload = {
    model: MODEL,
    messages: [
      {
        role: "user",
        content: [
          { type: "image_url", image_url: { url: imageUrl } },
          { type: "text", text: prompt },
        ],
      },
    ],
    temperature: 0.1,
    max_tokens: 4096,
  };

  const resp = await request(payload);
  const content = resp?.choices?.[0]?.message?.content ?? "";
  if (showJson) {
    console.log(JSON.stringify(resp, null, 2));
    return;
  }
  const csv = extractCsv(content);
  console.log(csv);
  if (output) {
    fs.writeFileSync(path.resolve(output), csv + "\n", "utf8");
    console.error(`\n[已写入] ${path.resolve(output)}`);
  }
}

main().catch((e) => {
  console.error(`识别失败: ${e.message}`);
  process.exit(1);
});
