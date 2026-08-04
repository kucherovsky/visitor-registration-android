package ru.atol.visitorregistration.printing

import java.net.InetSocketAddress
import java.net.Socket
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.atol.visitorregistration.data.PrinterService
import ru.atol.visitorregistration.model.PrinterConfig
import ru.atol.visitorregistration.model.PrinterEncoding
import ru.atol.visitorregistration.model.LabelTemplate
import ru.atol.visitorregistration.model.Visitor

class TsplPrinterClient(
    private val templateEngine: TsplTemplateEngine = TsplTemplateEngine()
) : PrinterService {
    override suspend fun checkConnection(config: PrinterConfig): Result<Unit> = runCatching {
        require(config.host.isNotBlank()) { "Укажите IP-адрес принтера" }
        withContext(Dispatchers.IO) {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(config.host, config.port), CONNECT_TIMEOUT_MS)
            }
        }
    }

    override suspend fun printTest(config: PrinterConfig, template: LabelTemplate): Result<Unit> = runCatching {
        val command = ByteArrayOutputStream().apply {
            appendHeader(template)
            val dotsPerMm = template.resolution.dpi / 25.4f
            val font = template.lines.firstOrNull { it.visible }?.fontName?.ifBlank { "3" } ?: "3"
            PrinterEncoding.entries.forEachIndexed { index, encoding ->
                appendText(
                    encoding = encoding,
                    x = (3f * dotsPerMm).toInt(),
                    y = ((3f + index * 10f) * dotsPerMm).toInt(),
                    font = font,
                    text = "${encoding.title}: Иванов Иван, Ёжик"
                )
            }
            appendAscii("PRINT 1,1\r\n")
        }.toByteArray()
        sendBytes(config, command).getOrThrow()
    }

    override suspend fun printBadge(config: PrinterConfig, visitor: Visitor, template: LabelTemplate): Result<Unit> = runCatching {
        val command = templateEngine.printBytes(template, visitor, config.encoding)
        sendBytes(config, command).getOrThrow()
    }

    private fun ByteArrayOutputStream.appendHeader(template: LabelTemplate) {
        appendAscii("REM TARGET_DPI ${template.resolution.dpi}\r\n")
        appendAscii("SIZE ${template.widthMm} mm,${template.heightMm} mm\r\n")
        appendAscii("GAP 2 mm,0 mm\r\n")
        appendAscii("DIRECTION 1\r\n")
        appendAscii("CLS\r\n")
    }

    private fun ByteArrayOutputStream.appendText(
        encoding: PrinterEncoding,
        x: Int,
        y: Int,
        font: String,
        text: String
    ) {
        if (text.isBlank()) return
        appendAscii("CODEPAGE ${encoding.codePage}\r\n")
        appendAscii("TEXT $x,$y,\"${font.tsplFontSafe()}\",0,1,1,\"")
        write(text.tsplSafe().toByteArray(charset(encoding.charsetName)))
        appendAscii("\"\r\n")
    }

    private fun ByteArrayOutputStream.appendAscii(value: String) {
        write(value.toByteArray(Charsets.US_ASCII))
    }

    private suspend fun sendBytes(config: PrinterConfig, command: ByteArray): Result<Unit> = runCatching {
        require(config.host.isNotBlank()) { "Укажите IP-адрес принтера" }
        withContext(Dispatchers.IO) {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(config.host, config.port), CONNECT_TIMEOUT_MS)
                socket.getOutputStream().use { output ->
                    output.write(command)
                    output.flush()
                }
            }
        }
    }

    private fun String.tsplSafe(): String = replace("\\", " ").replace("\"", "'")
    private fun String.tsplFontSafe(): String = replace("\\", "").replace("\"", "").ifBlank { "3" }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 3_000
    }
}
