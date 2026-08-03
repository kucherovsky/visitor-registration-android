package ru.atol.visitorregistration.data.local

import android.content.Context
import ru.atol.visitorregistration.model.PrinterConfig

class PrinterPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("printer", Context.MODE_PRIVATE)

    fun load(): PrinterConfig = PrinterConfig(
        name = preferences.getString("name", null) ?: "Основной принтер",
        host = preferences.getString("host", null) ?: "",
        port = preferences.getInt("port", 9100),
        widthMm = preferences.getInt("width_mm", 58),
        heightMm = preferences.getInt("height_mm", 40)
    )

    fun save(config: PrinterConfig) {
        preferences.edit()
            .putString("name", config.name)
            .putString("host", config.host)
            .putInt("port", config.port)
            .putInt("width_mm", config.widthMm)
            .putInt("height_mm", config.heightMm)
            .apply()
    }

    fun clear() {
        preferences.edit().clear().apply()
    }
}
