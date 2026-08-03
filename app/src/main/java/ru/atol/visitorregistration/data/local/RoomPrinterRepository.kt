package ru.atol.visitorregistration.data.local

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.atol.visitorregistration.data.PrinterRepository
import ru.atol.visitorregistration.model.PrinterConfig

class RoomPrinterRepository(private val database: AppDatabase) : PrinterRepository {
    private val dao = database.printerDao()

    override fun observeAll(): Flow<List<PrinterConfig>> = dao.observeAll().map { rows ->
        rows.map(PrinterEntity::toModel)
    }

    override suspend fun save(printer: PrinterConfig) {
        database.withTransaction {
            val shouldBeDefault = printer.isDefault || dao.count() == 0
            if (shouldBeDefault) dao.clearDefault()
            dao.upsert(printer.copy(isDefault = shouldBeDefault).toEntity())
        }
    }

    override suspend fun setDefault(printerId: String) {
        database.withTransaction {
            requireNotNull(dao.getById(printerId)) { "Принтер не найден" }
            dao.clearDefault()
            dao.markDefault(printerId)
        }
    }

    override suspend fun delete(printerId: String) {
        database.withTransaction {
            val removedWasDefault = dao.getById(printerId)?.isDefault == true
            dao.delete(printerId)
            if (removedWasDefault) dao.firstOrNull()?.let { dao.markDefault(it.id) }
        }
    }

    override suspend fun clearAll() = dao.deleteAll()
}
