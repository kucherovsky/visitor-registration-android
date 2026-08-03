package ru.atol.visitorregistration

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import ru.atol.visitorregistration.data.ImportResult
import ru.atol.visitorregistration.data.local.AppDatabase
import ru.atol.visitorregistration.data.local.RoomPrinterRepository
import ru.atol.visitorregistration.data.local.RoomVisitorRepository
import ru.atol.visitorregistration.exporting.AndroidAttendanceExporter
import ru.atol.visitorregistration.importing.AndroidSpreadsheetImporter
import ru.atol.visitorregistration.model.PrinterConfig
import ru.atol.visitorregistration.model.PrinterEncoding
import ru.atol.visitorregistration.model.Visitor
import ru.atol.visitorregistration.model.VisitorSource
import ru.atol.visitorregistration.model.VisitorType
import ru.atol.visitorregistration.printing.TsplPrinterClient

enum class ImportMode(val title: String) {
    REPLACE("Заменить список"),
    APPEND("Добавить к списку")
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.get(application)
    private val repository = RoomVisitorRepository(database)
    private val printerRepository = RoomPrinterRepository(database)
    private val importer = AndroidSpreadsheetImporter(application)
    private val exporter = AndroidAttendanceExporter(application)
    private val printerClient = TsplPrinterClient()

