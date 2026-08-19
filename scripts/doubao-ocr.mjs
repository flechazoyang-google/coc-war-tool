#!/usr/bin/env node
/**
 * 豆包大模型识图 → CSV 验证脚本（火山引擎 Ark，OpenAI 兼容接口）。
 *
 * 快速验证：把一张部落冲突部落战/联赛战报截图喂给豆包视觉模型，
 * 要求模型严格输出 CSV（成员名,排名,总星数,进攻1摧毁率,进攻2摧毁率），
 * 输出可直接粘贴进 App 的"CSV 数据"导入框（RULES §4.15 单事件格式）。
 *
 * 用法:
 *   node doubao-ocr.mjs <图片路径> [--prompt "自定义提示词"] [--output out.csv] [--json]
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

const DEFAULT_PROMPT = `你是一个部落冲突战报数据录入助手。请识别图片中的部落战/联赛战报表格，并严格按以下 CSV 格式输出，不要输出任何其他文字、解释、Markdown 代码围栏或前后缀：

成员名,排名,总星数,进攻1摧毁率,进攻2摧毁率

要求：
1. 第一行必须是表头，之后每行一个成员，按图片中的序号排列；
2. "排名"为图中序号，从 1 开始；
3. "总星数"为该成员本场进攻获得的总星数（部落战两次进攻合计 0-6，联赛一次进攻 0-3）；
4. 摧毁率为整数百分比，去掉 % 符号（如 87 表示 87%），未进攻填 0；
5. 联赛战报的"进攻2摧毁率"列留空；
6. 成员名必须与图中完全一致，禁止改写、加前后缀或省略；
7. 图片中没有成员数据时，只输出表头行；
8. 只输出 CSV 文本本身。`;

function parseArgs() {
  const argv = process.argv.slice(2);
  let image = "", prompt = "", output = "", showJson = false;
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === "--prompt" && argv[i + 1]) { prompt = argv[++i]; }
    else if (a === "--output" && argv[i + 1]) { output = argv[++i]; }
    else if (a === "--json") { showJson = true; }
    else if (!a.startsWith("--") && !image) { image = a; }
    else if (!a.startsWith("--")) { prompt = prompt ? prompt + " " + a : a; }
  }
  return { image, prompt: prompt || DEFAULT_PROMPT, output, showJson };
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
    console.error("用法: node doubao-ocr.mjs <图片路径> [--prompt 提示词] [--output out.csv] [--json]");
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
