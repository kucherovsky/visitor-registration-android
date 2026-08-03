package ru.atol.visitorregistration.exporting

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.atol.visitorregistration.data.AttendanceExporter
import ru.atol.visitorregistration.model.Visitor

class AndroidAttendanceExporter(
    private val context: Context,
    private val writer: AttendanceXlsxWriter = AttendanceXlsxWriter()
) : AttendanceExporter {
    override suspend fun write(uri: Uri, visitors: List<Visitor>): Int = withContext(Dispatchers.IO) {
        val checkedIn = visitors.filter { it.checkedInAt != null }
        context.contentResolver.openOutputStream(uri, "w")?.use { output -> writer.write(output, checkedIn) }
            ?: error("Не удалось создать Excel-файл")
        checkedIn.size
    }
}
