package ru.atol.visitorregistration

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.atol.visitorregistration.model.Visitor
import ru.atol.visitorregistration.model.VisitorType

private enum class AppSection(val title: String) {
    REGISTRATION("Регистрация"),
    NEW_VISITOR("Новый посетитель"),
    SETTINGS("Настройки")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                VisitorRegistrationApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VisitorRegistrationApp(vm: MainViewModel = viewModel()) {
    var section by remember { mutableStateOf(AppSection.REGISTRATION) }
    val snackbar = remember { SnackbarHostState() }
    val message = vm.statusMessage

    LaunchedEffect(message) {
        if (message != null) snackbar.showSnackbar(message)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(section.title) }) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                AppSection.entries.forEach { item ->
                    NavigationBarItem(
                        selected = section == item,
                        onClick = { section = item },
                        icon = { Text(if (section == item) "●" else "○") },
                        label = { Text(item.title) }
                    )
                }
            }
        }
    ) { padding ->
        when (section) {
            AppSection.REGISTRATION -> RegistrationScreen(vm, Modifier.padding(padding))
            AppSection.NEW_VISITOR -> NewVisitorScreen(vm, Modifier.padding(padding)) {
                section = AppSection.REGISTRATION
            }
            AppSection.SETTINGS -> SettingsScreen(vm, Modifier.padding(padding))
        }
    }
}

@Composable
private fun RegistrationScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(20.dp)) {
        OutlinedTextField(
            value = vm.searchQuery,
            onValueChange = { vm.searchQuery = it },
            label = { Text("Введите фамилию или имя") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        when {
            vm.searchQuery.isBlank() -> Text("Начните вводить фамилию посетителя")
            vm.searchResults.isEmpty() -> Text("Посетитель не найден. Добавьте его вручную.")
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(vm.searchResults, key = { it.id }) { visitor ->
                    VisitorCard(visitor = visitor, onCheckIn = { vm.checkInAndPrint(visitor) })
                }
            }
        }
    }
}

@Composable
private fun VisitorCard(visitor: Visitor, onCheckIn: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(visitor.fullName, style = MaterialTheme.typography.titleLarge)
            Text(listOf(visitor.company, visitor.position, visitor.city).filter { it.isNotBlank() }.joinToString(" · "))
            Text("${visitor.type.title} · ${visitor.source.title}")
            if (visitor.checkedInAt == null) {
                Button(onClick = onCheckIn) {
                    Text("Зарегистрировать и напечатать")
                }
            } else {
                Text("Посетитель уже зарегистрирован")
                OutlinedButton(onClick = onCheckIn) {
                    Text("Повторная печать")
                }
            }
        }
    }
}

@Composable
private fun NewVisitorScreen(vm: MainViewModel, modifier: Modifier = Modifier, onSaved: () -> Unit) {
    var lastName by remember { mutableStateOf("") }
    var firstName by remember { mutableStateOf("") }
    var company by remember { mutableStateOf("") }
    var position by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(VisitorType.GUEST) }

    LazyColumn(
        modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("Посетитель отсутствует в исходном списке", style = MaterialTheme.typography.titleMedium) }
        item { TextFieldRow("Фамилия *", lastName) { lastName = it } }
        item { TextFieldRow("Имя", firstName) { firstName = it } }
        item { TextFieldRow("Компания", company) { company = it } }
        item { TextFieldRow("Должность", position) { position = it } }
        item { TextFieldRow("Город", city) { city = it } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VisitorType.entries.forEach { item ->
                    FilterChip(selected = type == item, onClick = { type = item }, label = { Text(item.title) })
                }
            }
        }
        item {
            Button(
                onClick = {
                    if (vm.addVisitor(lastName, firstName, company, position, city, type)) onSaved()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Добавить и зарегистрировать")
            }
        }
    }
}

