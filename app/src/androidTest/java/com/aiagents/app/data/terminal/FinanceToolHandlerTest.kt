package com.aiagents.app.data.terminal

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aiagents.app.data.local.FinanceDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FinanceToolHandlerTest {
    private lateinit var database: FinanceDatabase
    private lateinit var handler: FinanceToolHandler

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, FinanceDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        handler = FinanceToolHandler(context, database.financeDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun addAndUpdateTransaction_changesAmountDescriptionAndCategory() = runBlocking {
        val addResult = handler.executeTool(
            toolCallId = "add",
            toolName = FinanceToolHandler.TOOL_ADD_TRANSACTION,
            arguments = """
                {
                  "type": "gasto",
                  "amount": 46.0,
                  "currency": "mxn",
                  "category": "Comida",
                  "description": "Tres tacos",
                  "date": "2026-07-13"
                }
            """.trimIndent()
        )

        assertTrue(addResult.content, addResult.success)
        val original = database.financeDao().getById(1L)!!
        assertEquals("expense", original.type)
        assertEquals("MXN", original.currency)

        val updateResult = handler.executeTool(
            toolCallId = "update",
            toolName = FinanceToolHandler.TOOL_UPDATE_TRANSACTION,
            arguments = """
                {
                  "transaction_id": 1,
                  "amount": 52.5,
                  "category": "Alimentacion",
                  "description": "Tres tacos y propina"
                }
            """.trimIndent()
        )

        assertTrue(updateResult.content, updateResult.success)
        val updated = database.financeDao().getById(1L)!!
        assertEquals(52.5, updated.amount, 0.001)
        assertEquals("Alimentacion", updated.category)
        assertEquals("Tres tacos y propina", updated.description)
        assertTrue(updated.updatedAt >= original.updatedAt)
    }

    @Test
    fun summary_separatesCurrenciesAndIncludesCategories() = runBlocking {
        add(type = "ingreso", amount = 1000.0, currency = "MXN", category = "Salario")
        add(type = "gasto", amount = 100.0, currency = "MXN", category = "Transporte")
        add(type = "expense", amount = 25.0, currency = "USD", category = "Software")

        val summary = handler.executeTool(
            toolCallId = "summary",
            toolName = FinanceToolHandler.TOOL_GET_SUMMARY,
            arguments = """{"period":"all"}"""
        )

        assertTrue(summary.content, summary.success)
        assertTrue(summary.content.contains("**MXN**"))
        assertTrue(summary.content.contains("**USD**"))
        assertTrue(summary.content.contains("Salario"))
        assertTrue(summary.content.contains("Transporte"))
        assertTrue(summary.content.contains("Software"))
    }

    private suspend fun add(
        type: String,
        amount: Double,
        currency: String,
        category: String
    ) {
        val result = handler.executeTool(
            toolCallId = "add-$category",
            toolName = FinanceToolHandler.TOOL_ADD_TRANSACTION,
            arguments = """
                {
                  "type": "$type",
                  "amount": $amount,
                  "currency": "$currency",
                  "category": "$category"
                }
            """.trimIndent()
        )
        assertTrue(result.content, result.success)
    }
}
