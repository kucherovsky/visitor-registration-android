package ru.atol.visitorregistration.data.local

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import ru.atol.visitorregistration.model.LabelAttribute
import ru.atol.visitorregistration.model.LabelFontStyle
import ru.atol.visitorregistration.model.LabelLineConfig
import ru.atol.visitorregistration.model.LabelTemplate
import ru.atol.visitorregistration.model.LabelTemplateKind
import ru.atol.visitorregistration.model.LabelTextAlignment
import ru.atol.visitorregistration.model.PrinterResolution
import ru.atol.visitorregistration.model.defaultLabelTemplate

class LabelTemplateStore(context: Context) {
    private val preferences = context.getSharedPreferences("label-templates", Context.MODE_PRIVATE)

    fun load(kind: LabelTemplateKind): LabelTemplate {
        val raw = preferences.getString(kind.name, null) ?: return defaultLabelTemplate(kind)
        return runCatching { decode(raw, kind) }.getOrElse { defaultLabelTemplate(kind) }
    }

    fun save(template: LabelTemplate) {
        preferences.edit().putString(template.kind.name, encode(template)).apply()
    }

    private fun encode(template: LabelTemplate): String = JSONObject().apply {
        put("widthMm", template.widthMm)
        put("heightMm", template.heightMm)
        put("resolution", template.resolution.name)
        put("lines", JSONArray().apply {
            template.lines.forEach { line ->
                put(JSONObject().apply {
                    put("id", line.id)
                    put("attribute", line.attribute.name)
                    put("visible", line.visible)
                    put("fontName", line.fontName)
                    put("fontSize", line.fontSize)
                    put("style", line.style.name)
                    put("alignment", line.alignment.name)
                    put("xMm", line.xMm.toDouble())
                    put("yMm", line.yMm.toDouble())
                    put("automaticPosition", line.automaticPosition)
                })
            }
        })
    }.toString()

    private fun decode(raw: String, kind: LabelTemplateKind): LabelTemplate {
        val json = JSONObject(raw)
        val rows = json.getJSONArray("lines")
        val lines = buildList {
            for (index in 0 until rows.length()) {
                val row = rows.getJSONObject(index)
                add(
                    LabelLineConfig(
                        id = row.getString("id"),
                        attribute = LabelAttribute.valueOf(row.getString("attribute")),
                        visible = row.optBoolean("visible", true),
                        fontName = row.optString("fontName", "3").ifBlank { "3" },
                        fontSize = row.optInt("fontSize", 18).coerceIn(8, 72),
                        style = enumOrDefault(row.optString("style"), LabelFontStyle.NORMAL),
                        alignment = enumOrDefault(row.optString("alignment"), LabelTextAlignment.LEFT),
                        xMm = row.optDouble("xMm", 3.5).toFloat(),
                        yMm = row.optDouble("yMm", 3.0).toFloat(),
                        automaticPosition = row.optBoolean("automaticPosition", true)
                    )
                )
            }
        }
        return LabelTemplate(
            kind = kind,
            widthMm = json.optInt("widthMm", 70).coerceIn(20, 120),
            heightMm = json.optInt("heightMm", 50).coerceIn(15, 100),
            resolution = enumOrDefault(json.optString("resolution"), PrinterResolution.DPI_203),
            lines = lines.ifEmpty { defaultLabelTemplate(kind).lines }
        )
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(value: String, fallback: T): T =
        runCatching { enumValueOf<T>(value) }.getOrDefault(fallback)
}
