package com.aiagents.app.data.memory

import android.content.Context
import android.util.AtomicFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Atomic, app-private source of truth for Cortex's always-on MEMORY.md.
 *
 * Room memories remain a searchable archive. Only this bounded file is injected on every Cortex
 * request, which makes its character budget meaningful.
 */
@Singleton
class CortexMarkdownMemoryStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val lock = Any()
    private val memoryDirectory = File(context.noBackupFilesDir, DIRECTORY_NAME)
    private val memoryFile = File(memoryDirectory, FILE_NAME)
    private val atomicFile = AtomicFile(memoryFile)

    private val _snapshots = MutableStateFlow(loadOrCreate())
    val snapshots: StateFlow<CortexMemorySnapshot> = _snapshots.asStateFlow()

    fun snapshot(): CortexMemorySnapshot = _snapshots.value

    fun applyOperations(
        operations: List<CortexMemoryOperation>,
        expectedRevision: String? = null
    ): CortexMemoryMutationResult = synchronized(lock) {
        val current = reloadLocked()
        revisionConflict(expectedRevision, current)?.let { return@synchronized it }
        if (current.storageError != null) {
            return@synchronized CortexMemoryMutationResult(
                success = false,
                changed = false,
                message = "MEMORY.md is blocked because its stored content is invalid. Repair it explicitly in the Memory screen; no agent mutation was applied.",
                snapshot = current
            )
        }

        val policyResult = CortexMemoryPolicy.apply(current.entries, operations)
        if (!policyResult.success) {
            return@synchronized CortexMemoryMutationResult(
                success = false,
                changed = false,
                message = policyResult.message,
                snapshot = current
            )
        }
        if (!policyResult.changed) {
            return@synchronized CortexMemoryMutationResult(
                success = true,
                changed = false,
                message = policyResult.message,
                snapshot = current
            )
        }
        persistLocked(policyResult.entries, current)
    }

    /** Replaces the entire Markdown document, used by the editor and SAF import. */
    fun replaceAll(
        markdown: String,
        expectedRevision: String? = null
    ): CortexMemoryMutationResult = synchronized(lock) {
        val current = reloadLocked()
        revisionConflict(expectedRevision, current)?.let { return@synchronized it }
        if (markdown.length > MAX_DOCUMENT_UTF16_UNITS) {
            return@synchronized CortexMemoryMutationResult(
                success = false,
                changed = false,
                message = "MEMORY.md input is too large to validate safely.",
                snapshot = current
            )
        }

        val parsed = CortexMemoryPolicy.parse(markdown)
        if (!parsed.success) {
            return@synchronized CortexMemoryMutationResult(
                success = false,
                changed = false,
                message = parsed.message,
                snapshot = current
            )
        }
        val serialized = CortexMemoryPolicy.serialize(parsed.entries)
        if (serialized == current.content && current.storageError == null) {
            return@synchronized CortexMemoryMutationResult(
                success = true,
                changed = false,
                message = "MEMORY.md already contains these entries.",
                snapshot = current
            )
        }
        val recoveryBackupCreated = if (current.storageError != null) {
            if (!preserveRecoveryCopyLocked()) {
                return@synchronized CortexMemoryMutationResult(
                    success = false,
                    changed = false,
                    message = "The invalid MEMORY.md could not be backed up, so it was not overwritten.",
                    snapshot = current
                )
            }
            true
        } else {
            false
        }
        persistLocked(parsed.entries, current).let { result ->
            if (result.success && recoveryBackupCreated) {
                result.copy(
                    message = result.message + " The invalid previous file was preserved as $RECOVERY_FILE_NAME."
                )
            } else {
                result
            }
        }
    }

    private fun persistLocked(
        entries: List<String>,
        fallback: CortexMemorySnapshot
    ): CortexMemoryMutationResult {
        val content = CortexMemoryPolicy.serialize(entries)
        return try {
            writeAtomically(content)
            val updated = createSnapshot(content, storageError = null)
            _snapshots.value = updated
            CortexMemoryMutationResult(
                success = true,
                changed = true,
                message = "MEMORY.md updated (${updated.usedChars}/${updated.maxChars} characters).",
                snapshot = updated
            )
        } catch (_: Exception) {
            val current = loadSnapshotFromDisk().let { disk ->
                if (disk.storageError == null) disk
                else fallback.copy(storageError = "Could not write MEMORY.md; the previous version was preserved.")
            }
            _snapshots.value = current
            CortexMemoryMutationResult(
                success = false,
                changed = false,
                message = "Could not write MEMORY.md; the previous version was preserved.",
                snapshot = current
            )
        }
    }

    private fun revisionConflict(
        expectedRevision: String?,
        current: CortexMemorySnapshot
    ): CortexMemoryMutationResult? {
        if (expectedRevision == null || expectedRevision == current.revision) return null
        return CortexMemoryMutationResult(
            success = false,
            changed = false,
            message = "MEMORY.md changed since it was opened. Reload it before saving to avoid losing newer edits.",
            snapshot = current
        )
    }

    private fun reloadLocked(): CortexMemorySnapshot {
        val loaded = loadSnapshotFromDisk()
        _snapshots.value = loaded
        return loaded
    }

    private fun loadOrCreate(): CortexMemorySnapshot = synchronized(lock) {
        try {
            memoryDirectory.mkdirs()
            restrictToOwner(memoryDirectory)
            try {
                snapshotFromRaw(readAtomicText())
            } catch (_: FileNotFoundException) {
                writeAtomically("")
                snapshotFromRaw("")
            }
        } catch (_: Exception) {
            createSnapshot(
                content = "",
                storageError = "MEMORY.md could not be initialized in app-private storage."
            )
        }
    }

    private fun loadSnapshotFromDisk(): CortexMemorySnapshot {
        return try {
            snapshotFromRaw(readAtomicText())
        } catch (_: FileNotFoundException) {
            createSnapshot("", "MEMORY.md is missing from app-private storage.")
        } catch (_: Exception) {
            createSnapshot("", "MEMORY.md could not be read safely.")
        }
    }

    /** AtomicFile.openRead restores a recoverable .bak before exposing the base file. */
    private fun readAtomicText(): String = atomicFile.openRead().use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_DOCUMENT_BYTES) {
                throw IOException("MEMORY.md exceeds the safe on-disk read limit")
            }
            output.write(buffer, 0, read)
        }
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(output.toByteArray()))
            .toString()
    }

    private fun snapshotFromRaw(raw: String): CortexMemorySnapshot {
        val parsed = CortexMemoryPolicy.parse(raw)
        val content = CortexMemoryPolicy.serialize(parsed.entries)
        return createSnapshot(
            content = content,
            storageError = parsed.message.takeUnless { parsed.success }
        )
    }

    /** Keeps invalid manual/drifted content available for recovery before an explicit UI repair. */
    private fun preserveRecoveryCopyLocked(): Boolean {
        return try {
            val recoveryFile = File(memoryDirectory, RECOVERY_FILE_NAME)
            atomicFile.openRead().use { input ->
                FileOutputStream(recoveryFile, false).use { output ->
                    input.copyTo(output)
                    output.flush()
                    output.fd.sync()
                }
            }
            restrictToOwner(recoveryFile)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun createSnapshot(content: String, storageError: String?): CortexMemorySnapshot {
        val entries = if (content.isBlank()) emptyList()
        else content.split(CortexMemoryPolicy.ENTRY_DELIMITER)
        val used = CortexMemoryPolicy.countCharacters(content)
        val max = CortexMemoryPolicy.HERMES_MEMORY_MAX_CHARS
        return CortexMemorySnapshot(
            content = content,
            entries = entries,
            usedChars = used,
            maxChars = max,
            revision = sha256(content),
            storageError = storageError,
            usagePercent = ((used.toDouble() / max.toDouble()) * 100.0).roundToInt()
        )
    }

    private fun writeAtomically(content: String) {
        memoryDirectory.mkdirs()
        var stream: FileOutputStream? = null
        try {
            stream = atomicFile.startWrite()
            stream.write(content.toByteArray(StandardCharsets.UTF_8))
            stream.flush()
            stream.fd.sync()
            atomicFile.finishWrite(stream)
            stream = null
            restrictToOwner(memoryFile)
            restrictToOwner(memoryDirectory)
        } catch (error: Exception) {
            atomicFile.failWrite(stream)
            throw error
        }
    }

    private fun restrictToOwner(file: File) {
        file.setReadable(false, false)
        file.setWritable(false, false)
        file.setExecutable(false, false)
        file.setReadable(true, true)
        file.setWritable(true, true)
        if (file.isDirectory) file.setExecutable(true, true)
    }

    private fun sha256(content: String): String = MessageDigest.getInstance("SHA-256")
        .digest(content.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

    companion object {
        const val FILE_NAME = "MEMORY.md"
        const val RECOVERY_FILE_NAME = "MEMORY.invalid.recovery.bak"
        private const val DIRECTORY_NAME = "cortex_memory"
        private const val MAX_DOCUMENT_BYTES = 16 * 1024
        private const val MAX_DOCUMENT_UTF16_UNITS = 32_768
    }
}

data class CortexMemorySnapshot(
    val content: String,
    val entries: List<String>,
    val usedChars: Int,
    val maxChars: Int,
    val usagePercent: Int,
    val revision: String,
    val storageError: String?
) {
    val remainingChars: Int get() = (maxChars - usedChars).coerceAtLeast(0)
    val isNearCapacity: Boolean get() = usagePercent >= 80
}

data class CortexMemoryMutationResult(
    val success: Boolean,
    val changed: Boolean,
    val message: String,
    val snapshot: CortexMemorySnapshot
)
