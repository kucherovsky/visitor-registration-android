package ru.atol.visitorregistration

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import ru.atol.visitorregistration.model.PrinterConfig
import ru.atol.visitorregistration.model.Visitor
import ru.atol.visitorregistration.model.VisitorSource
import ru.atol.visitorregistration.model.VisitorType
import ru.atol.visitorregistration.printing.TsplPrinterClient

class MainViewModel : ViewModel() {
    private val printerClient = TsplPrinterClient()
    private val visitors = mutableStateListOf<Visitor>()

    var searchQuery by mutableStateOf("")
    var selectedImportType by mutableStateOf(VisitorType.PARTNER)
    var selectedFileName by mutableStateOf<String?>(null)
    var printerConfig by mutableStateOf(PrinterConfig())
    var statusMessage by mutableStateOf<String?>(null)
    var printerBusy by mutableStateOf(false)

    val searchResults: List<Visitor>
        get() {
            val query = searchQuery.trim()
            if (query.isBlank()) return emptyList()
            return visitors.filter {
                it.lastName.contains(query, ignoreCase = true) ||
                    it.firstName.contains(query, ignoreCase = true)
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
        visitors += visitor
        statusMessage = "${visitor.fullName} добавлен и зарегистрирован"
        return true
    }

    fun checkIn(visitor: Visitor) {
        val index = visitors.indexOfFirst { it.id == visitor.id }
        if (index < 0) return
        visitors[index] = visitor.copy(checkedInAt = visitor.checkedInAt ?: System.currentTimeMillis())
        statusMessage = "${visitor.fullName} зарегистрирован"
    }

    fun updatePrinter(host: String, port: String) {
        printerConfig = printerConfig.copy(host = host.trim(), port = port.toIntOrNull() ?: 9100)
    }

    fun checkPrinter() = runPrinterAction("Соединение с принтером установлено") {
        printerClient.checkConnection(printerConfig)
    }

    fun testPrint() = runPrinterAction("Тестовая этикетка отправлена") {
        printerClient.printTest(printerConfig)
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
}