    var visitors by mutableStateOf<List<Visitor>>(emptyList())
        private set
    var searchQuery by mutableStateOf("")
    var selectedImportType by mutableStateOf(VisitorType.PARTNER)
    var importMode by mutableStateOf(ImportMode.REPLACE)
    var selectedFileName by mutableStateOf<String?>(null)
    var lastImportResult by mutableStateOf<ImportResult?>(null)
    var printers by mutableStateOf<List<PrinterConfig>>(emptyList())
        private set
    var statusMessage by mutableStateOf<String?>(null)
    var printerBusy by mutableStateOf(false)
        private set
    var importBusy by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            repository.observeAll().collectLatest { visitors = it }
        }
        viewModelScope.launch {
            printerRepository.observeAll().collectLatest { printers = it }
        }
    }

    val searchResults: List<Visitor>
        get() {
            val query = searchQuery.trim()
            if (query.isBlank()) return emptyList()
            return visitors.filter {
                it.lastName.contains(query, ignoreCase = true) ||
                    it.firstName.contains(query, ignoreCase = true)
            }
        }

    val checkedInCount: Int get() = visitors.count { it.checkedInAt != null }
    val activePrinter: PrinterConfig? get() = printers.firstOrNull { it.isDefault } ?: printers.firstOrNull()

    fun importFile(uri: Uri) {
        if (importBusy) return
        val type = selectedImportType
        val mode = importMode
        selectedFileName = displayName(uri)
        importBusy = true
        lastImportResult = null
        statusMessage = "Загружаем список…"
        viewModelScope.launch {
            runCatching {
                val result = importer.read(uri, type)
                if (mode == ImportMode.REPLACE) {
                    repository.replaceImported(type, result.visitors)
                } else {
                    repository.saveAll(result.visitors)
                }
                result
            }
                .onSuccess { result ->
                    lastImportResult = result
                    statusMessage = buildString {
                        append("Загружено записей: ${result.imported}")
                        if (result.warnings.isNotEmpty()) append(". ${result.warnings.joinToString("; ")}")
                    }
                }
                .onFailure { error -> statusMessage = "Ошибка загрузки: ${error.message ?: "неверный файл"}" }
            importBusy = false
        }
    }

    fun addVisitor(
        lastName: String,
        firstName: String,
        company: String,
        position: String,
        city: String,
        type: VisitorType
    ): Boolean {
        if (lastName.isBlank()) {
            statusMessage = "Укажите фамилию"
            return false
        }
        val visitor = Visitor(
            lastName = lastName.trim(),
            firstName = firstName.trim(),
            company = company.trim(),
            position = position.trim(),
            city = city.trim(),
            type = type,
            source = VisitorSource.MANUAL,
            checkedInAt = System.currentTimeMillis()
        )
        viewModelScope.launch {
            repository.save(visitor)
            printAfterRegistration(visitor, "${visitor.fullName} добавлен и зарегистрирован")
        }
        return true
    }

    fun checkInAndPrint(visitor: Visitor) {
        if (printerBusy) return
        viewModelScope.launch {
            val checkedIn = repository.checkIn(visitor.id)
            printAfterRegistration(checkedIn, "${visitor.fullName} зарегистрирован")
        }
    }

    fun savePrinter(
        name: String,
        host: String,
        port: String,
        width: String,
        height: String,
        encoding: PrinterEncoding,
        fontName: String,
        existing: PrinterConfig? = null
    ): Boolean {
        val printer = printerFromFields(name, host, port, width, height, encoding, fontName, existing) ?: return false
        viewModelScope.launch {
            printerRepository.save(printer.copy(isDefault = existing?.isDefault == true || printers.isEmpty()))
            statusMessage = if (existing == null) "Принтер ${printer.name} добавлен" else "Принтер ${printer.name} обновлён"
        }
        return true
    }

    fun setDefaultPrinter(printerId: String) {
        viewModelScope.launch {
            printerRepository.setDefault(printerId)
            statusMessage = "Основной принтер выбран"
        }
    }

    fun deletePrinter(printerId: String) {
        viewModelScope.launch {
            printerRepository.delete(printerId)
            statusMessage = "Принтер удалён"
        }
    }

    fun checkPrinter(name: String, host: String, port: String, width: String, height: String, encoding: PrinterEncoding, fontName: String) {
        val printer = printerFromFields(name, host, port, width, height, encoding, fontName) ?: return
        runPrinterAction("Соединение с принтером установлено") {
            printerClient.checkConnection(printer)
        }
    }

    fun testPrint(name: String, host: String, port: String, width: String, height: String, encoding: PrinterEncoding, fontName: String) {
        val printer = printerFromFields(name, host, port, width, height, encoding, fontName) ?: return
        runPrinterAction("Тест четырёх кодировок отправлен") {
            printerClient.printTest(printer)
        }
    }

    fun exportAttendance(uri: Uri) {
        viewModelScope.launch {
            runCatching { exporter.write(uri, visitors) }
                .onSuccess { count -> statusMessage = "В Excel выгружено посетителей: $count" }
                .onFailure { error -> statusMessage = "Ошибка выгрузки: ${error.message ?: "не удалось создать файл"}" }
        }
    }

    private fun printerFromFields(
        name: String,
        host: String,
        port: String,
        width: String,
        height: String,
        encoding: PrinterEncoding,
        fontName: String,
        existing: PrinterConfig? = null
    ): PrinterConfig? {
        if (host.isBlank()) {
            statusMessage = "Укажите IP-адрес принтера"
            return null
        }
        return PrinterConfig(
            id = existing?.id ?: java.util.UUID.randomUUID().toString(),
            name = name.trim().ifBlank { "Основной принтер" },
            host = host.trim(),
            port = port.toIntOrNull()?.takeIf { it in 1..65535 } ?: 9100,
            widthMm = width.toIntOrNull()?.takeIf { it > 0 } ?: 58,
            heightMm = height.toIntOrNull()?.takeIf { it > 0 } ?: 40,
            encoding = encoding,
            fontName = fontName.trim().ifBlank { "3" },
            isDefault = existing?.isDefault ?: false
        )
    }

    fun clearDatabase() {
        viewModelScope.launch {
            repository.clearAll()
            searchQuery = ""
            lastImportResult = null
            selectedFileName = null
            statusMessage = "База посетителей очищена"
        }
    }

    fun clearPrinters() {
        viewModelScope.launch {
            printerRepository.clearAll()
            statusMessage = "Все принтеры удалены"
        }
    }

    private suspend fun printAfterRegistration(visitor: Visitor, registrationMessage: String) {
        val printer = activePrinter
        if (printer == null) {
            statusMessage = "$registrationMessage. Принтер не настроен"
            return
        }
        printerBusy = true
        val result = printerClient.printBadge(printer, visitor)
        result.onSuccess { repository.markPrinted(visitor.id) }
        statusMessage = result.fold(
            onSuccess = { "$registrationMessage, этикетка напечатана" },
            onFailure = { "$registrationMessage. Ошибка печати: ${it.message ?: "нет соединения"}" }
        )
        printerBusy = false
    }

    private fun runPrinterAction(successMessage: String, action: suspend () -> Result<Unit>) {
        if (printerBusy) return
        printerBusy = true
        statusMessage = "Проверяем принтер…"
        viewModelScope.launch {
            val result = action()
            statusMessage = result.fold(
                onSuccess = { successMessage },
                onFailure = { "Ошибка принтера: ${it.message ?: "нет соединения"}" }
            )
            printerBusy = false
        }
    }

    private fun displayName(uri: Uri): String = getApplication<Application>().contentResolver
        .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
        ?: uri.lastPathSegment.orEmpty()
}
