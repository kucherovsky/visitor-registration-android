package ru.atol.visitorregistration

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import java.util.Locale
import ru.atol.visitorregistration.model.LabelAttribute
import ru.atol.visitorregistration.model.LabelFontStyle
import ru.atol.visitorregistration.model.LabelLineConfig
import ru.atol.visitorregistration.model.LabelTemplate
import ru.atol.visitorregistration.model.LabelTemplateKind
import ru.atol.visitorregistration.model.LabelTextAlignment
import ru.atol.visitorregistration.model.PrinterConfig
import ru.atol.visitorregistration.model.PrinterEncoding
import ru.atol.visitorregistration.model.PrinterResolution
import ru.atol.visitorregistration.model.Visitor
import ru.atol.visitorregistration.model.VisitorType
import ru.atol.visitorregistration.printing.PlacedLabelText

private val AtolRed = Color(0xFFD71920)
private val AtolBlack = Color(0xFF111111)
private val AtolLightScheme = lightColorScheme(
    primary = AtolRed,
    onPrimary = Color.White,
    secondary = Color(0xFF565656),
    background = Color(0xFFF1F1F1),
    surface = Color.White,
    surfaceVariant = Color(0xFFE4E4E4),
    onSurface = Color(0xFF191919),
    outline = Color(0xFF777777),
    error = Color(0xFFB00020)
)
private val AtolDarkScheme = darkColorScheme(
    primary = Color(0xFFFF5961),
    onPrimary = Color.White,
    secondary = Color(0xFFBDBDBD),
    background = Color(0xFF141414),
    surface = Color(0xFF242424),
    surfaceVariant = Color(0xFF343434),
    onSurface = Color(0xFFF3F3F3),
    outline = Color(0xFF8B8B8B),
    error = Color(0xFFFF6B75)
)

