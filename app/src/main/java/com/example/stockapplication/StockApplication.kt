package com.example.stockapplication

import android.app.Application
import com.example.stockapplication.data.SettingsRepository
import com.example.stockapplication.data.StockDatabase
import com.example.stockapplication.data.StockRepository
import com.example.stockapplication.data.SuppliesRepository

/**
 * Holds the singletons for the app. A hand-rolled container is enough here; swap it for Hilt
 * once the graph grows.
 */
class StockApplication : Application() {

    val repository: StockRepository by lazy {
        StockRepository(StockDatabase.get(this).stockDao())
    }

    /**
     * Supplies share [StockDao] with inventory: an entry and the stock it adds must commit in one
     * Room transaction, and the JSON backup/restore path must see both tables at one instant.
     */
    val suppliesRepository: SuppliesRepository by lazy {
        SuppliesRepository(StockDatabase.get(this).stockDao())
    }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }
}
