package ru.atol.visitorregistration.importing

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.atol.visitorregistration.data.ImportResult
import ru.atol.visitorregistration.data.SpreadsheetImporter
import ru.atol.visitorregistration.model.VisitorType

class AndroidSpreadsheetImporter(
    private val context: Context,
    private val tableReader: XlsxTableReader = XlsxTableReader(),
    private val tableMapper: VisitorTableMapper = VisitorTableMapper()
) : SpreadsheetImporter {
    override suspend fun read(uri: Uri, type: VisitorType): ImportResult = withContext(Dispatchers.IO) {
        val rows = context.contentResolver.openInputStream(uri)?.use(tableReader::read)
            ?: error("Не удалось открыть выбранный файл")
        tableMapper.map(rows, type)
    }
}
