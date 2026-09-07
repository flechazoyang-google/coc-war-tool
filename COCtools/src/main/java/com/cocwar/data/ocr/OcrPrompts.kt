package com.cocwar.data.ocr

/**
 * 识别提示词构建（与 docs/DOUBAO_OCR_VALIDATION.md §3/§5 的实测失败模式对齐）。
 *
 * 注意：本类仅生成提示词文本，App 自身不调用任何 AI 接口。
 * 用户从导入页或设置页复制提示词后，到豆包等外部软件完成识别。
 *
 * 实测（45 人真实战报）主要误差来源：
 * 1. 成员名错字——大小写（GuoAn→GuOAN）、形近/异体（請詶→請訓）、漏字/多字；
 * 2. 单屏数值跳变——星数 6→12、摧毁率 100→0。
 *
 * 注意：**末尾数字编号不是错字**。部落里同名玩家按顺序编号区分（余味 / 余味1 / 余味2
 * 是不同成员），因此 [findConfusables] 会用 [isNumberedVariant] 排除这类名字对，
 * 并在提示词里要求原样保留编号、不得合并。
 *
 * 对应策略：
 * - **结构化分段**：角色 → 输出格式 → 赛事模式 → 字段口径 → 独立观察值 → 自洽性检查 → 名字转录 → 花名册 → 匹配规则 → 易混名；
 * - **花名册注入**：把在册成员名列表注入，让模型在源头把错字纠回花名册写法；
 * - **易混名提醒**：从花名册自动算出形近/一字之差的名字对，显式要求逐字核对（命中 1 的错误占比最高）；
 * - **独立观察值**：总星数与摧毁率无换算关系，总星数直接抄图右侧数字，禁止用摧毁率反推星数；
 * - **看不清填 -1**：数字无法辨认时填 -1 作为待确认标记，交由用户核对，不猜测、不填 0 冒充。
 *
 * 输出格式与 RULES §4.15 单事件 CSV 完全一致：成员名,排名,总星数,进攻1摧毁率,进攻2摧毁率。
 */
object OcrPrompts {

    /** 输出表头（与 CsvImporter / CsvExporter 同口径）。 */
    const val HEADER = "成员名,排名,总星数,进攻1摧毁率,进攻2摧毁率"

    /** 赛事模式：决定「进攻2摧毁率」列是否填写。 */
    enum class WarMode { WAR, LEAGUE, AUTO }

    /** 无人名册时的完整提示词（AUTO 模式）。用 getter 惰性构建，避开 object 属性初始化顺序问题。 */
    val DEFAULT: String get() = build(emptyList(), WarMode.AUTO)

    // ==================== 对外构建入口 ====================

    /** 按赛事模式构建提示词；名单为空时返回该模式的默认提示词。 */
    fun buildPrompt(rosterNames: List<String>, mode: WarMode = WarMode.AUTO): String =
        build(rosterNames, mode)

    /**
     * 按事件类型字符串构建提示词（UI 层直接传 `EVENT_TYPE_WAR` / `EVENT_TYPE_LEAGUE`）。
     * null / 未知类型按 [WarMode.AUTO]（由模型据图判断）。
     */
    fun buildPrompt(rosterNames: List<String>, eventType: String?): String =
        build(rosterNames, modeOf(eventType))

    /** 事件类型字符串 → [WarMode]。 */
    fun modeOf(eventType: String?): WarMode = when (eventType) {
        com.cocwar.data.model.EVENT_TYPE_LEAGUE -> WarMode.LEAGUE
        com.cocwar.data.model.EVENT_TYPE_WAR -> WarMode.WAR
        else -> WarMode.AUTO
    }

    /**
     * 从花名册中挑出「极易混淆」的名字对：形近字、一字之差、仅大小写差异等。
     * 按相似度降序取前 [limit] 对；不足两词或无可疑对时返回空。
     *
     * 判定口径与 `ui/util/StringMatcher.isLikelySameName` 一致（相似度 ≥ 0.5，
     * 或多字词等长且编辑距离为 1）；此处自带轻量实现，避免 data 层反向依赖 ui 层。
     */
    fun findConfusables(
        names: List<String>,
        limit: Int = MAX_CONFUSABLE_PAIRS
    ): List<Pair<String, String>> {
        val clean = names.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(MAX_ROSTER)
        if (clean.size < 2) return emptyList()
        val pairs = mutableListOf<Pair<String, String>>()
        for (i in clean.indices) {
            for (j in i + 1 until clean.size) {
                val a = clean[i]
                val b = clean[j]
                if (isLikelySameName(a, b)) pairs += a to b
            }
        }
        if (pairs.isEmpty()) return emptyList()
        return pairs
            .sortedWith(
                compareByDescending<Pair<String, String>> { similarity(it.first, it.second) }
                    .thenBy { it.first }
            )
            .take(limit)
    }

