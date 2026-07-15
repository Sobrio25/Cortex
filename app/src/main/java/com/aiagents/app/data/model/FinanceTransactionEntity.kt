package com.aiagents.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "finance_transactions")
data class FinanceTransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: String,          // "expense", "income", "investment"
    val amount: Double,
    val currency: String,
    val category: String,
    val description: String = "",
    val date: Long,            // Epoch millis of transaction date
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt
)
