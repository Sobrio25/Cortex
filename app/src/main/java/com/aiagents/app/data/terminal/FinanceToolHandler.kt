package com.aiagents.app.data.terminal

import android.content.Context
import android.os.Environment
import android.util.Log
import com.aiagents.app.data.local.FinanceDao
import com.aiagents.app.data.model.FinanceTransactionEntity
import com.google.gson.JsonParser
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class FinanceToolResult(
    val toolCallId: String,
    val success: Boolean,
    val content: String
)

@Singleton
class FinanceToolHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val financeDao: FinanceDao
) {
    companion object {
        private const val TAG = "FinanceToolHandler"
        const val TOOL_ADD_TRANSACTION = "finance_add_transaction"
        const val TOOL_LIST_TRANSACTIONS = "finance_list_transactions"
        const val TOOL_GET_SUMMARY = "finance_get_summary"
        const val TOOL_SEARCH = "finance_search_transactions"
        const val TOOL_DELETE = "finance_delete_transaction"
        const val TOOL_GET_BALANCE = "finance_get_balance"
        const val TOOL_EXPORT_CSV = "finance_export_csv"

        val ALL_TOOL_NAMES = setOf(
            TOOL_ADD_TRANSACTION, TOOL_LIST_TRANSACTIONS, TOOL_GET_SUMMARY,
            TOOL_SEARCH, TOOL_DELETE, TOOL_GET_BALANCE, TOOL_EXPORT_CSV
        )

        private fun param(type: String, desc: String) = mapOf("type" to type, "description" to desc)

        private fun toolDef(name: String, desc: String, props: Map<String, Map<String, String>>, required: List<String>) = mapOf(
            "type" to "function",
            "function" to mapOf(
                "name" to name,
                "description" to desc,
                "parameters" to mapOf(
                    "type" to "object",
                    "properties" to props,
                    "required" to required
                )
            )
        )

        fun getToolDefinitionsJson(): List<Map<String, Any>> = listOf(
            toolDef(TOOL_ADD_TRANSACTION,
                "Registra una transaccion financiera (gasto, ingreso o inversion). Siempre confirma con el usuario antes de registrar. IMPORTANTE: currency es obligatorio. Si no sabes la moneda del usuario, busca en memoria (memory_search) o preguntale directamente antes de registrar.",
                mapOf(
                    "type" to param("string", "Tipo: expense, income, investment"),
                    "amount" to param("number", "Monto positivo de la transaccion"),
                    "category" to param("string", "Categoria. Ej: food, salary, rent, transport, stocks, freelance, entertainment, health, education, savings"),
                    "currency" to param("string", "Moneda ISO 4217. Ej: USD, MXN, EUR, COP, ARS. OBLIGATORIO: pregunta al usuario o busca en memoria si no lo sabes"),
                    "description" to param("string", "Descripcion o nota (opcional)"),
                    "date" to param("string", "Fecha yyyy-MM-dd (default: hoy)")
                ),
                listOf("type", "amount", "category", "currency")
            ),
            toolDef(TOOL_LIST_TRANSACTIONS,
                "Lista transacciones financieras recientes. Puede filtrar por tipo o categoria.",
                mapOf(
                    "limit" to param("integer", "Cantidad maxima de resultados (default: 20)"),
                    "type" to param("string", "Filtrar por tipo: expense, income, investment"),
                    "category" to param("string", "Filtrar por categoria")
                ),
                listOf()
            ),
            toolDef(TOOL_GET_SUMMARY,
                "Obtiene un resumen financiero con totales por tipo para un periodo. Muestra ingresos, gastos, inversiones y balance neto.",
                mapOf(
                    "period" to param("string", "Periodo: today, week, month, year, all (default: month)"),
                    "from" to param("string", "Fecha inicio yyyy-MM-dd (si se usa rango personalizado)"),
                    "to" to param("string", "Fecha fin yyyy-MM-dd (si se usa rango personalizado)")
                ),
                listOf()
            ),
            toolDef(TOOL_SEARCH,
                "Busca transacciones por palabra clave en categoria o descripcion.",
                mapOf(
                    "query" to param("string", "Texto a buscar"),
                    "type" to param("string", "Filtrar por tipo: expense, income, investment (opcional)"),
                    "limit" to param("integer", "Cantidad maxima (default: 20)")
                ),
                listOf("query")
            ),
            toolDef(TOOL_DELETE,
                "Elimina una transaccion por su ID.",
                mapOf(
                    "transaction_id" to param("integer", "ID de la transaccion a eliminar")
                ),
                listOf("transaction_id")
            ),
            toolDef(TOOL_GET_BALANCE,
                "Calcula el balance neto (ingresos - gastos) para un periodo.",
                mapOf(
                    "period" to param("string", "Periodo: today, week, month, year, all (default: month)"),
                    "from" to param("string", "Fecha inicio yyyy-MM-dd (rango personalizado)"),
                    "to" to param("string", "Fecha fin yyyy-MM-dd (rango personalizado)")
                ),
                listOf()
            ),
            toolDef(TOOL_EXPORT_CSV,
                "Exporta transacciones financieras a un archivo CSV en Documents/AIAgents/Finance. Devuelve la ruta del archivo creado.",
                mapOf(
                    "period" to param("string", "Periodo: today, week, month, year, all (default: all)"),
                    "from" to param("string", "Fecha inicio yyyy-MM-dd (rango personalizado)"),
                    "to" to param("string", "Fecha fin yyyy-MM-dd (rango personalizado)"),
                    "type" to param("string", "Filtrar por tipo: expense, income, investment (opcional)")
                ),
                listOf()
            )
        )
    }

    suspend fun executeTool(toolCallId: String, toolName: String, arguments: String): FinanceToolResult {
        return try {
            val args = JsonParser.parseString(arguments).asJsonObject
            when (toolName) {
                TOOL_ADD_TRANSACTION -> addTransaction(toolCallId, args)
                TOOL_LIST_TRANSACTIONS -> listTransactions(toolCallId, args)
                TOOL_GET_SUMMARY -> getSummary(toolCallId, args)
                TOOL_SEARCH -> searchTransactions(toolCallId, args)
                TOOL_DELETE -> deleteTransaction(toolCallId, args)
                TOOL_GET_BALANCE -> getBalance(toolCallId, args)
                TOOL_EXPORT_CSV -> exportCsv(toolCallId, args)
                else -> FinanceToolResult(toolCallId, false, "Tool desconocido: $toolName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing $toolName", e)
            FinanceToolResult(toolCallId, false, "Error: ${e.message}")
        }
    }

    private suspend fun addTransaction(toolCallId: String, args: com.google.gson.JsonObject): FinanceToolResult {
        val type = args.get("type")?.asString ?: return FinanceToolResult(toolCallId, false, "Error: 'type' es requerido (expense, income, investment)")
        if (type !in listOf("expense", "income", "investment")) {
            return FinanceToolResult(toolCallId, false, "Error: type debe ser 'expense', 'income' o 'investment'")
        }
        val amount = args.get("amount")?.asDouble ?: return FinanceToolResult(toolCallId, false, "Error: 'amount' es requerido")
        val category = args.get("category")?.asString ?: return FinanceToolResult(toolCallId, false, "Error: 'category' es requerido")
        val currency = args.get("currency")?.asString ?: return FinanceToolResult(toolCallId, false, "Error: 'currency' es requerido. Pregunta al usuario su moneda o busca en memoria con memory_search.")
        val description = args.get("description")?.asString ?: ""
        val dateStr = args.get("date")?.asString
        val date = if (dateStr != null) parseDate(dateStr) else todayStart()

        val entity = FinanceTransactionEntity(
            type = type,
            amount = amount,
            currency = currency,
            category = category,
            description = description,
            date = date
        )
        val id = financeDao.insert(entity)
        val typeLabel = when (type) { "expense" -> "Gasto"; "income" -> "Ingreso"; else -> "Inversion" }
        return FinanceToolResult(toolCallId, true,
            "$typeLabel registrado: $currency ${"%.2f".format(amount)} en '$category'" +
            (if (description.isNotBlank()) " — $description" else "") +
            " (ID: $id, Fecha: ${formatDate(date)})"
        )
    }

    private suspend fun listTransactions(toolCallId: String, args: com.google.gson.JsonObject): FinanceToolResult {
        val limit = args.get("limit")?.asInt ?: 20
        val type = args.get("type")?.asString
        val category = args.get("category")?.asString

        val transactions = when {
            category != null -> financeDao.getByCategory(category, limit)
            type != null -> financeDao.getByType(type, limit)
            else -> financeDao.getRecent(limit)
        }

        if (transactions.isEmpty()) {
            return FinanceToolResult(toolCallId, true, "No se encontraron transacciones.")
        }

        val sb = StringBuilder("**Transacciones** (${transactions.size}):\n\n")
        transactions.forEach { tx ->
            val icon = when (tx.type) { "income" -> "+" ; "expense" -> "-"; else -> "~" }
            sb.appendLine("- **#${tx.id}** [$icon${tx.currency} ${"%.2f".format(tx.amount)}] ${tx.category}" +
                (if (tx.description.isNotBlank()) " — ${tx.description}" else "") +
                " (${formatDate(tx.date)})")
        }
        return FinanceToolResult(toolCallId, true, sb.toString().trim())
    }

    private suspend fun getSummary(toolCallId: String, args: com.google.gson.JsonObject): FinanceToolResult {
        val (from, to) = parsePeriod(args)

        val income = financeDao.getSumByType("income", from, to) ?: 0.0
        val expenses = financeDao.getSumByType("expense", from, to) ?: 0.0
        val investments = financeDao.getSumByType("investment", from, to) ?: 0.0
        val balance = income - expenses - investments

        val period = args.get("period")?.asString ?: "month"
        val periodLabel = when (period) {
            "today" -> "Hoy"; "week" -> "Esta semana"; "month" -> "Este mes"
            "year" -> "Este año"; "all" -> "Todo el historial"
            else -> "${formatDate(from)} — ${formatDate(to)}"
        }

        return FinanceToolResult(toolCallId, true, buildString {
            appendLine("**Resumen Financiero** ($periodLabel)")
            appendLine()
            appendLine("| Concepto | Monto |")
            appendLine("|----------|-------|")
            appendLine("| Ingresos | +${"%.2f".format(income)} |")
            appendLine("| Gastos | -${"%.2f".format(expenses)} |")
            appendLine("| Inversiones | ~${"%.2f".format(investments)} |")
            appendLine("| **Balance neto** | **${"%.2f".format(balance)}** |")
        }.trim())
    }

    private suspend fun searchTransactions(toolCallId: String, args: com.google.gson.JsonObject): FinanceToolResult {
        val query = args.get("query")?.asString ?: return FinanceToolResult(toolCallId, false, "Error: 'query' es requerido")
        val type = args.get("type")?.asString
        val limit = args.get("limit")?.asInt ?: 20

        val results = if (type != null) {
            financeDao.searchByType(query, type, limit)
        } else {
            financeDao.search(query, limit)
        }

        if (results.isEmpty()) {
            return FinanceToolResult(toolCallId, true, "No se encontraron transacciones para '$query'.")
        }

        val sb = StringBuilder("**Resultados para '$query'** (${results.size}):\n\n")
        results.forEach { tx ->
            val icon = when (tx.type) { "income" -> "+"; "expense" -> "-"; else -> "~" }
            sb.appendLine("- **#${tx.id}** [$icon${tx.currency} ${"%.2f".format(tx.amount)}] ${tx.category}" +
                (if (tx.description.isNotBlank()) " — ${tx.description}" else "") +
                " (${formatDate(tx.date)})")
        }
        return FinanceToolResult(toolCallId, true, sb.toString().trim())
    }

    private suspend fun deleteTransaction(toolCallId: String, args: com.google.gson.JsonObject): FinanceToolResult {
        val id = args.get("transaction_id")?.asLong ?: return FinanceToolResult(toolCallId, false, "Error: 'transaction_id' es requerido")
        val existing = financeDao.getById(id)
        if (existing == null) {
            return FinanceToolResult(toolCallId, false, "Error: No se encontro transaccion con ID $id")
        }
        financeDao.deleteById(id)
        return FinanceToolResult(toolCallId, true,
            "Transaccion eliminada: #$id [${existing.type}] ${existing.currency} ${"%.2f".format(existing.amount)} — ${existing.category}")
    }

    private suspend fun getBalance(toolCallId: String, args: com.google.gson.JsonObject): FinanceToolResult {
        val (from, to) = parsePeriod(args)

        val income = financeDao.getSumByType("income", from, to) ?: 0.0
        val expenses = financeDao.getSumByType("expense", from, to) ?: 0.0
        val balance = income - expenses

        val period = args.get("period")?.asString ?: "month"
        val periodLabel = when (period) {
            "today" -> "hoy"; "week" -> "esta semana"; "month" -> "este mes"
            "year" -> "este año"; "all" -> "todo el historial"
            else -> "${formatDate(from)} — ${formatDate(to)}"
        }

        val sign = if (balance >= 0) "+" else ""
        return FinanceToolResult(toolCallId, true,
            "**Balance $periodLabel:** $sign${"%.2f".format(balance)} (Ingresos: ${"%.2f".format(income)}, Gastos: ${"%.2f".format(expenses)})")
    }

    private suspend fun exportCsv(toolCallId: String, args: com.google.gson.JsonObject): FinanceToolResult {
        val periodArgs = args.deepCopy()
        if (!periodArgs.has("period")) periodArgs.addProperty("period", "all")
        val (from, to) = parsePeriod(periodArgs)
        val type = args.get("type")?.asString

        val transactions = if (type != null) {
            financeDao.getByDateRange(from, to).filter { it.type == type }
        } else {
            financeDao.getByDateRange(from, to)
        }

        if (transactions.isEmpty()) {
            return FinanceToolResult(toolCallId, true, "No hay transacciones para exportar en el periodo seleccionado.")
        }

        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            "AIAgents/Finance"
        )
        dir.mkdirs()

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
        val file = File(dir, "finanzas_$timestamp.csv")

        val sb = StringBuilder()
        sb.appendLine("id,type,amount,currency,category,description,date")
        transactions.forEach { tx ->
            val desc = tx.description.replace("\"", "\"\"")
            val cat = tx.category.replace("\"", "\"\"")
            sb.appendLine("${tx.id},${tx.type},${tx.amount},${tx.currency},\"$cat\",\"$desc\",${formatDate(tx.date)}")
        }

        file.writeText(sb.toString())

        return FinanceToolResult(toolCallId, true,
            "CSV exportado: ${file.absolutePath} (${transactions.size} transacciones)")
    }

    // --- Helpers ---

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private fun parseDate(dateStr: String): Long {
        return try {
            dateFormat.parse(dateStr)?.time ?: todayStart()
        } catch (e: Exception) {
            todayStart()
        }
    }

    private fun formatDate(millis: Long): String = dateFormat.format(millis)

    private fun todayStart(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun parsePeriod(args: com.google.gson.JsonObject): Pair<Long, Long> {
        val fromStr = args.get("from")?.asString
        val toStr = args.get("to")?.asString
        if (fromStr != null && toStr != null) {
            return parseDate(fromStr) to parseDate(toStr) + 86400000L // end of day
        }

        val period = args.get("period")?.asString ?: "month"
        val cal = Calendar.getInstance()
        val to = cal.timeInMillis

        when (period) {
            "today" -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
            }
            "week" -> cal.add(Calendar.DAY_OF_YEAR, -7)
            "month" -> cal.add(Calendar.MONTH, -1)
            "year" -> cal.add(Calendar.YEAR, -1)
            "all" -> cal.timeInMillis = 0L
        }
        return cal.timeInMillis to to
    }
}