private enum class AppSection(val title: String, val shortTitle: String) {
    SEARCH("Регистрация", "Поиск"),
    ADD("Новый посетитель", "Добавить"),
    DATABASE("Загруженная база", "База"),
    SETTINGS("Настройки", "Настр.")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) AtolDarkScheme else AtolLightScheme) {
                VisitorRegistrationApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VisitorRegistrationApp(vm: MainViewModel = viewModel()) {
    var sectionName by rememberSaveable { mutableStateOf(AppSection.SEARCH.name) }
    val section = AppSection.valueOf(sectionName)
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(vm.statusMessage) {
        vm.statusMessage?.let { snackbar.showSnackbar(it) }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val tablet = maxWidth >= 700.dp
        if (tablet) {
            Row(Modifier.fillMaxSize()) {
                AppNavigationRail(section) { sectionName = it.name }
                AppScaffold(
                    vm = vm,
                    section = section,
                    snackbar = snackbar,
                    modifier = Modifier.weight(1f),
                    onNavigate = { sectionName = it.name },
                    bottomBar = null
                )
            }
        } else {
            AppScaffold(
                vm = vm,
                section = section,
                snackbar = snackbar,
                modifier = Modifier.fillMaxSize(),
                onNavigate = { sectionName = it.name },
                bottomBar = { AppBottomNavigation(section) { sectionName = it.name } }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppScaffold(
    vm: MainViewModel,
    section: AppSection,
    snackbar: SnackbarHostState,
    modifier: Modifier,
    onNavigate: (AppSection) -> Unit,
    bottomBar: (@Composable () -> Unit)?
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(section.title) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AtolBlack,
                    titleContentColor = Color.White
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { bottomBar?.invoke() }
    ) { padding ->
        when (section) {
            AppSection.SEARCH -> RegistrationScreen(vm, Modifier.padding(padding))
            AppSection.ADD -> NewVisitorScreen(vm, Modifier.padding(padding)) { onNavigate(AppSection.SEARCH) }
            AppSection.DATABASE -> DatabaseScreen(vm, Modifier.padding(padding))
            AppSection.SETTINGS -> SettingsScreen(vm, Modifier.padding(padding))
        }
    }
}

@Composable
private fun AppBottomNavigation(selected: AppSection, onSelect: (AppSection) -> Unit) {
    NavigationBar {
        AppSection.entries.forEach { item ->
            NavigationBarItem(
                selected = selected == item,
                onClick = { onSelect(item) },
                icon = { Text(if (selected == item) "●" else "○") },
                label = { Text(item.shortTitle, maxLines = 1) }
            )
        }
    }
}

@Composable
private fun AppNavigationRail(selected: AppSection, onSelect: (AppSection) -> Unit) {
    NavigationRail(header = { Text("Регистрация", modifier = Modifier.padding(12.dp)) }) {
        AppSection.entries.forEach { item ->
            NavigationRailItem(
                selected = selected == item,
                onClick = { onSelect(item) },
                icon = { Text(if (selected == item) "●" else "○") },
                label = { Text(item.shortTitle) },
                alwaysShowLabel = true
            )
        }
    }
}

@Composable
private fun RegistrationScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(20.dp)) {
        OutlinedTextField(
            value = vm.searchQuery,
            onValueChange = { vm.searchQuery = it },
            label = { Text("Фамилия, имя или компания") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        when {
            vm.searchQuery.isBlank() -> Text("Начните вводить фамилию, имя или компанию")
            vm.searchResults.isEmpty() -> Text("Посетитель не найден. Добавьте его вручную.")
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(vm.searchResults, key = Visitor::id) { visitor ->
                    VisitorRegistrationCard(visitor) { vm.checkInAndPrint(visitor) }
                }
            }
        }
    }
}

@Composable
private fun VisitorRegistrationCard(visitor: Visitor, onCheckIn: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(visitor.fullName, style = MaterialTheme.typography.titleLarge)
            Text(listOf(visitor.company, visitor.position, visitor.city).filter(String::isNotBlank).joinToString(" · "))
            Text("${visitor.type.title} · ${visitor.source.title}", color = MaterialTheme.colorScheme.secondary)
            if (visitor.checkedInAt == null) {
                Button(onClick = onCheckIn, modifier = Modifier.fillMaxWidth()) { Text("Зарегистрировать и напечатать") }
            } else {
                Text("✓ Посетитель зарегистрирован", color = Color(0xFF16813A))
                OutlinedButton(onClick = onCheckIn, modifier = Modifier.fillMaxWidth()) { Text("Повторная печать") }
            }
        }
    }
}

@Composable
private fun NewVisitorScreen(vm: MainViewModel, modifier: Modifier = Modifier, onSaved: () -> Unit) {
    var lastName by rememberSaveable { mutableStateOf("") }
    var firstName by rememberSaveable { mutableStateOf("") }
    var company by rememberSaveable { mutableStateOf("") }
    var position by rememberSaveable { mutableStateOf("") }
    var city by rememberSaveable { mutableStateOf("") }
    var typeName by rememberSaveable { mutableStateOf(VisitorType.GUEST.name) }
    val type = VisitorType.valueOf(typeName)

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
                listOf(VisitorType.GUEST, VisitorType.EMPLOYEE).forEach { option ->
                    FilterChip(
                        selected = type == option,
                        onClick = { typeName = option.name },
                        label = { Text(option.title) }
                    )
                }
            }
        }
        item {
            Button(
                onClick = {
                    if (vm.addVisitor(lastName, firstName, company, position, city, type)) onSaved()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Добавить и зарегистрировать") }
        }
    }
}

@Composable
private fun DatabaseScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 20.dp)
    ) {
        item { Text("Всего записей: ${vm.visitors.size}", color = MaterialTheme.colorScheme.secondary) }
        if (vm.visitors.isEmpty()) item { Text("База пока не загружена") }
        items(vm.visitors, key = Visitor::id) { visitor -> DatabaseVisitorCard(visitor) }
    }
}

@Composable
private fun DatabaseVisitorCard(visitor: Visitor) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(visitor.fullName, style = MaterialTheme.typography.titleMedium)
                    Text(visitor.source.title, color = MaterialTheme.colorScheme.secondary)
                }
                if (visitor.checkedInAt != null) {
                    Box(
                        Modifier.size(30.dp).background(Color(0xFFD7F5DD), shape = MaterialTheme.shapes.extraLarge),
                        contentAlignment = Alignment.Center
                    ) { Text("✓", color = Color(0xFF11692B), fontWeight = FontWeight.Bold) }
                }
            }
            HorizontalDivider()
            DatabaseField("Фамилия", visitor.lastName)
            DatabaseField("Имя", visitor.firstName)
            DatabaseField("Компания", visitor.company)
            DatabaseField("Должность", visitor.position)
            DatabaseField("Город", visitor.city)
            DatabaseField("Тип", visitor.type.title)
            DatabaseField("Статус", if (visitor.checkedInAt != null) "Зарегистрирован" else "")
        }
    }
}

