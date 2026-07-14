package com.aiagents.app.presentation.memory

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiagents.app.data.local.MemoryDao
import com.aiagents.app.data.memory.CortexMarkdownMemoryStore
import com.aiagents.app.data.memory.CortexMemoryPolicy
import com.aiagents.app.data.memory.CortexMemorySnapshot
import com.aiagents.app.data.memory.CortexProfileStore
import com.aiagents.app.data.model.MemoryEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import javax.inject.Inject

@HiltViewModel
class MemoryViewModel @Inject constructor(
    private val memoryDao: MemoryDao,
    private val cortexMarkdownMemoryStore: CortexMarkdownMemoryStore,
    private val cortexProfileStore: CortexProfileStore,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    companion object {
        private const val TAG = "MemoryViewModel"
        private const val MAX_MARKDOWN_IMPORT_BYTES = 64 * 1024
    }

    private val _memories = MutableStateFlow<List<MemoryEntity>>(emptyList())
    val memories: StateFlow<List<MemoryEntity>> = _memories

    private val _totalCount = MutableStateFlow(0)
    val totalCount: StateFlow<Int> = _totalCount

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val initialEditors = ContextFileKind.entries.associateWith { kind ->
        val snapshot = snapshotFor(kind)
        ContextFileEditorState(
            snapshot = snapshot,
            draft = snapshot.content,
            baseRevision = snapshot.revision
        )
    }
    private val _contextFileEditors = MutableStateFlow(initialEditors)
    val contextFileEditors: StateFlow<Map<ContextFileKind, ContextFileEditorState>> =
        _contextFileEditors

    private val _selectedContextFile = MutableStateFlow(ContextFileKind.SOUL)
    val selectedContextFile: StateFlow<ContextFileKind> = _selectedContextFile

    init {
        loadMemories()
        observeContextFile(ContextFileKind.SOUL, cortexProfileStore.soulSnapshots)
        observeContextFile(ContextFileKind.USER, cortexProfileStore.userSnapshots)
        observeContextFile(ContextFileKind.MEMORY, cortexMarkdownMemoryStore.snapshots)
    }

    private fun observeContextFile(
        kind: ContextFileKind,
        snapshots: StateFlow<CortexMemorySnapshot>
    ) {
        viewModelScope.launch {
            snapshots.collect { snapshot ->
                updateEditor(kind) { current ->
                    if (current.dirty) {
                        current.copy(
                            snapshot = snapshot,
                            conflict = snapshot.revision != current.baseRevision
                        )
                    } else {
                        current.copy(
                            snapshot = snapshot,
                            draft = snapshot.content,
                            baseRevision = snapshot.revision,
                            conflict = false
                        )
                    }
                }
            }
        }
    }

    fun selectContextFile(kind: ContextFileKind) {
        _selectedContextFile.value = kind
    }

    fun setContextFileDraft(kind: ContextFileKind, markdown: String) {
        val latest = snapshotFor(kind)
        updateEditor(kind) { current ->
            val dirty = markdown != latest.content
            current.copy(
                snapshot = latest,
                draft = markdown,
                dirty = dirty,
                baseRevision = if (dirty) current.baseRevision else latest.revision,
                conflict = if (dirty) current.conflict else false
            )
        }
    }

    fun contextFileCharacterCount(kind: ContextFileKind, markdown: String): Int {
        val maxChars = _contextFileEditors.value.getValue(kind).snapshot.maxChars
        val normalizedEntries = CortexMemoryPolicy.parse(markdown, maxChars).entries
        return CortexMemoryPolicy.countCharacters(
            CortexMemoryPolicy.serialize(normalizedEntries)
        )
    }

    fun saveContextFile(kind: ContextFileKind) {
        persistContextFile(kind, _contextFileEditors.value.getValue(kind).baseRevision)
    }

    fun forceSaveContextFile(kind: ContextFileKind) {
        persistContextFile(kind, expectedRevision = null)
    }

    private fun persistContextFile(kind: ContextFileKind, expectedRevision: String?) {
        val editor = _contextFileEditors.value.getValue(kind)
        val draft = editor.draft
        val maxChars = editor.snapshot.maxChars
        val usedChars = contextFileCharacterCount(kind, draft)
        if (usedChars > maxChars) {
            updateEditor(kind) {
                it.copy(result = "${kind.fileName} excede el limite: $usedChars/$maxChars caracteres. No se trunco ni se guardo.")
            }
            return
        }

        val result = when (kind) {
            ContextFileKind.SOUL -> cortexProfileStore.replaceSoul(draft, expectedRevision)
            ContextFileKind.USER -> cortexProfileStore.replaceUser(draft, expectedRevision)
            ContextFileKind.MEMORY -> cortexMarkdownMemoryStore.replaceAll(draft, expectedRevision)
        }
        updateEditor(kind) { current ->
            if (result.success) {
                current.copy(
                    snapshot = result.snapshot,
                    draft = result.snapshot.content,
                    baseRevision = result.snapshot.revision,
                    dirty = false,
                    conflict = false,
                    result = result.message
                )
            } else {
                current.copy(
                    snapshot = result.snapshot,
                    conflict = result.snapshot.revision != current.baseRevision,
                    result = result.message
                )
            }
        }
    }

    fun revertContextFileDraft(kind: ContextFileKind) {
        val latest = snapshotFor(kind)
        updateEditor(kind) {
            it.copy(
                snapshot = latest,
                draft = latest.content,
                baseRevision = latest.revision,
                dirty = false,
                conflict = false,
                result = "Se descarto el borrador y se recargo ${kind.fileName}."
            )
        }
    }

    fun clearContextFileResult(kind: ContextFileKind) {
        updateEditor(kind) { it.copy(result = null) }
    }

    fun exportContextFile(kind: ContextFileKind, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val snapshot = snapshotFor(kind)
                val output = appContext.contentResolver.openOutputStream(uri)
                    ?: error("No se pudo abrir el documento de destino")
                output.use { stream ->
                    stream.write(snapshot.content.toByteArray(StandardCharsets.UTF_8))
                }
                updateEditor(kind) {
                    it.copy(result = "${kind.fileName} exportado (${snapshot.usedChars}/${snapshot.maxChars} caracteres).")
                }
            } catch (e: Exception) {
                Log.e(TAG, "${kind.fileName} export failed", e)
                updateEditor(kind) {
                    it.copy(result = "No se pudo exportar ${kind.fileName}: ${e.message}")
                }
            }
        }
    }

    fun importContextFile(kind: ContextFileKind, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val input = appContext.contentResolver.openInputStream(uri)
                    ?: error("No se pudo abrir el documento")
                val markdown = input.use { stream ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0
                    while (true) {
                        val read = stream.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_MARKDOWN_IMPORT_BYTES) {
                            error(
                                "El archivo supera el máximo de $MAX_MARKDOWN_IMPORT_BYTES bytes permitido para importar ${kind.fileName}"
                            )
                        }
                        output.write(buffer, 0, read)
                    }
                    StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(output.toByteArray()))
                        .toString()
                }

                // Keep the complete source in the editor. Oversized files are
                // rejected below instead of silently clipping user content.
                setContextFileDraft(kind, markdown)

                val maxChars = snapshotFor(kind).maxChars
                val usedChars = contextFileCharacterCount(kind, markdown)
                if (usedChars > maxChars) {
                    updateEditor(kind) {
                        it.copy(result = "El archivo tiene $usedChars/$maxChars caracteres. No se trunco ni se importo; puedes reducirlo en el editor.")
                    }
                    return@launch
                }
                persistContextFile(
                    kind = kind,
                    expectedRevision = _contextFileEditors.value.getValue(kind).baseRevision
                )
            } catch (e: Exception) {
                Log.e(TAG, "${kind.fileName} import failed", e)
                updateEditor(kind) {
                    it.copy(result = "No se pudo importar ${kind.fileName}: ${e.message}")
                }
            }
        }
    }

    private fun snapshotFor(kind: ContextFileKind): CortexMemorySnapshot = when (kind) {
        ContextFileKind.SOUL -> cortexProfileStore.soulSnapshot()
        ContextFileKind.USER -> cortexProfileStore.userSnapshot()
        ContextFileKind.MEMORY -> cortexMarkdownMemoryStore.snapshot()
    }

    private fun updateEditor(
        kind: ContextFileKind,
        transform: (ContextFileEditorState) -> ContextFileEditorState
    ) {
        _contextFileEditors.update { editors ->
            editors + (kind to transform(editors.getValue(kind)))
        }
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

enum class ContextFileKind(val fileName: String) {
    SOUL(CortexProfileStore.SOUL_FILE_NAME),
    USER(CortexProfileStore.USER_FILE_NAME),
    MEMORY(CortexMarkdownMemoryStore.FILE_NAME)
}

data class ContextFileEditorState(
    val snapshot: CortexMemorySnapshot,
    val draft: String,
    val baseRevision: String,
    val dirty: Boolean = false,
    val conflict: Boolean = false,
    val result: String? = null
)
