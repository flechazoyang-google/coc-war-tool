package com.cocwar.ui.util

/**
 * 基于编辑距离（Levenshtein）的名称相似度匹配。
 */
object StringMatcher {

    /**
     * 计算两个字符串的编辑距离。
     */
    fun levenshtein(a: String, b: String): Int {
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
                curr[j] = minOf(
                    prev[j] + 1,
                    curr[j - 1] + 1,
                    prev[j - 1] + cost
                )
            }
            // swap references
            val tmp = prev; prev = curr; curr = tmp
        }
        return prev[n]
    }

    /**
     * 计算相似度 0..1（1 = 完全相同）。
     */
    fun similarity(a: String, b: String): Float {
        val dist = levenshtein(a, b)
        val maxLen = maxOf(a.length, b.length)
        if (maxLen == 0) return 1f
        return 1f - dist.toFloat() / maxLen
    }

    /**
     * 从候选列表中找出最佳匹配，返回相似度 >= threshold 的第一个候选项（最高相似度）。
     * 如果无匹配返回 null。
     */
    fun bestMatch(
        target: String,
        candidates: List<String>,
        threshold: Float = 0.7f
    ): Pair<String, Float>? {
        var best: String? = null
        var bestScore = 0f
        for (c in candidates) {
            val score = similarity(target, c)
            if (score > bestScore) {
                bestScore = score
                best = c
            }
        }
        return if (best != null && bestScore >= threshold && bestScore < 1f)
            best!! to bestScore else null
    }
}
