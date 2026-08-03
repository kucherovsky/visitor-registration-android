package ru.atol.visitorregistration.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import ru.atol.visitorregistration.model.PrinterConfig
import ru.atol.visitorregistration.model.PrinterEncoding

@Entity(tableName = "printers")
data class PrinterEntity(
    @PrimaryKey val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val widthMm: Int,
    val heightMm: Int,
    val encoding: String,
    val fontName: String,
    val isDefault: Boolean
)

fun PrinterConfig.toEntity() = PrinterEntity(
    id = id,
    name = name,
    host = host,
    port = port,
    widthMm = widthMm,
    heightMm = heightMm,
    encoding = encoding.name,
    fontName = fontName,
    isDefault = isDefault
)

fun PrinterEntity.toModel() = PrinterConfig(
    id = id,
    name = name,
    host = host,
    port = port,
    widthMm = widthMm,
    heightMm = heightMm,
    encoding = runCatching { PrinterEncoding.valueOf(encoding) }.getOrDefault(PrinterEncoding.WINDOWS_1251),
    fontName = fontName,
    isDefault = isDefault
)
