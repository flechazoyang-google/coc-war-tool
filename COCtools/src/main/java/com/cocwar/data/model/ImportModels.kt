package com.cocwar.data.model

import com.cocwar.data.db.MemberEntity
import com.cocwar.data.db.WarEventEntity

/** 一次解析/导入的完整结果：事件 + 成员列表（CSV 导入与样例数据共用）。 */
data class ParsedEvent(
    val event: WarEventEntity,
    val members: List<MemberEntity>
)

/** 解析结果：成功携带 [ParsedEvent]，失败携带错误信息。 */
sealed interface ParseResult {
    data class Success(val data: ParsedEvent) : ParseResult
    data class Error(val message: String) : ParseResult
}
