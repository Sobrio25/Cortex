package com.aiagents.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.aiagents.app.data.model.FinanceTransactionEntity

/**
 * Isolated SQLite database for financial data.
 *
 * Keeping this separate from [AppDatabase] prevents financial records from
 * sharing storage with conversations, agent memory, files, and app settings.
 */
@Database(
    entities = [FinanceTransactionEntity::class],
    version = 1,
    exportSchema = true
)
abstract class FinanceDatabase : RoomDatabase() {
    abstract fun financeDao(): FinanceDao

    companion object {
        const val DATABASE_NAME = "finance_data.db"
    }
}
