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
        return if (best != null && bestScore >= threshold)
            best!! to bestScore else null
    }

    /**
     * 疑似同名判定：编辑距离相似度 >= [threshold]，或（多字名）等长且仅一字之差。
     * 单字名不适用一字之差兜底——任意两个不同的单字名编辑距离都是 1，
     * 兜底会把所有单字名互相判成同名。
     */
    fun isLikelySameName(a: String, b: String, threshold: Float = 0.5f): Boolean {
        if (a == b) return true
        if (a.isEmpty() || b.isEmpty()) return false
        if (similarity(a, b) >= threshold) return true
        return a.length == b.length && a.length >= 2 && levenshtein(a, b) == 1
    }

    /**
     * 从候选中找最佳疑似同名项（相似度最高者），无合格候选返回 null。
     * 与 [isLikelySameName] 同一判定口径，供入册确认闸/数据体检共用。
     */
    fun bestLikelyMatch(
        target: String,
        candidates: List<String>,
        threshold: Float = 0.5f
    ): Pair<String, Float>? {
        var best: Pair<String, Float>? = null
        for (c in candidates) {
            if (!isLikelySameName(target, c, threshold)) continue
            val score = similarity(target, c)
            if (best == null || score > best!!.second) best = c to score
        }
        return best
    }
}
