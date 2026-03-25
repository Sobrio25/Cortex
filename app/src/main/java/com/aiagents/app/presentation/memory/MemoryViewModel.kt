package com.aiagents.app.presentation.memory

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiagents.app.data.local.MemoryDao
import com.aiagents.app.data.model.MemoryEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MemoryViewModel @Inject constructor(
    private val memoryDao: MemoryDao,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    companion object {
        private const val TAG = "MemoryViewModel"
    }

    private val _memories = MutableStateFlow<List<MemoryEntity>>(emptyList())
    val memories: StateFlow<List<MemoryEntity>> = _memories

    private val _totalCount = MutableStateFlow(0)
    val totalCount: StateFlow<Int> = _totalCount

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    init {
        loadMemories()
    }

    fun loadMemories() {
        viewModelScope.launch {
            val category = _selectedCategory.value
            val query = _searchQuery.value.trim()

            _memories.value = when {
                query.isNotEmpty() -> {
                    val ftsQuery = query.split("\\s+".toRegex()).joinToString(" ") { "$it*" }
                    try {
                        val results = memoryDao.searchFts(ftsQuery, 50)
                        if (category != null) results.filter { it.category == category } else results
                    } catch (_: Exception) {
                        if (category != null) memoryDao.getByCategory(category, 50)
                        else memoryDao.getAll(50)
                    }
                }
                category != null -> memoryDao.getByCategory(category, 50)
                else -> memoryDao.getAll(50)
            }
            _totalCount.value = memoryDao.count()
        }
    }

    fun setCategory(category: String?) {
        _selectedCategory.value = category
        loadMemories()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        loadMemories()
    }

    fun deleteMemory(id: Long) {
        viewModelScope.launch {
            memoryDao.deleteLinksForMemory(id)
            memoryDao.deleteById(id)
            loadMemories()
        }
    }

    fun updateMemory(id: Long, content: String, importance: Int) {
        viewModelScope.launch {
            val existing = memoryDao.getById(id) ?: return@launch
            memoryDao.update(existing.copy(
                content = content,
                importance = importance,
                updatedAt = System.currentTimeMillis()
            ))
            loadMemories()
        }
    }

    fun runCleanupNow() {
        viewModelScope.launch {
            val thirtyDaysAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
            memoryDao.decayOldMemories(threshold = thirtyDaysAgo, factor = 0.9f)
            memoryDao.deleteWeakMemories()
            memoryDao.deleteExpiredMemories()
            loadMemories()
        }
    }

    fun deleteAllMemories() {
        viewModelScope.launch {
            val all = memoryDao.getAll(1000)
            for (m in all) {
                memoryDao.deleteLinksForMemory(m.id)
                memoryDao.deleteById(m.id)
            }
            loadMemories()
        }
    }

    private val _exportResult = MutableStateFlow<String?>(null)
    val exportResult: StateFlow<String?> = _exportResult

    fun clearExportResult() { _exportResult.value = null }

    fun exportMemories(uri: Uri) {
        viewModelScope.launch {
            try {
                val all = memoryDao.getAll(10000)
                val exportData = all.map { m ->
                    mapOf(
                        "content" to m.content,
                        "category" to m.category,
                        "subcategory" to m.subcategory,
                        "importance" to m.importance,
                        "confidence" to m.confidence,
                        "source" to m.source,
                        "accessCount" to m.accessCount,
                        "createdAt" to m.createdAt,
                        "updatedAt" to m.updatedAt
                    )
                }
                val json = Gson().toJson(exportData)
                appContext.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                _exportResult.value = "Exportadas ${all.size} memorias"
            } catch (e: Exception) {
                Log.e(TAG, "Export failed", e)
                _exportResult.value = "Error: ${e.message}"
            }
        }
    }

    fun importMemories(uri: Uri) {
        viewModelScope.launch {
            try {
                val json = appContext.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: return@launch
                val type = object : TypeToken<List<Map<String, Any>>>() {}.type
                val data: List<Map<String, Any>> = Gson().fromJson(json, type)
                val now = System.currentTimeMillis()
                var count = 0
                for (item in data) {
                    val content = item["content"]?.toString() ?: continue
                    val category = item["category"]?.toString() ?: "fact"
                    val subcategory = item["subcategory"]?.toString() ?: ""
                    val importance = (item["importance"] as? Double)?.toInt() ?: 5
                    val confidence = (item["confidence"] as? Double)?.toFloat() ?: 1.0f

                    memoryDao.insert(MemoryEntity(
                        content = content,
                        category = category,
                        subcategory = subcategory,
                        importance = importance,
                        confidence = confidence,
                        source = "import",
                        createdAt = now,
                        updatedAt = now,
                        lastAccessedAt = now
                    ))
                    count++
                }
                _exportResult.value = "Importadas $count memorias"
                loadMemories()
            } catch (e: Exception) {
                Log.e(TAG, "Import failed", e)
                _exportResult.value = "Error: ${e.message}"
            }
        }
    }
}