@Composable
private fun TextFieldRow(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SettingsScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    var printerName by remember(vm.printerConfig.name) { mutableStateOf(vm.printerConfig.name) }
    var host by remember(vm.printerConfig.host) { mutableStateOf(vm.printerConfig.host) }
    var port by remember(vm.printerConfig.port) { mutableStateOf(vm.printerConfig.port.toString()) }
    var width by remember(vm.printerConfig.widthMm) { mutableStateOf(vm.printerConfig.widthMm.toString()) }
    var height by remember(vm.printerConfig.heightMm) { mutableStateOf(vm.printerConfig.heightMm.toString()) }
    var confirmClearDatabase by remember { mutableStateOf(false) }
    var confirmClearPrinters by remember { mutableStateOf(false) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importFile(uri)
    }

    LazyColumn(
        modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Text("Загрузка базы", style = MaterialTheme.typography.headlineSmall) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(VisitorType.PARTNER, VisitorType.EMPLOYEE).forEach { type ->
                    FilterChip(
                        selected = vm.selectedImportType == type,
                        onClick = { vm.selectedImportType = type },
                        label = { Text(type.title) }
                    )
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ImportMode.entries.forEach { mode ->
                    FilterChip(
                        selected = vm.importMode == mode,
                        onClick = { vm.importMode = mode },
                        label = { Text(mode.title) }
                    )
                }
            }
        }
        item {
            OutlinedButton(
                onClick = { filePicker.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) },
                enabled = !vm.importBusy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (vm.importBusy) "Загрузка…" else "Выбрать и загрузить Excel-файл")
            }
        }
        vm.selectedFileName?.let { name -> item { Text("Выбран файл: $name") } }
        vm.lastImportResult?.let { result ->
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Загружено записей: ${result.imported}", style = MaterialTheme.typography.titleMedium)
                        result.warnings.forEach { warning -> Text("• $warning") }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(10.dp)) }
        item { Text("Принтер", style = MaterialTheme.typography.headlineSmall) }
        item { TextFieldRow("Название принтера", printerName) { printerName = it } }
        item { TextFieldRow("IP-адрес", host) { host = it } }
        item { TextFieldRow("Порт", port) { port = it } }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = width,
                    onValueChange = { width = it },
                    label = { Text("Ширина, мм") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = height,
                    onValueChange = { height = it },
                    label = { Text("Высота, мм") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { vm.updatePrinter(printerName, host, port, width, height); vm.checkPrinter() },
                    enabled = !vm.printerBusy,
                    modifier = Modifier.weight(1f)
                ) { Text("Проверить") }
                Button(
                    onClick = { vm.updatePrinter(printerName, host, port, width, height); vm.testPrint() },
                    enabled = !vm.printerBusy,
                    modifier = Modifier.weight(1f)
                ) { Text("Тестовая печать") }
            }
        }
        item { Spacer(Modifier.height(10.dp)) }
        item { Text("Очистка данных", style = MaterialTheme.typography.headlineSmall) }
        item {
            OutlinedButton(
                onClick = { confirmClearDatabase = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Очистить базу посетителей") }
        }
        item {
            OutlinedButton(
                onClick = { confirmClearPrinters = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Очистить принтеры") }
        }
    }

    if (confirmClearDatabase) {
        AlertDialog(
            onDismissRequest = { confirmClearDatabase = false },
            title = { Text("Очистить базу?") },
            text = { Text("Будут удалены все посетители и история регистраций. Отменить это действие нельзя.") },
            confirmButton = {
                Button(
                    onClick = { confirmClearDatabase = false; vm.clearDatabase() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Очистить") }
            },
            dismissButton = { OutlinedButton(onClick = { confirmClearDatabase = false }) { Text("Отмена") } }
        )
    }

    if (confirmClearPrinters) {
        AlertDialog(
            onDismissRequest = { confirmClearPrinters = false },
            title = { Text("Очистить принтеры?") },
            text = { Text("Сохранённые IP-адрес, порт и размер этикетки будут сброшены.") },
            confirmButton = {
                Button(
                    onClick = { confirmClearPrinters = false; vm.clearPrinters() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Очистить") }
            },
            dismissButton = { OutlinedButton(onClick = { confirmClearPrinters = false }) { Text("Отмена") } }
        )
    }
}
