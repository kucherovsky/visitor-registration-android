package ru.atol.visitorregistration.printing

import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.atol.visitorregistration.data.PrinterService
import ru.atol.visitorregistration.model.PrinterConfig
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

    override suspend fun printTest(config: PrinterConfig): Result<Unit> = send(
        config,
        buildString {
            appendLine("SIZE ${config.widthMm} mm,${config.heightMm} mm")
            appendLine("GAP 2 mm,0 mm")
            appendLine("DIRECTION 1")
            appendLine("CLS")
            appendLine("TEXT 30,30,\"3\",0,1,1,\"TEST PRINT\"")
            appendLine("TEXT 30,85,\"2\",0,1,1,\"TSPL CONNECTION OK\"")
            appendLine("PRINT 1,1")
        }
    )

    override suspend fun printBadge(config: PrinterConfig, visitor: Visitor): Result<Unit> = send(
        config,
        buildString {
            appendLine("SIZE ${config.widthMm} mm,${config.heightMm} mm")
            appendLine("GAP 2 mm,0 mm")
            appendLine("DIRECTION 1")
            appendLine("CLS")
            appendLine("TEXT 24,24,\"3\",0,1,1,\"${visitor.fullName.tsplSafe()}\"")
            appendLine("TEXT 24,80,\"2\",0,1,1,\"${visitor.company.tsplSafe()}\"")
            appendLine("PRINT 1,1")
        }
    )

    private suspend fun send(config: PrinterConfig, command: String): Result<Unit> = runCatching {
        require(config.host.isNotBlank()) { "Укажите IP-адрес принтера" }
        withContext(Dispatchers.IO) {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(config.host, config.port), CONNECT_TIMEOUT_MS)
                socket.getOutputStream().use { output ->
                    output.write(command.toByteArray(Charsets.UTF_8))
                    output.flush()
                }
            }
        }
    }

    private fun String.tsplSafe(): String = replace("\\", " ").replace("\"", "'")

    private companion object {
        const val CONNECT_TIMEOUT_MS = 3_000
    }
}
