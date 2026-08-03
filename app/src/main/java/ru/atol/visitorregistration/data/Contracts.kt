package ru.atol.visitorregistration.data

import android.net.Uri
import kotlinx.coroutines.flow.Flow
import ru.atol.visitorregistration.model.PrinterConfig
import ru.atol.visitorregistration.model.Visitor
import ru.atol.visitorregistration.model.VisitorType

data class ImportResult(
    val visitors: List<Visitor>,
    val skipped: Int,
    val warnings: List<String>
) {
    val imported: Int get() = visitors.size
}

interface VisitorRepository {
    fun observeAll(): Flow<List<Visitor>>
    suspend fun save(visitor: Visitor)
    suspend fun saveAll(visitors: List<Visitor>)
    suspend fun checkIn(visitorId: String): Visitor
    suspend fun markPrinted(visitorId: String)
    suspend fun replaceImported(type: VisitorType, visitors: List<Visitor>)
    suspend fun clearAll()
}

interface SpreadsheetImporter {
    suspend fun read(uri: Uri, type: VisitorType): ImportResult
}

interface PrinterService {
    suspend fun checkConnection(config: PrinterConfig): Result<Unit>
    suspend fun printTest(config: PrinterConfig): Result<Unit>
    suspend fun printBadge(config: PrinterConfig, visitor: Visitor): Result<Unit>
}
