package ru.atol.visitorregistration.data.local

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.atol.visitorregistration.data.VisitorRepository
import ru.atol.visitorregistration.model.Visitor
import ru.atol.visitorregistration.model.VisitorSource
import ru.atol.visitorregistration.model.VisitorType

class RoomVisitorRepository(private val database: AppDatabase) : VisitorRepository {
    private val dao = database.visitorDao()

    override fun observeAll(): Flow<List<Visitor>> = dao.observeAll().map { rows ->
        rows.map(VisitorEntity::toModel)
    }

    override suspend fun save(visitor: Visitor) = dao.upsert(visitor.toEntity())

    override suspend fun saveAll(visitors: List<Visitor>) = dao.upsertAll(visitors.map(Visitor::toEntity))

    override suspend fun checkIn(visitorId: String): Visitor {
        dao.checkIn(visitorId, System.currentTimeMillis())
        return requireNotNull(dao.getById(visitorId)) { "Посетитель не найден" }.toModel()
    }

    override suspend fun markPrinted(visitorId: String) = dao.markPrinted(visitorId)

    override suspend fun replaceImported(type: VisitorType, visitors: List<Visitor>) {
        val source = when (type) {
            VisitorType.PARTNER -> VisitorSource.PARTNER_FILE
            VisitorType.EMPLOYEE -> VisitorSource.EMPLOYEE_FILE
            VisitorType.GUEST -> error("Гостевой список не импортируется")
        }
        database.withTransaction {
            dao.deleteBySource(source.name)
            dao.upsertAll(visitors.map(Visitor::toEntity))
        }
    }

    override suspend fun clearAll() = dao.deleteAll()
}
