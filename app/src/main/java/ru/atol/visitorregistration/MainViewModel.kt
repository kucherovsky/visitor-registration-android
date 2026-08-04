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
import ru.atol.visitorregistration.data.local.LabelTemplateStore
import ru.atol.visitorregistration.data.local.RoomPrinterRepository
import ru.atol.visitorregistration.data.local.RoomVisitorRepository
import ru.atol.visitorregistration.exporting.AndroidAttendanceExporter
import ru.atol.visitorregistration.importing.AndroidSpreadsheetImporter
import ru.atol.visitorregistration.model.PrinterConfig
import ru.atol.visitorregistration.model.PrinterEncoding
import ru.atol.visitorregistration.model.LabelTemplate
import ru.atol.visitorregistration.model.LabelTemplateKind
import ru.atol.visitorregistration.model.Visitor
import ru.atol.visitorregistration.model.VisitorSource
import ru.atol.visitorregistration.model.VisitorType
import ru.atol.visitorregistration.model.defaultLabelTemplate
import ru.atol.visitorregistration.model.matchesSearch
import ru.atol.visitorregistration.printing.TsplPrinterClient
import ru.atol.visitorregistration.printing.TsplTemplateEngine
import ru.atol.visitorregistration.printing.PlacedLabelText

enum class ImportMode(val title: String) {
    REPLACE("Заменить список"),
    APPEND("Добавить к списку")
}

