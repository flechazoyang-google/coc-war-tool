package com.cocwar.data.sync

/**
 * 双向同步决策（RULES §6 决策表，纯函数，无副作用）。
 *
 * 输入为两端的数据指纹（导出 JSON 的 SHA-256）与上次同步时的指纹，
 * 输出为应执行的动作。指纹为 null 表示「无数据」（本地空库 / 远端文件不存在）。
 */
object SyncDecider {

    enum class SyncAction {
        /** 两端一致或均无数据，无需操作 */
        UP_TO_DATE,

        /** 上传本地覆盖远端 */
        PUSH_LOCAL,

        /** 下载远端恢复本地 */
        PULL_REMOTE,

        /** 两端都有修改，需用户决策 */
        CONFLICT
    }

    fun decide(
        localFp: String?,
        remoteFp: String?,
        lastLocalFp: String?,
        lastRemoteFp: String?
    ): SyncAction {
        // 无数据优先：空↔空一致；本地空→拉取；远端空→推送
        if (localFp == null && remoteFp == null) return SyncAction.UP_TO_DATE
        if (localFp == null) return SyncAction.PULL_REMOTE
        if (remoteFp == null) return SyncAction.PUSH_LOCAL

        // 两端都有数据：指纹相同即一致
        if (localFp == remoteFp) return SyncAction.UP_TO_DATE

        // 变更判定：无上次指纹（首次同步）视为「已变更」
        val localChanged = lastLocalFp == null || localFp != lastLocalFp
        val remoteChanged = lastRemoteFp == null || remoteFp != lastRemoteFp

        return when {
            localChanged && !remoteChanged -> SyncAction.PUSH_LOCAL
            !localChanged && remoteChanged -> SyncAction.PULL_REMOTE
            else -> SyncAction.CONFLICT
        }
    }
}