    /** 名单清理：去首尾空白、丢弃空串、保持顺序去重。 */
    fun cleanRoster(names: List<String>): List<String> =
        names.map { it.trim() }.filter { it.isNotBlank() }.distinct()

    // ==================== 提示词拼装 ====================

    private fun build(rosterNames: List<String>, mode: WarMode): String {
        val all = cleanRoster(rosterNames)
        val names = all.take(MAX_ROSTER)
        val sb = StringBuilder()
        sb.append(SECTION_ROLE).append('\n').append('\n')
        sb.append(SECTION_FORMAT).append('\n').append('\n')
        sb.append(sectionMode(mode)).append('\n').append('\n')
        sb.append(SECTION_FIELDS).append('\n').append('\n')
        sb.append(SECTION_INDEPENDENCE).append('\n').append('\n')
        sb.append(SECTION_CONSISTENCY).append('\n').append('\n')
        sb.append(SECTION_NAME_RULES)
        if (names.isNotEmpty()) {
            sb.append('\n').append('\n').append(sectionRoster(names, all.size)).append('\n').append('\n')
            sb.append(SECTION_MATCH_RULES)
            val confusables = findConfusables(names)
            if (confusables.isNotEmpty()) {
                sb.append('\n').append('\n').append(sectionConfusables(confusables))
            }
        }
        return sb.toString().trimEnd()
    }

    private fun sectionMode(mode: WarMode): String = when (mode) {
        WarMode.WAR ->
            "# 本次赛事\n本次是**部落战**：每名成员有 2 次进攻，「进攻1摧毁率」与「进攻2摧毁率」两列都要填写。"
        WarMode.LEAGUE ->
            "# 本次赛事\n本次是**部落对战联赛**：每名成员只有 1 次进攻，「进攻2摧毁率」列必须留空，只填「进攻1摧毁率」。"
        WarMode.AUTO ->
            "# 本次赛事\n先判断截图类型：每人 2 次进攻 = 部落战（两列摧毁率都填）；每人 1 次进攻 = 联赛（「进攻2摧毁率」列留空）。"
    }

