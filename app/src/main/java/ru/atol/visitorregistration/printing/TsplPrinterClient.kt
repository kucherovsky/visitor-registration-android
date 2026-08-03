package ru.atol.visitorregistration.printing

import java.net.InetSocketAddress
import java.net.Socket
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.atol.visitorregistration.data.PrinterService
import ru.atol.visitorregistration.model.PrinterConfig
import ru.atol.visitorregistration.model.PrinterEncoding
import ru.atol.visitorregistration.model.Visitor

class TsplPrinterClient : PrinterService {
    override suspend fun checkConnection(config: PrinterConfig): Result<Unit> = runCatching {
        require(config.host.isNotBlank()) { "Укажите IP-адрес принтера" }
        withContext(Dispatchers.IO) {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(config.host, config.port), CONNECT_TIMEOUT_MS)
            }
        }
    }

    override suspend fun printTest(config: PrinterConfig): Result<Unit> = runCatching {
        val command = ByteArrayOutputStream().apply {
            appendHeader(config)
            PrinterEncoding.entries.forEachIndexed { index, encoding ->
                appendText(
                    encoding = encoding,
                    x = 24,
                    y = 20 + index * 58,
                    font = config.fontName,
                    text = "${encoding.title}: ТЕСТ ЯЁЙ"
                )
            }
            appendAscii("PRINT 1,1\r\n")
        }.toByteArray()
        sendBytes(config, command).getOrThrow()
    }

    override suspend fun printBadge(config: PrinterConfig, visitor: Visitor): Result<Unit> = runCatching {
        val command = ByteArrayOutputStream().apply {
            appendHeader(config)
            appendText(config.encoding, 24, 24, config.fontName, visitor.fullName)
            appendText(config.encoding, 24, 82, config.fontName, visitor.company)
            appendText(config.encoding, 24, 126, config.fontName, visitor.position)
            appendAscii("PRINT 1,1\r\n")
        }.toByteArray()
        sendBytes(config, command).getOrThrow()
    }

    private fun ByteArrayOutputStream.appendHeader(config: PrinterConfig) {
        appendAscii("SIZE ${config.widthMm} mm,${config.heightMm} mm\r\n")
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
