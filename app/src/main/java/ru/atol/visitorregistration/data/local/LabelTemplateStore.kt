package ru.atol.visitorregistration.data.local

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import ru.atol.visitorregistration.model.LabelAttribute
import ru.atol.visitorregistration.model.LabelFontStyle
import ru.atol.visitorregistration.model.LabelLineConfig
import ru.atol.visitorregistration.model.LabelTemplate
import ru.atol.visitorregistration.model.LabelTemplateKind
import ru.atol.visitorregistration.model.LabelRotation
import ru.atol.visitorregistration.model.LabelTextAlignment
import ru.atol.visitorregistration.model.PrinterResolution
import ru.atol.visitorregistration.model.BOLD_LABEL_FONT
import ru.atol.visitorregistration.model.REGULAR_LABEL_FONT
import ru.atol.visitorregistration.model.defaultLabelTemplate

class LabelTemplateStore(context: Context) {
    private val preferences = context.getSharedPreferences("label-templates", Context.MODE_PRIVATE)

    fun load(kind: LabelTemplateKind): LabelTemplate {
        val raw = preferences.getString(kind.name, null) ?: return defaultLabelTemplate(kind)
        return runCatching {
            val json = JSONObject(raw)
            val template = decode(json, kind)
            if (json.optInt("schemaVersion", 1) < SCHEMA_VERSION) save(template)
            template
        }.getOrElse { defaultLabelTemplate(kind) }
    }

    fun save(template: LabelTemplate) {
        preferences.edit().putString(template.kind.name, encode(template)).apply()
    }

    fun clearAll() {
        preferences.edit().clear().apply()
    }

    private fun encode(template: LabelTemplate): String = JSONObject().apply {
        put("schemaVersion", SCHEMA_VERSION)
        put("widthMm", template.widthMm)
        put("heightMm", template.heightMm)
        put("resolution", template.resolution.name)
        put("copies", template.copies)
        put("rotation", template.rotation.name)
        put("alignment", template.alignment.name)
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

    private fun decode(json: JSONObject, kind: LabelTemplateKind): LabelTemplate {
        val storedSchemaVersion = json.optInt("schemaVersion", 1)
        val needsLegacyDefaultsMigration = storedSchemaVersion < 5
        val needsLatestDefaultsMigration = storedSchemaVersion < SCHEMA_VERSION
        val rows = json.getJSONArray("lines")
        val lines = buildList {
            for (index in 0 until rows.length()) {
                val row = rows.getJSONObject(index)
                val decoded = LabelLineConfig(
                        id = row.getString("id"),
                        attribute = LabelAttribute.valueOf(row.getString("attribute")),
                        visible = row.optBoolean("visible", true),
                        fontName = row.optString("fontName", "3").ifBlank { "3" },
                        fontSize = row.optInt("fontSize", 18).coerceIn(8, 72),
                        style = enumOrDefault(row.optString("style"), LabelFontStyle.NORMAL),
                        alignment = enumOrDefault(row.optString("alignment"), LabelTextAlignment.LEFT),
                        xMm = row.optDouble("xMm", 0.0).toFloat(),
                        yMm = row.optDouble("yMm", 0.0).toFloat(),
                        automaticPosition = row.optBoolean("automaticPosition", true)
                    )
                add(when {
                    needsLegacyDefaultsMigration -> decoded.withRecommendedDefaults(kind)
                    needsLatestDefaultsMigration -> decoded.withLatestDefaultSizes(kind)
                    else -> decoded
                })
            }
        }
        return LabelTemplate(
            kind = kind,
            widthMm = if (storedSchemaVersion < 7) 50 else
                json.optInt("widthMm", 50).coerceIn(20, 120),
            heightMm = if (storedSchemaVersion < 7) 70 else
                json.optInt("heightMm", 70).coerceIn(15, 100),
            resolution = enumOrDefault(json.optString("resolution"), PrinterResolution.DPI_203),
            copies = if (storedSchemaVersion < 8 && json.optInt("copies", 1) == 1) 2 else
                json.optInt("copies", 2).coerceIn(1, 4),
            rotation = enumOrDefault(json.optString("rotation"), LabelRotation.DEG_90),
            alignment = if (needsLegacyDefaultsMigration) LabelTextAlignment.LEFT else
                enumOrDefault(json.optString("alignment"), LabelTextAlignment.LEFT),
            lines = lines.ifEmpty { defaultLabelTemplate(kind).lines }
        )
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(value: String, fallback: T): T =
        runCatching { enumValueOf<T>(value) }.getOrDefault(fallback)

    private fun LabelLineConfig.withRecommendedDefaults(kind: LabelTemplateKind): LabelLineConfig {
        val bold = attribute == LabelAttribute.FULL_NAME ||
            (kind == LabelTemplateKind.EMPLOYEE && attribute == LabelAttribute.COMPANY)
        val defaultSize = when (attribute) {
            LabelAttribute.FULL_NAME -> 22
            LabelAttribute.COMPANY -> if (kind == LabelTemplateKind.EMPLOYEE) 16 else 12
            LabelAttribute.POSITION -> 10
            else -> 12
        }
        return copy(
            fontName = if (bold) BOLD_LABEL_FONT else REGULAR_LABEL_FONT,
            fontSize = defaultSize,
            style = LabelFontStyle.NORMAL,
            alignment = LabelTextAlignment.LEFT,
            xMm = 0f,
            yMm = if (attribute == LabelAttribute.FULL_NAME) 3f else 0f,
            automaticPosition = true
        )
    }

    private fun LabelLineConfig.withLatestDefaultSizes(kind: LabelTemplateKind): LabelLineConfig = when {
        kind == LabelTemplateKind.VISITOR && attribute == LabelAttribute.COMPANY -> copy(fontSize = 12)
        kind == LabelTemplateKind.EMPLOYEE && attribute == LabelAttribute.POSITION -> copy(fontSize = 10)
        else -> this
    }

    private companion object {
        const val SCHEMA_VERSION = 8
    }
}