    private fun sectionRoster(names: List<String>, total: Int): String {
        val body = names.chunked(ROSTER_PER_LINE).joinToString("\n") { line -> line.joinToString("、") }
        val head = "# 部落在册成员名单（共 " + names.size + " 人，用于校正名字错字）"
        val tail = if (total > names.size) {
            "\n（名单较长，仅列出前 " + MAX_ROSTER + " 位；未列出的成员仍按图中写法输出）"
        } else ""
        return head + "\n" + body + tail
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
        你是《部落冲突》战报数据转录助手：把战报截图中的成员列表逐行、逐字转录为 CSV。
        准确性第一：看不清就填 -1 作标记，绝不猜测、不填 0 冒充看清。
    """.trimIndent()

    private val SECTION_FORMAT = """
        # 输出格式（严格遵守）
        第一行必须是下面这行表头，一字不改：
        $HEADER
        随后每行一名成员，恰好 5 列，用英文逗号分隔，列内不要加空格。
        只输出 CSV 本身：禁止任何解释、标题、Markdown 代码块围栏、行号或项目符号（排名列本身就是序号）。
        图中没有任何成员数据时，只输出表头行。
    """.trimIndent()

    private val SECTION_FIELDS = """
        # 字段口径
        - 排名：图中该成员的序号，从 1 开始连续递增，不要重排、不要跳号。
        - 总星数：直接抄写图右侧显示的数字，部落战 0-6、联赛 0-3。
        - 摧毁率：整数百分比，去掉 % 号（87 表示 87%），范围 0-100。
        - 未进攻、没有进攻记录：总星数填 0、摧毁率填 0。
        - 看不清（数字被遮挡、模糊、反光、被滚动条截断等）：填 -1，作为「待确认」标记，
          交给使用者人工核对；绝不填 0 冒充看清、也绝不猜一个数。
    """.trimIndent()

    private val SECTION_INDEPENDENCE = """
        # 关键约束：总星数与摧毁率是独立观察值
        总星数来源于图中右侧的数字显示；摧毁率来源于左侧的进度数字，二者没有直接换算关系：
        - 摧毁率 100% 不一定是 3 星，摧毁率 30% 也不一定是 0 星，一律按图中右侧数字为准。
        - 不要用摧毁率反推星数，也不要用星数反推摧毁率。
        - 出现「按摧毁率算星数才对得上」的感觉时，仍以图中可见数字为准。
    """.trimIndent()

    private val SECTION_CONSISTENCY = """
        # 自洽性检查（仅做范围与排版核对，不做数值互推）
        - 总星数：正常为 0-6（部落战）/ 0-3（联赛）；-1 是合法的「看不清」标记，保留原样。
        - 摧毁率：正常为 0-100；-1 是合法的「看不清」标记，保留原样。
        - 排名：从 1 开始连续递增、无重号、无跳号；不连续说明漏人或合并。
        - 不要合并相邻两行，也不要漏掉被滚动条截断的半行成员；每名成员在结果中只出现一次。
    """.trimIndent()

    private val SECTION_NAME_RULES = """
        # 成员名转录
        - 逐字抄写图中的名字，保留原字符与大小写，不翻译、不改写、不加空格或标点。
        - 忽略头像、职位徽章、等级数字与装饰符号，它们不是名字的一部分。
        - 名字末尾的数字是同名成员的区分编号（如 余味 / 余味1 / 余味2 是三名不同成员）：
          必须原样保留，不要当成笔误删除，也不要合并成一个人。
    """.trimIndent()

    private val SECTION_MATCH_RULES = """
        # 名单匹配规则
        1. 图中名字与名单中某个名字高度相似（形近字/异体字、仅大小写差异、缺字或多字不超过 1 个）时，输出名单中的写法。
        2. 判断不了归属时，按图中写法原样输出；名单只是校正工具，不是候选集。
        3. 禁止臆造名单中不存在、图中也没有的名字；禁止把两个不同成员写成同一个名字。
        4. 末尾数字编号不同 = 不同成员（余味 / 余味1 是两个人，各自都有独立的一行数据），
           不要相互纠正、不要合并成一个人。
    """.trimIndent()

    // ==================== 轻量字符串相似度 ====================
    // 与 ui/util/StringMatcher 同口径；本地实现以避免 data 层反向依赖 ui 层。

    private fun isLikelySameName(a: String, b: String): Boolean {
        if (a == b) return false
        if (a.isEmpty() || b.isEmpty()) return false
        // 编号变体是两名不同成员（余味 / 余味1），不是形近错字——不能提示"逐字核对"，
        // 否则会诱导模型把两个人合并成一个
        if (isNumberedVariant(a, b)) return false
        if (similarity(a, b) >= CONFUSABLE_THRESHOLD) return true
        return a.length == b.length && a.length >= 2 && levenshtein(a, b) == 1
    }

    /** 去掉结尾的连续数字（同名成员的编号后缀）："余味1" → "余味"，"余味" → "余味"。 */
    private fun stripNumberSuffix(name: String): String =
        name.trimEnd('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')

    /**
     * 是否为「同名成员的编号变体」：去掉末尾数字后相同（余味 / 余味1 / 余味2）。
     *
     * 部落里同名玩家按顺序编号区分：第一个是原名，其后依次加数字后缀，
     * 因此这类名字是**不同成员**而非 OCR 错字，不能当作易混名提醒。
     */
    fun isNumberedVariant(a: String, b: String): Boolean {
        if (a == b) return false
        val sa = stripNumberSuffix(a)
        val sb = stripNumberSuffix(b)
        return sa.isNotEmpty() && sa == sb
    }

    private fun similarity(a: String, b: String): Float {
        val maxLen = maxOf(a.length, b.length)
        if (maxLen == 0) return 1f
        return 1f - levenshtein(a, b).toFloat() / maxLen
    }

    private fun levenshtein(a: String, b: String): Int {
        val m = a.length
        val n = b.length
        if (m == 0) return n
        if (n == 0) return m
        var prev = IntArray(n + 1) { it }
        var curr = IntArray(n + 1)
        for (i in 1..m) {
            curr[0] = i
            for (j in 1..n) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost)
            }
            val tmp = prev; prev = curr; curr = tmp
        }
        return prev[n]
    }

    // ==================== 常量 ====================

    /** 名单注入上限：超出部分不注入（COC 部落上限 50 人，留余量），避免 prompt 过长。 */
    private const val MAX_ROSTER = 60

    /** 名单每行展示的名字数（紧凑排布，省 token 且便于模型扫描）。 */
    private const val ROSTER_PER_LINE = 6

    /** 易混名对最多提示条数。 */
    private const val MAX_CONFUSABLE_PAIRS = 12

    /** 易混判定阈值（相似度），与 StringMatcher.isLikelySameName 一致。 */
    private const val CONFUSABLE_THRESHOLD = 0.5f
}
