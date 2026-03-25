package com.aiagents.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.aiagents.app.data.model.FinanceTransactionEntity

@Dao
interface FinanceDao {

    @Insert
    suspend fun insert(transaction: FinanceTransactionEntity): Long

    @Query("DELETE FROM finance_transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM finance_transactions WHERE id = :id")
    suspend fun getById(id: Long): FinanceTransactionEntity?

    @Query("SELECT * FROM finance_transactions WHERE date BETWEEN :from AND :to ORDER BY date DESC")
    suspend fun getByDateRange(from: Long, to: Long): List<FinanceTransactionEntity>

    @Query("SELECT * FROM finance_transactions WHERE type = :type ORDER BY date DESC LIMIT :limit")
    suspend fun getByType(type: String, limit: Int = 50): List<FinanceTransactionEntity>

    @Query("SELECT * FROM finance_transactions WHERE category = :category ORDER BY date DESC LIMIT :limit")
    suspend fun getByCategory(category: String, limit: Int = 50): List<FinanceTransactionEntity>

    @Query("SELECT * FROM finance_transactions ORDER BY date DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 20): List<FinanceTransactionEntity>

    @Query("SELECT SUM(amount) FROM finance_transactions WHERE type = :type AND date BETWEEN :from AND :to")
    suspend fun getSumByType(type: String, from: Long, to: Long): Double?

    @Query("SELECT * FROM finance_transactions WHERE (category LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%') ORDER BY date DESC LIMIT :limit")
    suspend fun search(query: String, limit: Int = 20): List<FinanceTransactionEntity>

    @Query("SELECT * FROM finance_transactions WHERE (category LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%') AND type = :type ORDER BY date DESC LIMIT :limit")
    suspend fun searchByType(query: String, type: String, limit: Int = 20): List<FinanceTransactionEntity>
}
