package com.cocwar.ui.importflow

object CopyPrompts {

    const val JSON_PROMPT = """你是一个部落冲突战报数据录入助手。请识别图片中的部落战/联赛战报表格，严格按以下 JSON 格式输出，不要输出任何其他文字、解释或 Markdown 代码围栏。

格式说明：
- 顶层只有一个字段 "members"，是成员数组
- 每个成员对象包含：
  - "player_name"：成员名（与图中完全一致，禁止改写）
  - "total_stars"：该成员本场获得的总星数（整数）
  - "attacks"：进攻数组，每次进攻包含 "attack_order"（进攻序号，从1开始）和 "destruction_percentage"（摧毁率，整数0-100，去掉%符号）
- 部落战每人最多2次进攻，联赛每人1次进攻
- 未进攻的成员 attacks 为空数组，total_stars 为 0
- 如果是联赛，在顶层加一个字段 "season": "YYYY-MM"（如 "2026-08"）

示例（部落战）：
{"members":[{"player_name":"陈平安","total_stars":6,"attacks":[{"attack_order":1,"destruction_percentage":100},{"attack_order":2,"destruction_percentage":92}]},{"player_name":"混子祭天","total_stars":3,"attacks":[{"attack_order":1,"destruction_percentage":100},{"attack_order":2,"destruction_percentage":0}]}]}

只输出 JSON 本身。"""

    const val CSV_PROMPT = """你是一个部落冲突战报数据录入助手。请识别图片中的部落战/联赛战报表格，严格按以下 CSV 格式输出，不要输出任何其他文字、解释或 Markdown 代码围栏。

格式：
成员名,排名,总星数,进攻1摧毁率,进攻2摧毁率

要求：
1. 第一行必须是表头，之后每行一个成员，按图中序号排列
2. "排名"为图中序号，从1开始
3. "总星数"为该成员本场获得的总星数（部落战两次进攻合计0-6，联赛一次进攻0-3）
4. 摧毁率为整数百分比，去掉%符号（如87表示87%），未进攻填0
5. 联赛战报的"进攻2摧毁率"列留空
6. 成员名必须与图中完全一致，禁止改写、加前后缀或省略
7. 只输出 CSV 文本本身，不要加代码围栏或任何前后缀

示例（部落战）：
成员名,排名,总星数,进攻1摧毁率,进攻2摧毁率
陈平安,1,6,100,92
混子祭天,2,3,100,0"""
}
