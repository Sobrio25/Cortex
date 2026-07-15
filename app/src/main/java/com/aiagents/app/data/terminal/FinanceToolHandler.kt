package com.aiagents.app.data.terminal

import android.content.Context
import android.os.Environment
import android.util.Log
import com.aiagents.app.data.local.FinanceDao
import com.aiagents.app.data.local.FinanceTypeTotal
import com.aiagents.app.data.model.FinanceTransactionEntity
import com.google.gson.JsonParser
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
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
        const val TOOL_UPDATE_TRANSACTION = "finance_update_transaction"
        const val TOOL_LIST_TRANSACTIONS = "finance_list_transactions"
        const val TOOL_GET_SUMMARY = "finance_get_summary"
        const val TOOL_SEARCH = "finance_search_transactions"
        const val TOOL_DELETE = "finance_delete_transaction"
        const val TOOL_GET_BALANCE = "finance_get_balance"
        const val TOOL_EXPORT_CSV = "finance_export_csv"

        val ALL_TOOL_NAMES = setOf(
            TOOL_ADD_TRANSACTION, TOOL_UPDATE_TRANSACTION, TOOL_LIST_TRANSACTIONS, TOOL_GET_SUMMARY,
            TOOL_SEARCH, TOOL_DELETE, TOOL_GET_BALANCE, TOOL_EXPORT_CSV
        )

        private val EDITABLE_FIELDS = setOf(
            "type", "amount", "currency", "category", "description", "date"
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
                "Registra una transaccion financiera aislada de chats y memoria. Clasificala como gasto, ingreso o inversion y siempre confirma los datos con el usuario antes de guardar. currency es obligatorio.",
                mapOf(
                    "type" to param("string", "Tipo: expense, income, investment"),
                    "amount" to param("number", "Monto positivo de la transaccion"),
                    "category" to param("string", "Categoria libre dentro del tipo. Ej: alimentacion, salario, renta, transporte, acciones, freelance, entretenimiento, salud, educacion, ahorro"),
                    "currency" to param("string", "Moneda ISO 4217. Ej: USD, MXN, EUR, COP, ARS. OBLIGATORIO: pregunta al usuario o busca en memoria si no lo sabes"),
                    "description" to param("string", "Descripcion o nota (opcional)"),
                    "date" to param("string", "Fecha yyyy-MM-dd (default: hoy)")
                ),
                listOf("type", "amount", "category", "currency")
            ),
            toolDef(TOOL_UPDATE_TRANSACTION,
                "Edita una transaccion financiera existente por ID. Permite cambiar monto, descripcion, tipo, categoria, moneda o fecha. Confirma con el usuario antes de modificar.",
                mapOf(
                    "transaction_id" to param("integer", "ID de la transaccion a editar"),
                    "type" to param("string", "Nuevo tipo: expense, income, investment (opcional)"),
                    "amount" to param("number", "Nuevo monto positivo (opcional)"),
                    "category" to param("string", "Nueva categoria (opcional)"),
                    "currency" to param("string", "Nueva moneda ISO 4217 (opcional)"),
                    "description" to param("string", "Nueva descripcion; puede ser vacia (opcional)"),
                    "date" to param("string", "Nueva fecha yyyy-MM-dd (opcional)")
                ),
                listOf("transaction_id")
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
                "Obtiene un resumen por moneda, tipo y categoria para un periodo. Muestra ingresos, gastos, inversiones y balance neto sin mezclar monedas.",
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
                "Elimina una transaccion por su ID. Confirma con el usuario antes de eliminar.",
                mapOf(
                    "transaction_id" to param("integer", "ID de la transaccion a eliminar")
                ),
                listOf("transaction_id")
            ),
            toolDef(TOOL_GET_BALANCE,
                "Calcula por moneda el balance neto (ingresos - gastos - inversiones) para un periodo.",
                mapOf(
                    "period" to param("string", "Periodo: today, week, month, year, all (default: month)"),
                    "from" to param("string", "Fecha inicio yyyy-MM-dd (rango personalizado)"),
                    "to" to param("string", "Fecha fin yyyy-MM-dd (rango personalizado)")
                ),
                listOf()
            ),
            toolDef(TOOL_EXPORT_CSV,
                "Exporta transacciones financieras a un CSV dentro del almacenamiento privado de la app. Devuelve la ruta del archivo creado.",
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
                TOOL_UPDATE_TRANSACTION -> updateTransaction(toolCallId, args)
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
        val typeValue = args.stringOrNull("type")
            ?: return FinanceToolResult(toolCallId, false, "Error: 'type' es requerido (expense, income, investment)")
        val type = normalizeType(typeValue)
            ?: return FinanceToolResult(toolCallId, false, "Error: type debe ser expense/gasto, income/ingreso o investment/inversion")
        val amount = args.get("amount")?.asDouble ?: return FinanceToolResult(toolCallId, false, "Error: 'amount' es requerido")
        if (!amount.isFinite() || amount <= 0.0) {
            return FinanceToolResult(toolCallId, false, "Error: 'amount' debe ser un numero positivo")
        }
        val category = args.stringOrNull("category")?.trim().orEmpty()
        if (category.isBlank()) {
            return FinanceToolResult(toolCallId, false, "Error: 'category' es requerido y no puede estar vacio")
        }
        val currency = normalizeCurrency(args.stringOrNull("currency"))
            ?: return FinanceToolResult(toolCallId, false, "Error: 'currency' debe ser una moneda ISO 4217 de 3 letras, por ejemplo MXN, USD o EUR")
        val description = args.stringOrNull("description")?.trim().orEmpty()
        val dateStr = args.stringOrNull("date")
        val date = if (dateStr != null) {
            parseDateOrNull(dateStr)
                ?: return FinanceToolResult(toolCallId, false, "Error: 'date' debe usar el formato yyyy-MM-dd")
        } else {
            todayStart()
        }

        val now = System.currentTimeMillis()
        val entity = FinanceTransactionEntity(
            type = type,
            amount = amount,
            currency = currency,
            category = category,
            description = description,
            date = date,
            createdAt = now,
            updatedAt = now
        )
        val id = financeDao.insert(entity)
        return FinanceToolResult(toolCallId, true,
            "${typeLabel(type)} registrado: $currency ${formatAmount(amount)} en '$category'" +
            (if (description.isNotBlank()) " — $description" else "") +
            " (ID: $id, Fecha: ${formatDate(date)})"
        )
    }

    private suspend fun updateTransaction(
        toolCallId: String,
        args: com.google.gson.JsonObject
    ): FinanceToolResult {
        val id = args.get("transaction_id")?.asLong
            ?: return FinanceToolResult(toolCallId, false, "Error: 'transaction_id' es requerido")
        val existing = financeDao.getById(id)
            ?: return FinanceToolResult(toolCallId, false, "Error: No se encontro transaccion con ID $id")

        val requestedFields = EDITABLE_FIELDS.filter(args::has)
        if (requestedFields.isEmpty()) {
            return FinanceToolResult(
                toolCallId,
                false,
                "Error: indica al menos un campo para editar: amount, description, type, category, currency o date"
            )
        }

        var updated = existing
        if (args.has("type")) {
            val type = normalizeType(args.stringOrNull("type"))
                ?: return FinanceToolResult(toolCallId, false, "Error: type debe ser expense/gasto, income/ingreso o investment/inversion")
            updated = updated.copy(type = type)
        }
        if (args.has("amount")) {
            val amount = args.get("amount")?.takeUnless { it.isJsonNull }?.asDouble
                ?: return FinanceToolResult(toolCallId, false, "Error: 'amount' no puede ser nulo")
            if (!amount.isFinite() || amount <= 0.0) {
                return FinanceToolResult(toolCallId, false, "Error: 'amount' debe ser un numero positivo")
            }
            updated = updated.copy(amount = amount)
        }
        if (args.has("currency")) {
            val currency = normalizeCurrency(args.stringOrNull("currency"))
                ?: return FinanceToolResult(toolCallId, false, "Error: 'currency' debe ser una moneda ISO 4217 de 3 letras")
            updated = updated.copy(currency = currency)
        }
        if (args.has("category")) {
            val category = args.stringOrNull("category")?.trim().orEmpty()
            if (category.isBlank()) {
                return FinanceToolResult(toolCallId, false, "Error: 'category' no puede estar vacia")
            }
            updated = updated.copy(category = category)
        }
        if (args.has("description")) {
            val description = args.stringOrNull("description")?.trim().orEmpty()
            updated = updated.copy(description = description)
        }
        if (args.has("date")) {
            val date = args.stringOrNull("date")?.let(::parseDateOrNull)
                ?: return FinanceToolResult(toolCallId, false, "Error: 'date' debe usar el formato yyyy-MM-dd")
            updated = updated.copy(date = date)
        }

        updated = updated.copy(updatedAt = System.currentTimeMillis())
        if (financeDao.update(updated) == 0) {
            return FinanceToolResult(toolCallId, false, "Error: no se pudo actualizar la transaccion #$id")
        }

        return FinanceToolResult(
            toolCallId,
            true,
            "Transaccion #$id actualizada: ${typeLabel(updated.type)}, " +
                "${updated.currency} ${formatAmount(updated.amount)}, categoria '${updated.category}', " +
                "descripcion '${updated.description.ifBlank { "Sin descripcion" }}', fecha ${formatDate(updated.date)}"
        )
    }

    private suspend fun listTransactions(toolCallId: String, args: com.google.gson.JsonObject): FinanceToolResult {
        val limit = (args.get("limit")?.asInt ?: 20).coerceIn(1, 100)
        val type = args.stringOrNull("type")?.let { raw ->
            normalizeType(raw)
                ?: return FinanceToolResult(toolCallId, false, "Error: filtro 'type' invalido")
        }
        val category = args.stringOrNull("category")?.trim()?.takeIf(String::isNotBlank)

        val transactions = when {
            category != null && type != null -> financeDao.getByTypeAndCategory(type, category, limit)
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
            sb.appendLine("- **#${tx.id}** [$icon${tx.currency} ${formatAmount(tx.amount)}] ${tx.category}" +
                (if (tx.description.isNotBlank()) " — ${tx.description}" else "") +
                " (${formatDate(tx.date)})")
        }
        return FinanceToolResult(toolCallId, true, sb.toString().trim())
    }

    private suspend fun getSummary(toolCallId: String, args: com.google.gson.JsonObject): FinanceToolResult {
        val (from, to) = parsePeriod(args)
        val totals = financeDao.getTotalsByTypeAndCurrency(from, to)
        if (totals.isEmpty()) {
            return FinanceToolResult(toolCallId, true, "No hay transacciones en el periodo seleccionado.")
        }
        val categoryTotals = financeDao.getTotalsByCategory(from, to)

        val periodLabel = periodLabel(args, from, to, capitalize = true)

        return FinanceToolResult(toolCallId, true, buildString {
            appendLine("**Resumen Financiero** ($periodLabel)")
            totals.groupBy { it.currency }.forEach { (currency, currencyTotals) ->
                val income = currencyTotals.totalFor("income")
                val expenses = currencyTotals.totalFor("expense")
                val investments = currencyTotals.totalFor("investment")
                val balance = income - expenses - investments
                appendLine()
                appendLine("**$currency**")
                appendLine("| Concepto | Monto |")
                appendLine("|----------|-------|")
                appendLine("| Ingresos | +${formatAmount(income)} |")
                appendLine("| Gastos | -${formatAmount(expenses)} |")
                appendLine("| Inversiones | ~${formatAmount(investments)} |")
                appendLine("| **Balance neto** | **${formatSignedAmount(balance)}** |")

                val categoriesForCurrency = categoryTotals.filter { it.currency == currency }
                if (categoriesForCurrency.isNotEmpty()) {
                    appendLine()
                    appendLine("Categorias:")
                    categoriesForCurrency.groupBy { it.type }.forEach { (type, categories) ->
                        appendLine("- ${typeLabel(type)}: " + categories.joinToString { category ->
                            "${category.category} ${formatAmount(category.total)}"
                        })
                    }
                }
            }
        }.trim())
    }

    private suspend fun searchTransactions(toolCallId: String, args: com.google.gson.JsonObject): FinanceToolResult {
        val query = args.stringOrNull("query")?.trim().orEmpty()
        if (query.isBlank()) {
            return FinanceToolResult(toolCallId, false, "Error: 'query' es requerido y no puede estar vacio")
        }
        val type = args.stringOrNull("type")?.let { raw ->
            normalizeType(raw)
                ?: return FinanceToolResult(toolCallId, false, "Error: filtro 'type' invalido")
        }
        val limit = (args.get("limit")?.asInt ?: 20).coerceIn(1, 100)

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
            sb.appendLine("- **#${tx.id}** [$icon${tx.currency} ${formatAmount(tx.amount)}] ${tx.category}" +
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
            "Transaccion eliminada: #$id [${typeLabel(existing.type)}] ${existing.currency} ${formatAmount(existing.amount)} — ${existing.category}")
    }

    private suspend fun getBalance(toolCallId: String, args: com.google.gson.JsonObject): FinanceToolResult {
        val (from, to) = parsePeriod(args)
        val totals = financeDao.getTotalsByTypeAndCurrency(from, to)
        if (totals.isEmpty()) {
            return FinanceToolResult(toolCallId, true, "No hay transacciones en el periodo seleccionado.")
        }

        val periodLabel = periodLabel(args, from, to, capitalize = false)

        val content = buildString {
            appendLine("**Balance $periodLabel**")
            totals.groupBy { it.currency }.forEach { (currency, currencyTotals) ->
                val income = currencyTotals.totalFor("income")
                val expenses = currencyTotals.totalFor("expense")
                val investments = currencyTotals.totalFor("investment")
                val balance = income - expenses - investments
                appendLine(
                    "- $currency: **${formatSignedAmount(balance)}** " +
                        "(Ingresos: ${formatAmount(income)}, Gastos: ${formatAmount(expenses)}, " +
                        "Inversiones: ${formatAmount(investments)})"
                )
            }
        }.trim()
        return FinanceToolResult(toolCallId, true, content)
    }

    private suspend fun exportCsv(toolCallId: String, args: com.google.gson.JsonObject): FinanceToolResult {
        val periodArgs = args.deepCopy()
        if (!periodArgs.has("period")) periodArgs.addProperty("period", "all")
        val (from, to) = parsePeriod(periodArgs)
        val type = args.stringOrNull("type")?.let { raw ->
            normalizeType(raw)
                ?: return FinanceToolResult(toolCallId, false, "Error: filtro 'type' invalido")
        }

        val transactions = if (type != null) {
            financeDao.getByDateRange(from, to).filter { it.type == type }
        } else {
            financeDao.getByDateRange(from, to)
        }

        if (transactions.isEmpty()) {
            return FinanceToolResult(toolCallId, true, "No hay transacciones para exportar en el periodo seleccionado.")
        }

        val documentsDirectory = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: File(context.filesDir, "documents")
        val dir = File(documentsDirectory, "AIAgents/Finance")
        if (!dir.exists() && !dir.mkdirs()) {
            return FinanceToolResult(toolCallId, false, "Error: no se pudo crear el directorio privado de exportacion")
        }

        val timestamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.US)
            .withZone(ZoneId.systemDefault())
            .format(Instant.now())
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

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val zoneId: ZoneId
        get() = ZoneId.systemDefault()

    private fun com.google.gson.JsonObject.stringOrNull(name: String): String? =
        get(name)?.takeUnless { it.isJsonNull }?.asString

    private fun normalizeType(value: String?): String? = when (value?.trim()?.lowercase(Locale.ROOT)) {
        "expense", "gasto", "gastos" -> "expense"
        "income", "ingreso", "ingresos" -> "income"
        "investment", "inversion", "inversión", "inversiones" -> "investment"
        else -> null
    }

    private fun normalizeCurrency(value: String?): String? {
        val currency = value?.trim()?.uppercase(Locale.ROOT) ?: return null
        return currency.takeIf { it.matches(Regex("[A-Z]{3}")) }
    }

    private fun typeLabel(type: String): String = when (type) {
        "expense" -> "Gasto"
        "income" -> "Ingreso"
        "investment" -> "Inversion"
        else -> type
    }

    private fun formatAmount(amount: Double): String = String.format(Locale.US, "%.2f", amount)

    private fun formatSignedAmount(amount: Double): String =
        (if (amount >= 0.0) "+" else "") + formatAmount(amount)

    private fun List<FinanceTypeTotal>.totalFor(type: String): Double =
        firstOrNull { it.type == type }?.total ?: 0.0

    private fun parseDateOrNull(dateStr: String): Long? = try {
        LocalDate.parse(dateStr.trim(), dateFormatter)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
    } catch (_: DateTimeParseException) {
        null
    }

    private fun formatDate(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(zoneId).toLocalDate().format(dateFormatter)

    private fun periodLabel(
        args: com.google.gson.JsonObject,
        from: Long,
        to: Long,
        capitalize: Boolean
    ): String {
        val label = if (args.stringOrNull("from") != null) {
            "${formatDate(from)} — ${formatDate(to - 1L)}"
        } else {
            when (args.stringOrNull("period") ?: "month") {
                "today" -> "hoy"
                "week" -> "esta semana"
                "month" -> "este mes"
                "year" -> "este año"
                "all" -> "todo el historial"
                else -> error("Periodo no validado")
            }
        }
        return if (capitalize) label.replaceFirstChar { it.uppercase() } else label
    }

    private fun todayStart(): Long {
        return LocalDate.now(zoneId).atStartOfDay(zoneId).toInstant().toEpochMilli()
    }

    private fun parsePeriod(args: com.google.gson.JsonObject): Pair<Long, Long> {
        val fromStr = args.stringOrNull("from")
        val toStr = args.stringOrNull("to")
        if ((fromStr == null) != (toStr == null)) {
            throw IllegalArgumentException("'from' y 'to' deben enviarse juntos")
        }
        if (fromStr != null && toStr != null) {
            val from = parseDateOrNull(fromStr)
                ?: throw IllegalArgumentException("'from' debe usar el formato yyyy-MM-dd")
            val toDate = try {
                LocalDate.parse(toStr.trim(), dateFormatter)
            } catch (_: DateTimeParseException) {
                throw IllegalArgumentException("'to' debe usar el formato yyyy-MM-dd")
            }
            val to = toDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            require(from < to) { "'from' debe ser anterior o igual a 'to'" }
            return from to to
        }

        val period = args.stringOrNull("period") ?: "month"
        val cal = Calendar.getInstance()
        val to = cal.timeInMillis + 1L

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
            else -> throw IllegalArgumentException("period debe ser today, week, month, year o all")
        }
        return cal.timeInMillis to to
    }
}
