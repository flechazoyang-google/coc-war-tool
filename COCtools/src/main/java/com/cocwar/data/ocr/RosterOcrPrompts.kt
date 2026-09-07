package com.cocwar.data.ocr

/**
 * 花名册识别提示词构建（「成员更新」入口用）。
 *
 * 与战报提示词 [OcrPrompts] 同套路，但目标不同：这里是**成员名单**，
 * 输出 `名字,职位` 两列，用于与现有花名册比对出「新增 / 退出 / 职位变化」。
 *
 * App 自身不调用任何 AI 接口：用户在花名册页复制提示词 → 到豆包等外部软件识别
 * 游戏内「部落 → 成员」页截图 → 把结果粘贴回 App。
 *
 * 三个关键设计：
 * 1. **注入当前花名册**：让模型在源头把错字纠回花名册写法，退出/新增的判断才准；
 * 2. **易混名提醒**：复用 [OcrPrompts.findConfusables] 算出形近名，要求逐字核对
 *    （已排除「余味 / 余味1」这类编号变体，避免诱导模型把两个人合并）；
 * 3. **多图合并**：长名单常需滚动分屏截图，明确要求合并去重，避免同一个人出现两行。
 */
object RosterOcrPrompts {

    /** 输出表头（与 RosterTextParser 的解析口径一致）。 */
    const val HEADER = "名字,职位"

    /** 无花名册时的默认提示词。 */
    val DEFAULT: String get() = build(emptyList())

    /** 按当前花名册构建提示词（名单为空时给出不含名单段落的通用版）。 */
    fun build(rosterNames: List<String>): String = buildPrompt(OcrPrompts.cleanRoster(rosterNames))

    private fun buildPrompt(names: List<String>): String {
        val sb = StringBuilder()
        sb.append(SECTION_ROLE).append('\n').append('\n')
        sb.append(SECTION_FORMAT).append('\n').append('\n')
        sb.append(SECTION_SCOPE).append('\n').append('\n')
        sb.append(SECTION_FIELDS).append('\n').append('\n')
        if (names.isEmpty()) {
            sb.append(SECTION_NAME_RULES)
            return sb.toString().trimEnd()
        }
        sb.append(SECTION_NAME_RULES).append('\n').append('\n')
        sb.append(sectionRoster(names)).append('\n').append('\n')
        sb.append(SECTION_MATCH_RULES)
        val confusables = OcrPrompts.findConfusables(names)
        if (confusables.isNotEmpty()) {
            sb.append('\n').append('\n').append(sectionConfusables(confusables))
        }
        sb.append('\n').append('\n').append(SECTION_CONSISTENCY)
        return sb.toString().trimEnd()
    }

    private fun sectionRoster(names: List<String>): String {
        val body = names.chunked(ROSTER_PER_LINE).joinToString("\n") { line -> line.joinToString("、") }
        return "# 当前在册名单（共 " + names.size + " 人）\n" + body
    }

    private fun sectionConfusables(pairs: List<Pair<String, String>>): String {
        val body = pairs.joinToString("\n") { (a, b) -> "$a ↔ $b" }
        return "# 易混名字提醒（必须逐字核对）\n" +
            "下列名字字形或拼写极其接近，是最容易识别错的一组，请对照截图逐个字确认，不要互相串写：\n" +
            body
    }

    // ==================== 文本片段 ====================

    private val SECTION_ROLE = """
        # 角色
        你是《部落冲突》部落成员名单转录助手：把成员列表截图逐行转录为 CSV，用于更新部落花名册。
        准确性第一：看不清就标记，绝不猜测、绝不编造。
    """.trimIndent()

    private val SECTION_FORMAT = """
        # 输出格式（严格遵守）
        第一行必须是下面这行表头，一字不改：
        $HEADER
        随后每行一名成员，恰好 2 列，用英文逗号分隔，列内不要加空格。
        只输出 CSV 本身：禁止任何解释、标题、Markdown 代码块围栏、行号或项目符号。
        我会分多张截图发给你，它们属于同一份名单：全部截图合并成一个 CSV，
        同一名成员只输出一次（按首次出现的顺序），不要因为出现在多张图里就重复输出。
    """.trimIndent()

    private val SECTION_SCOPE = """
        # 截图口径
        - 只转录「我的部落」成员列表中「家乡」标签页下的当前成员。
        - 不要转录「建筑大师基地」「部落都城」标签页的内容。
        - 每行成员包含：序号、头像、名字（下方小字为职位或登录信息）、近期捐播/近期收到、奖杯数、联赛徽章。
          只需要名字和职位，其余（序号、头像、捐收数字、奖杯、徽章、装饰符号）一律忽略。
    """.trimIndent()

    private val SECTION_FIELDS = """
        # 字段口径
        - 名字：逐字抄写，保留原字符与大小写，不翻译、不改写、不加空格或标点。
        - 职位：看名字下方的小字，只能是 首领 / 副首领 / 长老（图中或写作「长者」）/ 成员 四种；
          那一行若是「最近登录」等信息而非职位，视为普通成员填「成员」。
        - 名字末尾的数字是同名成员的区分编号（如 余味 / 余味1 / 余味2 是不同成员），
          必须原样保留，不要当成笔误删除，也不要合并成一个人。
        - 名字实在看不清：整行写成 ?,成员，不要猜一个名字。
    """.trimIndent()

    private val SECTION_NAME_RULES = """
        # 成员名转录
        - 逐字抄写图中的名字，保留原字符与大小写。
        - 名字末尾的数字是同名成员的区分编号，必须原样保留（余味 / 余味1 是两个人）。
    """.trimIndent()

    private val SECTION_MATCH_RULES = """
        # 名单校正
        1. 图中名字与名单中某个名字高度相似（形近字/异体字、仅大小写差异、缺字或多字不超过 1 个）时，
           输出名单中的写法——这是在册老成员，不是新人。
        2. 判断不了归属时，按图中写法原样输出；名单只是校正工具，不是候选集。
        3. 禁止臆造名单中不存在、图中也没有的名字；禁止把两个不同成员写成同一个名字。
        4. 末尾数字编号不同 = 不同成员（余味 / 余味1 是两个人），不要相互纠正、不要合并。
    """.trimIndent()

    private val SECTION_CONSISTENCY = """
        # 自洽性检查
        - 每张截图内的成员按序号连续，无漏行、无重复、不合并相邻两行。
        - 合并所有截图后，同一名成员不得出现两次。
    """.trimIndent()

    /** 名单每行展示的名字数（紧凑排布，省 token 且便于模型扫描）。 */
    private const val ROSTER_PER_LINE = 6
}
