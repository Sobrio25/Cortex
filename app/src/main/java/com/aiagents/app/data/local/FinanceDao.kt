package com.aiagents.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.aiagents.app.data.model.FinanceTransactionEntity

data class FinanceTypeTotal(
    val type: String,
    val currency: String,
    val total: Double
)

data class FinanceCategoryTotal(
    val type: String,
    val currency: String,
    val category: String,
    val total: Double
)

@Dao
interface FinanceDao {

    @Insert
    suspend fun insert(transaction: FinanceTransactionEntity): Long

    @Update
    suspend fun update(transaction: FinanceTransactionEntity): Int

    @Query("DELETE FROM finance_transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM finance_transactions WHERE id = :id")
    suspend fun getById(id: Long): FinanceTransactionEntity?

    @Query("SELECT * FROM finance_transactions WHERE date >= :from AND date < :to ORDER BY date DESC, id DESC")
    suspend fun getByDateRange(from: Long, to: Long): List<FinanceTransactionEntity>

    @Query("SELECT * FROM finance_transactions WHERE type = :type ORDER BY date DESC, id DESC LIMIT :limit")
    suspend fun getByType(type: String, limit: Int = 50): List<FinanceTransactionEntity>

    @Query("SELECT * FROM finance_transactions WHERE category = :category COLLATE NOCASE ORDER BY date DESC, id DESC LIMIT :limit")
    suspend fun getByCategory(category: String, limit: Int = 50): List<FinanceTransactionEntity>

    @Query("SELECT * FROM finance_transactions WHERE type = :type AND category = :category COLLATE NOCASE ORDER BY date DESC, id DESC LIMIT :limit")
    suspend fun getByTypeAndCategory(type: String, category: String, limit: Int = 50): List<FinanceTransactionEntity>

    @Query("SELECT * FROM finance_transactions ORDER BY date DESC, id DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 20): List<FinanceTransactionEntity>

    @Query("SELECT SUM(amount) FROM finance_transactions WHERE type = :type AND date >= :from AND date < :to")
    suspend fun getSumByType(type: String, from: Long, to: Long): Double?

    @Query("SELECT type, currency, SUM(amount) AS total FROM finance_transactions WHERE date >= :from AND date < :to GROUP BY type, currency ORDER BY currency, type")
    suspend fun getTotalsByTypeAndCurrency(from: Long, to: Long): List<FinanceTypeTotal>

    @Query("SELECT type, currency, category, SUM(amount) AS total FROM finance_transactions WHERE date >= :from AND date < :to GROUP BY type, currency, category ORDER BY currency, type, total DESC")
    suspend fun getTotalsByCategory(from: Long, to: Long): List<FinanceCategoryTotal>

    @Query("SELECT * FROM finance_transactions WHERE (category LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%') ORDER BY date DESC, id DESC LIMIT :limit")
    suspend fun search(query: String, limit: Int = 20): List<FinanceTransactionEntity>

    @Query("SELECT * FROM finance_transactions WHERE (category LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%') AND type = :type ORDER BY date DESC, id DESC LIMIT :limit")
    suspend fun searchByType(query: String, type: String, limit: Int = 20): List<FinanceTransactionEntity>
}