data class PendingImport(
    val fileName: String,
    val type: VisitorType,
    val mode: ImportMode,
    val result: ImportResult
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.get(application)
    private val repository = RoomVisitorRepository(database)
    private val printerRepository = RoomPrinterRepository(database)
    private val importer = AndroidSpreadsheetImporter(application)
    private val exporter = AndroidAttendanceExporter(application)
    private val templateStore = LabelTemplateStore(application)
    private val templateEngine = TsplTemplateEngine()
    private val printerClient = TsplPrinterClient(templateEngine)

    var visitors by mutableStateOf<List<Visitor>>(emptyList())
        private set
    var searchQuery by mutableStateOf("")
    var selectedImportType by mutableStateOf(VisitorType.PARTNER)
    var importMode by mutableStateOf(ImportMode.REPLACE)
    var selectedFileName by mutableStateOf<String?>(null)
    var lastImportResult by mutableStateOf<ImportResult?>(null)
    var pendingImport by mutableStateOf<PendingImport?>(null)
        private set
    var printers by mutableStateOf<List<PrinterConfig>>(emptyList())
        private set
    var statusMessage by mutableStateOf<String?>(null)
    var printerBusy by mutableStateOf(false)
        private set
    var importBusy by mutableStateOf(false)
        private set
    var visitorTemplate by mutableStateOf(templateEngine.applyAutomaticPositions(templateStore.load(LabelTemplateKind.VISITOR)))
        private set
    var employeeTemplate by mutableStateOf(templateEngine.applyAutomaticPositions(templateStore.load(LabelTemplateKind.EMPLOYEE)))
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
            if (searchQuery.isBlank()) return emptyList()
            return visitors.filter { it.matchesSearch(searchQuery) }
        }

    val checkedInCount: Int get() = visitors.count { it.checkedInAt != null }
    val activePrinter: PrinterConfig? get() = printers.firstOrNull { it.isDefault } ?: printers.firstOrNull()

    fun labelTemplate(kind: LabelTemplateKind): LabelTemplate = when (kind) {
        LabelTemplateKind.VISITOR -> visitorTemplate
        LabelTemplateKind.EMPLOYEE -> employeeTemplate
    }

    fun applyAutomaticLayout(template: LabelTemplate): LabelTemplate = templateEngine.applyAutomaticPositions(template)

    fun previewLayout(template: LabelTemplate): List<PlacedLabelText> =
        templateEngine.layout(template, templateEngine.sampleVisitor(template.kind))

    fun labelTemplateTspl(template: LabelTemplate): String = templateEngine.templateText(template)

    fun saveLabelTemplate(template: LabelTemplate) {
        val resolved = templateEngine.applyAutomaticPositions(template)
        templateStore.save(resolved)
        when (resolved.kind) {
            LabelTemplateKind.VISITOR -> visitorTemplate = resolved
            LabelTemplateKind.EMPLOYEE -> employeeTemplate = resolved
        }
        statusMessage = "Шаблон «${resolved.kind.title}» сохранён"
    }

    fun resetLabelTemplate(kind: LabelTemplateKind): LabelTemplate {
        val reset = templateEngine.applyAutomaticPositions(defaultLabelTemplate(kind))
        templateStore.save(reset)
        when (kind) {
            LabelTemplateKind.VISITOR -> visitorTemplate = reset
            LabelTemplateKind.EMPLOYEE -> employeeTemplate = reset
        }
        statusMessage = "Шаблон «${kind.title}» сброшен к заводским настройкам"
        return reset
    }

    fun testLabelTemplate(template: LabelTemplate) {
        val printer = activePrinter
        if (printer == null) {
            statusMessage = "Сначала добавьте и выберите основной принтер"
            return
        }
        val sample = templateEngine.sampleVisitor(template.kind)
        runPrinterAction("Тестовый шаблон «${template.kind.title}» отправлен") {
            printerClient.printBadge(printer, sample, template)
        }
    }

    fun importFile(uri: Uri) {
        if (importBusy) return
        val type = selectedImportType
        val mode = importMode
        val fileName = displayName(uri)
        selectedFileName = fileName
        importBusy = true
        lastImportResult = null
        pendingImport = null
        statusMessage = "Проверяем выбранный файл…"
        viewModelScope.launch {
            runCatching { importer.read(uri, type) }
                .onSuccess { result ->
                    pendingImport = PendingImport(fileName, type, mode, result)
                    statusMessage = null
                }
                .onFailure { error -> statusMessage = "Ошибка загрузки: ${importErrorMessage(error)}" }
            importBusy = false
        }
    }

    fun confirmImport() {
        val pending = pendingImport ?: return
        if (importBusy) return
        importBusy = true
        viewModelScope.launch {
            runCatching {
                if (pending.mode == ImportMode.REPLACE) {
                    repository.replaceImported(pending.type, pending.result.visitors)
                } else {
                    repository.saveAll(pending.result.visitors)
                }
            }.onSuccess {
                lastImportResult = pending.result
                pendingImport = null
                statusMessage = "Список сохранён: ${pending.result.imported} записей"
            }.onFailure { error ->
                statusMessage = "Ошибка сохранения: ${error.message ?: "не удалось обновить базу"}"
            }
            importBusy = false
        }
    }

    fun cancelImport() {
        pendingImport = null
        selectedFileName = null
        statusMessage = "Загрузка отменена"
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
        encoding: PrinterEncoding,
        existing: PrinterConfig? = null
    ): Boolean {
        val printer = printerFromFields(name, host, port, encoding, existing) ?: return false
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

    fun checkPrinter(name: String, host: String, port: String, encoding: PrinterEncoding) {
        val printer = printerFromFields(name, host, port, encoding) ?: return
        runPrinterAction("Соединение с принтером установлено") {
            printerClient.checkConnection(printer)
        }
    }

    fun testPrint(name: String, host: String, port: String, encoding: PrinterEncoding) {
        val printer = printerFromFields(name, host, port, encoding) ?: return
        runPrinterAction("Тест четырёх кодировок отправлен") {
            printerClient.printTest(printer, visitorTemplate)
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
        encoding: PrinterEncoding,
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
            widthMm = existing?.widthMm ?: 50,
            heightMm = existing?.heightMm ?: 70,
            encoding = encoding,
            fontName = existing?.fontName ?: "3",
            isDefault = existing?.isDefault ?: false
        )
    }

    fun clearDatabase() {
        viewModelScope.launch {
            repository.clearAll()
            searchQuery = ""
            lastImportResult = null
            pendingImport = null
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

    fun resetApplication() {
        viewModelScope.launch {
            repository.clearAll()
            printerRepository.clearAll()
            templateStore.clearAll()
            searchQuery = ""
            selectedImportType = VisitorType.PARTNER
            importMode = ImportMode.REPLACE
            selectedFileName = null
            lastImportResult = null
            pendingImport = null
            visitorTemplate = templateEngine.applyAutomaticPositions(defaultLabelTemplate(LabelTemplateKind.VISITOR))
            employeeTemplate = templateEngine.applyAutomaticPositions(defaultLabelTemplate(LabelTemplateKind.EMPLOYEE))
            statusMessage = "Приложение сброшено к заводским настройкам"
        }
    }

    private suspend fun printAfterRegistration(visitor: Visitor, registrationMessage: String) {
        val printer = activePrinter
        if (printer == null) {
            statusMessage = "$registrationMessage. Принтер не настроен"
            return
        }
        printerBusy = true
        val template = if (visitor.type == VisitorType.EMPLOYEE) employeeTemplate else visitorTemplate
        val result = printerClient.printBadge(printer, visitor, template)
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

    private fun importErrorMessage(error: Throwable): String = generateSequence(error) { it.cause }
        .mapNotNull { it.message?.trim()?.takeIf(String::isNotBlank) }
        .firstOrNull()
        ?: "не удалось прочитать Excel-файл"

}
