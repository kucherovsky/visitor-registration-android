package ru.atol.visitorregistration.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import ru.atol.visitorregistration.model.PrinterConfig

@Entity(tableName = "printers")
data class PrinterEntity(
    @PrimaryKey val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val widthMm: Int,
    val heightMm: Int,
    val isDefault: Boolean
)

fun PrinterConfig.toEntity() = PrinterEntity(
    id = id,
    name = name,
    host = host,
    port = port,
    widthMm = widthMm,
    heightMm = heightMm,
    isDefault = isDefault
)

fun PrinterEntity.toModel() = PrinterConfig(
    id = id,
    name = name,
    host = host,
    port = port,
    widthMm = widthMm,
    heightMm = heightMm,
    isDefault = isDefault
)
