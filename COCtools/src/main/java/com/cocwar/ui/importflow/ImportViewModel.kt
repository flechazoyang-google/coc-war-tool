package com.cocwar.ui.importflow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cocwar.data.db.PendingImportEntity
import com.cocwar.data.ocr.OcrClient
import com.cocwar.data.ocr.OcrConfig
import com.cocwar.data.ocr.OcrCsvExtractor
import com.cocwar.data.parser.WarJsonParser
import com.cocwar.data.repository.WarRepository
import kotlinx.coroutines.launch

class ImportViewModel(
    private val repo: WarRepository,
    private val ocrConfig: OcrConfig? = null
) : ViewModel() {

    /** 解析战报：先按 JSON 内容自动识别类型（RULES §4.9），再解析并注入花名册职位映射。 */
    suspend fun parse(json: String): WarJsonParser.ParseResult {
        val roleMap = repo.rosterRoleMap()
        return WarJsonParser.parse(
            json,
            eventType = WarJsonParser.inferEventType(json),
            rosterRoles = roleMap
        )
    }

    /** 解析 CSV 战报（B2，RULES §4.15）：按类型填充槽位，复用 JSON 解析完整链路。 */
    suspend fun parseCsv(
        text: String,
        eventType: String,
        slotCount: Int
    ): WarJsonParser.ParseResult {
        val roleMap = repo.rosterRoleMap()
        return com.cocwar.data.csv.CsvImporter.parse(
            text = text,
            slotCount = slotCount,
            eventType = eventType,
            rosterRoles = roleMap
        )
    }

    suspend fun generateName(eventType: String, eventRound: Int): String =
        repo.generateEventName(eventType, eventRound)

    suspend fun generateNameForDate(eventType: String, eventRound: Int, dateMillis: Long): String {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = dateMillis }
        return repo.generateEventName(eventType, eventRound, cal)
    }

    /**
     * 识图：调用已配置的视觉模型（默认千问），返回提取后的纯 CSV。
     * @throws OcrClient.OcrException 未配置 Key / 网络 / 超时 / API 错误 / 响应解析失败
     */
    suspend fun recognize(imageBase64: String, mimeType: String = "image/jpeg"): String {
        val config = ocrConfig ?: throw OcrClient.OcrException.NotConfigured()
        if (!config.isConfigured) throw OcrClient.OcrException.NotConfigured()
        val client = OcrClient(
            apiKey = config.apiKey,
            baseUrl = config.baseUrl,
            model = config.model
        )
        return OcrCsvExtractor.extract(client.recognize(imageBase64, mimeType))
    }

    suspend fun loadRoster(): List<String> = repo.getRoster()

    /**
     * 保存事件：自动把名单外的成员加入花名册，再导入事件，全部串行在
     * 同一个 viewModelScope 协程中完成。避免 onSaved→popBackStack 后
     * 名单写入协程被取消导致新成员丢失。
     */
    suspend fun loadPendingImport(id: String): PendingImportEntity? = repo.getPendingImport(id)

    fun save(parsed: WarJsonParser.ParsedEvent, pendingImportId: String? = null, onSaved: () -> Unit) {
        viewModelScope.launch {
            val roster = repo.getRoster()
            val newNames = parsed.members.map { it.playerName }.filter { it !in roster }.distinct()
            if (newNames.isNotEmpty()) repo.addToRoster(newNames)
            repo.importEvent(parsed)
            if (pendingImportId != null) repo.deletePendingImport(pendingImportId)
            onSaved()
        }
    }
}
