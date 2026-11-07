package com.example.stockapplication.data

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.Flow

/**
 * Validation and id generation for the supplies section.
 *
 * The database work stays in [StockDao] so an entry, its lines and the material stock changes all
 * share one Room transaction. This class only trims user input, rejects duplicate names, and turns
 * outcomes into values the ViewModel can localise.
 */
class SuppliesRepository(
    private val dao: StockDao,
    private val now: () -> Long = System::currentTimeMillis,
) {

    fun observeProviders(): Flow<List<ProviderSummary>> = dao.observeProviders()

    fun observeMaterials(): Flow<List<MaterialRow>> = dao.observeMaterials()

    fun observeMaterialEntries(): Flow<List<MaterialEntry>> = dao.observeMaterialEntries()

    fun observeMaterialEntriesWithLines(): Flow<List<MaterialEntryWithLines>> =
        dao.observeMaterialEntriesWithLines()

    fun observeMaterialEntriesForProvider(providerId: Long): Flow<List<MaterialEntry>> =
        dao.observeMaterialEntriesForProvider(providerId)

    fun observeMaterialEntry(entryId: String): Flow<MaterialEntryWithLines?> =
        dao.observeMaterialEntry(entryId)

    suspend fun findProvider(providerId: Long): Provider? = dao.findProvider(providerId)

    suspend fun findMaterial(materialId: Long): Material? = dao.findMaterial(materialId)

    suspend fun findMaterialEntry(entryId: String): MaterialEntry? =
        dao.findMaterialEntry(entryId)

    suspend fun saveProvider(provider: Provider): SaveProviderResult {
        val clean = provider.copy(
            name = provider.name.trim(),
            phone = provider.phone.trim(),
            address = provider.address.trim(),
            notes = provider.notes.trim(),
        )
        if (clean.name.isEmpty()) return SaveProviderResult.EmptyName

        val clash = dao.findProviderByName(clean.name)
        if (clash != null && clash.id != clean.id) {
            return SaveProviderResult.DuplicateName(clean.name)
        }

        return if (clean.id == 0L) {
            val id = dao.insertProvider(clean)
            SaveProviderResult.Success(clean.copy(id = id))
        } else {
            if (dao.findProvider(clean.id) == null) return SaveProviderResult.NotFound
            dao.updateProvider(clean)
            SaveProviderResult.Success(clean)
        }
    }

    suspend fun deleteProvider(providerId: Long) {
        dao.deleteProvider(providerId)
    }

    suspend fun saveMaterial(material: Material): SaveMaterialResult {
        val clean = material.copy(
            name = material.name.trim(),
            customUnit = material.customUnit.trim(),
            stock = material.stock.coerceAtLeast(0),
            lowStockThreshold = material.lowStockThreshold.coerceAtLeast(0),
            notes = material.notes.trim(),
        )
        if (clean.name.isEmpty()) return SaveMaterialResult.EmptyName
        if (clean.unit == MaterialUnit.OTHER && clean.customUnit.isEmpty()) {
            return SaveMaterialResult.CustomUnitRequired
        }

        val clash = dao.findMaterialByName(clean.name)
        if (clash != null && clash.id != clean.id) {
            return SaveMaterialResult.DuplicateName(clean.name)
        }

        // A unit only carries a custom label when it is OTHER; keeping a stale "sack" around
        // after switching back to BOX would make a later edit confusing.
        val normalised = clean.copy(
            customUnit = if (clean.unit == MaterialUnit.OTHER) clean.customUnit else "",
        )

        return if (normalised.id == 0L) {
            val id = dao.insertMaterial(normalised)
            SaveMaterialResult.Success(normalised.copy(id = id))
        } else {
            if (dao.findMaterial(normalised.id) == null) return SaveMaterialResult.NotFound
            dao.updateMaterial(normalised)
            SaveMaterialResult.Success(normalised)
        }
    }

    suspend fun deleteMaterial(materialId: Long) {
        dao.deleteMaterial(materialId)
    }

    /** Sets a stock correction directly; purchases must go through [recordEntry]. */
    suspend fun adjustStock(materialId: Long, newStock: Int): Material? {
        val material = dao.findMaterial(materialId) ?: return null
        val updated = material.copy(stock = newStock.coerceAtLeast(0))
        dao.updateMaterial(updated)
        return updated
    }

    suspend fun recordEntry(
        providerId: Long,
        purchasedAt: Long,
        reference: String = "",
        note: String = "",
        lines: List<NewMaterialLine>,
    ): RecordEntryResult = dao.recordMaterialEntry(
        entryId = nextEntryId(),
        providerId = providerId,
        purchasedAt = purchasedAt,
        reference = reference,
        note = note,
        lines = lines,
    )

    suspend fun deleteEntry(entryId: String): DeleteEntryResult =
        dao.deleteMaterialEntry(entryId)

    private fun nextEntryId(): String {
        val stamp = now().toString(36)
        return "MAT-$stamp-${entryCounter.incrementAndGet()}"
    }

    private companion object {
        /**
         * Keeps ids unique when two entries are saved in the same millisecond. The counter is
         * process-local, like the order counter in [StockRepository]; the timestamp makes
         * collisions across restarts effectively impossible.
         */
        val entryCounter = AtomicLong(0L)
    }
}

sealed interface SaveProviderResult {
    data class Success(val provider: Provider) : SaveProviderResult
    data object EmptyName : SaveProviderResult
    data class DuplicateName(val name: String) : SaveProviderResult
    data object NotFound : SaveProviderResult
}

sealed interface SaveMaterialResult {
    data class Success(val material: Material) : SaveMaterialResult
    data object EmptyName : SaveMaterialResult
    data class DuplicateName(val name: String) : SaveMaterialResult
    data object CustomUnitRequired : SaveMaterialResult
    data object NotFound : SaveMaterialResult
}