@Composable
private fun DatabaseField(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.width(112.dp))
        Text(value, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun TextFieldRow(label: String, value: String, enabled: Boolean = true, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SettingsScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    var printerName by rememberSaveable { mutableStateOf("") }
    var host by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf("9100") }
    var encodingName by rememberSaveable { mutableStateOf(PrinterEncoding.WINDOWS_1251.name) }
    var editingPrinter by remember { mutableStateOf<PrinterConfig?>(null) }
    var confirmClearDatabase by remember { mutableStateOf(false) }
    var confirmClearPrinters by remember { mutableStateOf(false) }
    var printerToDelete by remember { mutableStateOf<PrinterConfig?>(null) }
    val encoding = PrinterEncoding.valueOf(encodingName)
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importFile)
    }
    val exportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri -> uri?.let(vm::exportAttendance) }

    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 20.dp)
    ) {
        item {
            SettingsCard("Загрузка базы") {
                Text("Кого загружаем?", color = MaterialTheme.colorScheme.secondary)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(VisitorType.PARTNER, VisitorType.EMPLOYEE).forEach { type ->
                        FilterChip(
                            selected = vm.selectedImportType == type,
                            onClick = { vm.selectedImportType = type },
                            label = { Text(type.title) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Text("Как загрузить?", color = MaterialTheme.colorScheme.secondary)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ImportMode.entries.forEach { mode ->
                        FilterChip(
                            selected = vm.importMode == mode,
                            onClick = { vm.importMode = mode },
                            label = { Text(mode.title) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                OutlinedButton(
                    onClick = { filePicker.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) },
                    enabled = !vm.importBusy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (vm.importBusy) "Загрузка…" else "Выбрать Excel-файл") }
                vm.selectedFileName?.let { Text("Выбран файл: $it") }
                vm.lastImportResult?.let { Text("Загружено записей: ${it.imported}") }
            }
        }
        item {
            SettingsCard("Выгрузка результатов") {
                Text("Зарегистрировано посетителей: ${vm.checkedInCount}")
                Button(
                    onClick = { exportPicker.launch("пришедшие_${LocalDate.now()}.xlsx") },
                    enabled = vm.checkedInCount > 0,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Выгрузить пришедших в Excel") }
            }
        }
        item {
            SettingsCard("Сохранённые принтеры") {
                if (vm.printers.isEmpty()) Text("Принтеры пока не добавлены")
                vm.printers.forEach { printer ->
                    Text(printer.name, style = MaterialTheme.typography.titleMedium)
                    Text("${printer.host}:${printer.port} · ${printer.encoding.title}")
                    if (printer.isDefault) Text("Основной принтер", color = AtolRed)
                    if (!printer.isDefault) {
                        OutlinedButton(onClick = { vm.setDefaultPrinter(printer.id) }) { Text("Сделать основным") }
                    }
                    OutlinedButton(onClick = {
                        editingPrinter = printer
                        printerName = printer.name
                        host = printer.host
                        port = printer.port.toString()
                        encodingName = printer.encoding.name
                    }, modifier = Modifier.fillMaxWidth()) { Text("Изменить") }
                    OutlinedButton(
                        onClick = { printerToDelete = printer },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Удалить") }
                    HorizontalDivider()
                }
            }
        }
        item {
            SettingsCard(if (editingPrinter == null) "Добавить принтер" else "Изменить принтер") {
                TextFieldRow("Название принтера", printerName) { printerName = it }
                TextFieldRow("IP-адрес", host) { host = it }
                TextFieldRow("Порт", port) { port = it }
                DropdownField("Кодировка русского текста", encoding, PrinterEncoding.entries.toList(), PrinterEncoding::title) {
                    encodingName = it.name
                }
                OutlinedButton(
                    onClick = { vm.checkPrinter(printerName, host, port, encoding) },
                    enabled = !vm.printerBusy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Проверить соединение") }
                Button(
                    onClick = { vm.testPrint(printerName, host, port, encoding) },
                    enabled = !vm.printerBusy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Тестовая печать") }
                Button(
                    onClick = {
                        if (vm.savePrinter(printerName, host, port, encoding, editingPrinter)) {
                            editingPrinter = null
                            printerName = ""
                            host = ""
                            port = "9100"
                            encodingName = PrinterEncoding.WINDOWS_1251.name
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (editingPrinter == null) "Добавить принтер" else "Сохранить изменения") }
            }
        }
        item { LabelTemplateEditor(vm) }
        item {
            SettingsCard("Очистка данных") {
                OutlinedButton(
                    onClick = { confirmClearDatabase = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Очистить базу посетителей") }
                OutlinedButton(
                    onClick = { confirmClearPrinters = true },
                    enabled = vm.printers.isNotEmpty(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Очистить принтеры") }
            }
        }
        item { SettingsCard("О приложении") { Text("Версия ${BuildConfig.VERSION_NAME} · тестовая сборка") } }
    }

    ConfirmDialog(confirmClearDatabase, "Очистить базу?", "Будут удалены все посетители и история регистраций.", {
        confirmClearDatabase = false
        vm.clearDatabase()
    }) { confirmClearDatabase = false }
    ConfirmDialog(confirmClearPrinters, "Очистить принтеры?", "Все сохранённые принтеры будут удалены.", {
        confirmClearPrinters = false
        vm.clearPrinters()
    }) { confirmClearPrinters = false }
    printerToDelete?.let { printer ->
        ConfirmDialog(true, "Удалить принтер?", printer.name, {
            printerToDelete = null
            vm.deletePrinter(printer.id)
        }) { printerToDelete = null }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            content()
        }
    }
}

@Composable
private fun LabelTemplateEditor(vm: MainViewModel) {
    var kindName by rememberSaveable { mutableStateOf(LabelTemplateKind.VISITOR.name) }
    var visitorDraft by remember { mutableStateOf(vm.visitorTemplate) }
    var employeeDraft by remember { mutableStateOf(vm.employeeTemplate) }
    var tsplText by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
    val kind = LabelTemplateKind.valueOf(kindName)
    val template = if (kind == LabelTemplateKind.VISITOR) visitorDraft else employeeDraft

    fun setTemplate(next: LabelTemplate) {
        val resolved = vm.applyAutomaticLayout(next)
        if (kind == LabelTemplateKind.VISITOR) visitorDraft = resolved else employeeDraft = resolved
    }

    SettingsCard("Настройки этикетки") {
        Text("Какой шаблон редактируем?", color = MaterialTheme.colorScheme.secondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LabelTemplateKind.entries.forEach { option ->
                FilterChip(
                    selected = kind == option,
                    onClick = { kindName = option.name },
                    label = { Text(option.title) }
                )
            }
        }
        DropdownField("Разрешение принтера", template.resolution, PrinterResolution.entries.toList(), PrinterResolution::title) {
            setTemplate(template.copy(resolution = it))
        }
        TextFieldRow("Ширина этикетки, мм", template.widthMm.toString()) { value ->
            value.toIntOrNull()?.takeIf { it in 20..120 }?.let { setTemplate(template.copy(widthMm = it)) }
        }
        TextFieldRow("Высота этикетки, мм", template.heightMm.toString()) { value ->
            value.toIntOrNull()?.takeIf { it in 15..100 }?.let { setTemplate(template.copy(heightMm = it)) }
        }
        LabelPreview(template, vm.previewLayout(template))
        template.lines.forEachIndexed { index, line ->
            LabelLineEditor(
                line = line,
                index = index,
                total = template.lines.size,
                onChange = { changed ->
                    setTemplate(template.copy(lines = template.lines.toMutableList().also { it[index] = changed }))
                },
                onMoveUp = {
                    if (index > 0) setTemplate(template.copy(lines = template.lines.toMutableList().also {
                        val moved = it.removeAt(index)
                        it.add(index - 1, moved)
                    }))
                },
                onMoveDown = {
                    if (index < template.lines.lastIndex) setTemplate(template.copy(lines = template.lines.toMutableList().also {
                        val moved = it.removeAt(index)
                        it.add(index + 1, moved)
                    }))
                }
            )
        }
        OutlinedButton(
            onClick = {
                setTemplate(template.copy(lines = template.lines + LabelLineConfig(attribute = LabelAttribute.REGISTRATION_DATE)))
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Добавить текстовый блок") }
        Button(onClick = { vm.saveLabelTemplate(template) }, modifier = Modifier.fillMaxWidth()) { Text("Сохранить шаблон") }
        OutlinedButton(onClick = { tsplText = vm.labelTemplateTspl(template) }, modifier = Modifier.fillMaxWidth()) {
            Text("Показать шаблон TSPL")
        }
    }

    tsplText?.let { text ->
        AlertDialog(
            onDismissRequest = { tsplText = null },
            title = { Text("Шаблон TSPL") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = {},
                    readOnly = true,
                    minLines = 12,
                    maxLines = 20,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = { clipboard.setText(AnnotatedString(text)) }) { Text("Копировать") }
            },
            dismissButton = { TextButton(onClick = { tsplText = null }) { Text("Закрыть") } }
        )
    }
}

@Composable
private fun LabelLineEditor(
    line: LabelLineConfig,
    index: Int,
    total: Int,
    onChange: (LabelLineConfig) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    val standardFonts = (1..8).map(Int::toString)
    val selectedFont = if (line.fontName in standardFonts) line.fontName else "Другой"
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = line.visible, onCheckedChange = { onChange(line.copy(visible = it)) })
                Text("Текстовый блок ${index + 1}", modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                OutlinedButton(onClick = onMoveUp, enabled = index > 0) { Text("↑") }
                Spacer(Modifier.width(6.dp))
                OutlinedButton(onClick = onMoveDown, enabled = index < total - 1) { Text("↓") }
            }
            DropdownField("Атрибут", line.attribute, LabelAttribute.entries.toList(), LabelAttribute::title) {
                onChange(line.copy(attribute = it))
            }
            DropdownField("Шрифт", selectedFont, standardFonts + "Другой", { value ->
                if (value == "Другой") value else "Стандартный $value"
            }) { selected ->
                onChange(line.copy(fontName = if (selected == "Другой") "" else selected))
            }
            TextFieldRow("Имя нестандартного шрифта", if (selectedFont == "Другой") line.fontName else "", selectedFont == "Другой") {
                onChange(line.copy(fontName = it))
            }
            TextFieldRow("Размер шрифта", line.fontSize.toString()) { value ->
                value.toIntOrNull()?.takeIf { it in 8..72 }?.let { onChange(line.copy(fontSize = it)) }
            }
            DropdownField("Начертание", line.style, LabelFontStyle.entries.toList(), LabelFontStyle::title) {
                onChange(line.copy(style = it))
            }
            DropdownField("Выравнивание", line.alignment, LabelTextAlignment.entries.toList(), LabelTextAlignment::title) {
                onChange(line.copy(alignment = it))
            }
            TextFieldRow("Позиция X, мм", formatCoordinate(line.xMm)) { value ->
                value.replace(',', '.').toFloatOrNull()?.takeIf { it >= 0f }?.let {
                    onChange(line.copy(xMm = it, automaticPosition = false))
                }
            }
            TextFieldRow("Позиция Y, мм", formatCoordinate(line.yMm)) { value ->
                value.replace(',', '.').toFloatOrNull()?.takeIf { it >= 0f }?.let {
                    onChange(line.copy(yMm = it, automaticPosition = false))
                }
            }
            OutlinedButton(
                onClick = { onChange(line.copy(automaticPosition = true)) },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (line.automaticPosition) "Координаты рассчитываются автоматически" else "Рассчитать координаты автоматически") }
        }
    }
}

@Composable
private fun LabelPreview(template: LabelTemplate, placements: List<PlacedLabelText>) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("Превью · ${template.kind.title} · ${template.widthMm} × ${template.heightMm} мм · ${template.resolution.title}")
        BoxWithConstraints(
            Modifier.fillMaxWidth().widthIn(max = 620.dp).aspectRatio(template.widthMm.toFloat() / template.heightMm)
                .background(Color.White).border(1.dp, Color.DarkGray)
        ) {
            val scale = maxWidth.value / template.widthMm
            placements.forEach { placed ->
                val x = (placed.xMm * scale).dp
                val y = (placed.yMm * scale).dp
                val availableWidth = ((template.widthMm - placed.xMm).coerceAtLeast(2f) * scale).dp
                val previewFont = (placed.fontSize * 25.4f / 72f * scale).coerceAtLeast(6f).sp
                Text(
                    text = placed.text,
                    color = Color.Black,
                    fontSize = previewFont,
                    fontWeight = if (placed.style == LabelFontStyle.BOLD) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (placed.style == LabelFontStyle.ITALIC) FontStyle.Italic else FontStyle.Normal,
                    textDecoration = if (placed.style == LabelFontStyle.UNDERLINE) TextDecoration.Underline else TextDecoration.None,
                    textAlign = when (placed.alignment) {
                        LabelTextAlignment.LEFT -> TextAlign.Left
                        LabelTextAlignment.CENTER -> TextAlign.Center
                        LabelTextAlignment.RIGHT -> TextAlign.Right
                    },
                    maxLines = 1,
                    modifier = Modifier.offset(x, y).width(availableWidth)
                )
            }
        }
    }
}

@Composable
private fun <T> DropdownField(
    label: String,
    selected: T,
    options: List<T>,
    title: (T) -> String,
    onSelected: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, color = MaterialTheme.colorScheme.secondary)
        Box(Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(title(selected), modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                Text("▾")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(title(option)) },
                        onClick = {
                            expanded = false
                            onSelected(option)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfirmDialog(
    visible: Boolean,
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                Text("Подтвердить")
            }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

private fun formatCoordinate(value: Float): String = String.format(Locale.US, "%.1f", value)
