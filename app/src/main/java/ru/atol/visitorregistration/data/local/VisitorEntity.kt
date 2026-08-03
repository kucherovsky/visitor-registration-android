package ru.atol.visitorregistration.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import ru.atol.visitorregistration.model.Visitor
import ru.atol.visitorregistration.model.VisitorSource
import ru.atol.visitorregistration.model.VisitorType

@Entity(tableName = "visitors")
data class VisitorEntity(
    @PrimaryKey val id: String,
    val lastName: String,
    val firstName: String,
    val company: String,
    val position: String,
    val city: String,
    val type: String,
    val source: String,
    val checkedInAt: Long?,
    val printCount: Int
)

fun Visitor.toEntity() = VisitorEntity(
    id = id,
    lastName = lastName,
    firstName = firstName,
    company = company,
    position = position,
    city = city,
    type = type.name,
    source = source.name,
    checkedInAt = checkedInAt,
    printCount = printCount
)

fun VisitorEntity.toModel() = Visitor(
    id = id,
    lastName = lastName,
    firstName = firstName,
    company = company,
    position = position,
    city = city,
    type = VisitorType.valueOf(type),
    source = VisitorSource.valueOf(source),
    checkedInAt = checkedInAt,
    printCount = printCount
)
