package com.aiagents.app.data.memory

import android.content.Context
import android.util.AtomicFile
import com.aiagents.app.data.local.SecurePreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-private identity files inspired by Hermes Agent.
 *
 * SOUL.md defines the orchestrator's stable identity. USER.md is a compact, agent-curated profile
 * of the person using the app. Both are atomic files and are frozen by [RuntimeContextProvider]
 * for the lifetime of a conversation.
 */
@Singleton
class CortexProfileStore @Inject constructor(
    @ApplicationContext context: Context,
    private val securePreferences: SecurePreferences
) {
    private val lock = Any()
    private val directory = File(context.noBackupFilesDir, DIRECTORY_NAME)
    private val soulDocument = ProfileDocument(
        file = File(directory, SOUL_FILE_NAME),
        maxChars = HERMES_CONTEXT_FILE_MAX_CHARS,
        defaultContent = {
            defaultSoul(securePreferences.getCortexName() ?: DEFAULT_AGENT_NAME)
        }
    )
    private val userDocument = ProfileDocument(
        file = File(directory, USER_FILE_NAME),
        maxChars = HERMES_USER_MAX_CHARS,
        defaultContent = {
            defaultUser(
                userName = securePreferences.getUserName(),
                preferredName = securePreferences.getPreferredUserName()
            )
        }
    )

    private val _soulSnapshots = MutableStateFlow(soulDocument.loadOrCreate())
    val soulSnapshots: StateFlow<CortexMemorySnapshot> = _soulSnapshots.asStateFlow()

    private val _userSnapshots = MutableStateFlow(userDocument.loadOrCreate())
    val userSnapshots: StateFlow<CortexMemorySnapshot> = _userSnapshots.asStateFlow()

    fun soulSnapshot(): CortexMemorySnapshot = _soulSnapshots.value
    fun userSnapshot(): CortexMemorySnapshot = _userSnapshots.value

    /** One-time onboarding seed. The user has not had an opportunity to customize these files yet. */
    fun seedFromOnboarding(
        agentName: String,
        userName: String,
        preferredName: String
    ) = synchronized(lock) {
        val canonicalAgentName = agentName.trim().ifBlank { DEFAULT_AGENT_NAME }
        securePreferences.saveCortexName(canonicalAgentName)
        _soulSnapshots.value = soulDocument.replace(
            defaultSoul(canonicalAgentName),
            expectedRevision = null
        ).snapshot
        _userSnapshots.value = userDocument.replace(
            defaultUser(userName, preferredName),
            expectedRevision = null
        ).snapshot
    }

    /**
     * Migrates only an untouched generated SOUL.md from the default Cortex name to the real agent
     * name. A user-edited soul is never overwritten.
     */
    fun alignGeneratedIdentity(
        agentName: String,
        userName: String?,
        preferredName: String?
    ) = synchronized(lock) {
        val canonicalAgentName = agentName.trim().ifBlank { DEFAULT_AGENT_NAME }
        val storedAgentName = securePreferences.getCortexName() ?: DEFAULT_AGENT_NAME
        val currentSoul = soulDocument.reload()
        val generatedCandidates = setOf(
            defaultSoul(DEFAULT_AGENT_NAME),
            defaultSoul(storedAgentName),
            legacyDefaultSoul(DEFAULT_AGENT_NAME, null, null),
            legacyDefaultSoul(DEFAULT_AGENT_NAME, userName, preferredName),
            legacyDefaultSoul(storedAgentName, null, null),
            legacyDefaultSoul(storedAgentName, userName, preferredName)
        )
        if (currentSoul.storageError == null && currentSoul.content in generatedCandidates &&
            currentSoul.content != defaultSoul(canonicalAgentName)
        ) {
            _soulSnapshots.value = soulDocument.replace(
                defaultSoul(canonicalAgentName),
                expectedRevision = currentSoul.revision
            ).snapshot
        } else {
            _soulSnapshots.value = currentSoul
        }
        securePreferences.saveCortexName(canonicalAgentName)
        val currentUser = userDocument.reload()
        val generatedUserCandidates = setOf(
            defaultUser(null, null),
            defaultUser(userName, preferredName)
        )
        _userSnapshots.value = if (
            currentUser.storageError == null &&
            currentUser.content in generatedUserCandidates &&
            currentUser.content != defaultUser(userName, preferredName)
        ) {
            userDocument.replace(
                defaultUser(userName, preferredName),
                expectedRevision = currentUser.revision
            ).snapshot
        } else {
            currentUser
        }
    }

    fun replaceSoul(markdown: String, expectedRevision: String? = null): CortexMemoryMutationResult =
        synchronized(lock) {
            soulDocument.replace(markdown, expectedRevision).also { _soulSnapshots.value = it.snapshot }
        }

    fun replaceUser(markdown: String, expectedRevision: String? = null): CortexMemoryMutationResult =
        synchronized(lock) {
            userDocument.replace(markdown, expectedRevision).also { _userSnapshots.value = it.snapshot }
        }

    fun applyUserOperations(
        operations: List<CortexMemoryOperation>,
        expectedRevision: String? = null
    ): CortexMemoryMutationResult = synchronized(lock) {
        val current = userDocument.reload()
        _userSnapshots.value = current
        revisionConflict(USER_FILE_NAME, expectedRevision, current)?.let { return@synchronized it }
        if (current.storageError != null) {
            return@synchronized mutationFailure(
                "$USER_FILE_NAME is blocked because its stored content is invalid. Repair it in the Memory screen.",
                current
            )
        }
        val policyResult = CortexMemoryPolicy.apply(
            currentEntries = current.entries,
            operations = operations,
            maxChars = HERMES_USER_MAX_CHARS
        )
        if (!policyResult.success) {
            return@synchronized mutationFailure(policyResult.message, current)
        }
        if (!policyResult.changed) {
            return@synchronized CortexMemoryMutationResult(true, false, policyResult.message, current)
        }
        userDocument.persist(policyResult.entries, current).also { _userSnapshots.value = it.snapshot }
    }

    private inner class ProfileDocument(
        private val file: File,
        private val maxChars: Int,
        private val defaultContent: () -> String
    ) {
        private val atomicFile = AtomicFile(file)

        fun loadOrCreate(): CortexMemorySnapshot = try {
            directory.mkdirs()
            restrictToOwner(directory)
            try {
                snapshotFromRaw(readAtomicText())
            } catch (_: FileNotFoundException) {
                val initial = defaultContent()
                writeAtomically(initial)
                snapshotFromRaw(initial)
            }
        } catch (_: Exception) {
            createSnapshot("", "${file.name} could not be initialized in app-private storage.")
        }

        fun reload(): CortexMemorySnapshot = try {
            snapshotFromRaw(readAtomicText())
        } catch (_: FileNotFoundException) {
            createSnapshot("", "${file.name} is missing from app-private storage.")
        } catch (_: Exception) {
            createSnapshot("", "${file.name} could not be read safely.")
        }

        fun replace(
            markdown: String,
            expectedRevision: String?
        ): CortexMemoryMutationResult {
            val current = reload()
            revisionConflict(file.name, expectedRevision, current)?.let { return it }
            if (markdown.length > MAX_DOCUMENT_UTF16_UNITS) {
                return mutationFailure("${file.name} input is too large to validate safely.", current)
            }
            val parsed = CortexMemoryPolicy.parse(markdown, maxChars)
            if (!parsed.success) return mutationFailure(parsed.message, current)
            val serialized = CortexMemoryPolicy.serialize(parsed.entries)
            if (serialized == current.content && current.storageError == null) {
                return CortexMemoryMutationResult(
                    success = true,
                    changed = false,
                    message = "${file.name} already contains this content.",
                    snapshot = current
                )
            }
            return persist(parsed.entries, current)
        }

        fun persist(
            entries: List<String>,
            fallback: CortexMemorySnapshot
        ): CortexMemoryMutationResult {
            val content = CortexMemoryPolicy.serialize(entries)
            return try {
                writeAtomically(content)
                val updated = createSnapshot(content, null)
                CortexMemoryMutationResult(
                    success = true,
                    changed = true,
                    message = "${file.name} updated (${updated.usedChars}/${updated.maxChars} characters).",
                    snapshot = updated
                )
            } catch (_: Exception) {
                mutationFailure("Could not write ${file.name}; the previous version was preserved.", fallback)
            }
        }

        private fun snapshotFromRaw(raw: String): CortexMemorySnapshot {
            val parsed = CortexMemoryPolicy.parse(raw, maxChars)
            return createSnapshot(
                content = CortexMemoryPolicy.serialize(parsed.entries),
                storageError = parsed.message.takeUnless { parsed.success }
            )
        }

        private fun createSnapshot(content: String, storageError: String?): CortexMemorySnapshot {
            val entries = if (content.isBlank()) emptyList()
            else content.split(CortexMemoryPolicy.ENTRY_DELIMITER)
            val used = CortexMemoryPolicy.countCharacters(content)
            return CortexMemorySnapshot(
                content = content,
                entries = entries,
                usedChars = used,
                maxChars = maxChars,
                usagePercent = ((used.toDouble() / maxChars) * 100.0).roundToInt(),
                revision = sha256(content),
                storageError = storageError
            )
        }

        private fun readAtomicText(): String = atomicFile.openRead().use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_DOCUMENT_BYTES) throw IOException("${file.name} is too large")
                output.write(buffer, 0, read)
            }
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(output.toByteArray()))
                .toString()
        }

        private fun writeAtomically(content: String) {
            directory.mkdirs()
            var stream: FileOutputStream? = null
            try {
                stream = atomicFile.startWrite()
                stream.write(content.toByteArray(StandardCharsets.UTF_8))
                stream.flush()
                stream.fd.sync()
                atomicFile.finishWrite(stream)
                stream = null
                restrictToOwner(file)
                restrictToOwner(directory)
            } catch (error: Exception) {
                atomicFile.failWrite(stream)
                throw error
            }
        }
    }

    companion object {
        const val SOUL_FILE_NAME = "SOUL.md"
        const val USER_FILE_NAME = "USER.md"
        const val HERMES_USER_MAX_CHARS = 1_375
        const val HERMES_CONTEXT_FILE_MAX_CHARS = 20_000
        const val DEFAULT_AGENT_NAME = "Cortex"
        private const val DIRECTORY_NAME = "cortex_memory"
        private const val MAX_DOCUMENT_BYTES = 64 * 1024
        private const val MAX_DOCUMENT_UTF16_UNITS = 65_536

        internal fun defaultSoul(agentName: String): String {
            val safeAgent = singleLine(agentName).ifBlank { DEFAULT_AGENT_NAME }
            return """
                # SOUL.md

                You are $safeAgent, a capable personal AI running on the user's Android device.

                Be honest about uncertainty and completed actions. Protect the user's privacy, ask before consequential external actions, and use available tools when they improve accuracy. Match the user's language and adapt to their preferences without pretending to know facts that are not in context.
            """.trimIndent()
        }

        private fun legacyDefaultSoul(
            agentName: String,
            userName: String?,
            preferredName: String?
        ): String {
            val safeAgent = singleLine(agentName).ifBlank { DEFAULT_AGENT_NAME }
            val safeUser = singleLine(userName).ifBlank { "the user" }
            val safePreferred = singleLine(preferredName).ifBlank { safeUser }
            return """
                # SOUL.md

                You are $safeAgent, a capable personal AI and central agent orchestrator running on the user's Android device.
                You are speaking with $safeUser; they prefer to be called $safePreferred.

                Be honest about uncertainty and completed actions. Protect the user's privacy, ask before consequential external actions, and use available tools when they improve accuracy. Match the user's language and adapt to their preferences without pretending to know facts that are not in context.
            """.trimIndent()
        }

        internal fun defaultUser(userName: String?, preferredName: String?): String {
            val safeUser = singleLine(userName).ifBlank { "Not provided" }
            val safePreferred = singleLine(preferredName).ifBlank {
                safeUser.takeUnless { it == "Not provided" } ?: "Not provided"
            }
            return """
                # USER.md

                - Name: $safeUser
                - Preferred name: $safePreferred
                - Address the user as: $safePreferred
            """.trimIndent()
        }

        private fun singleLine(value: String?): String = value.orEmpty()
            .replace(Regex("[\\r\\n\\t]+"), " ")
            .trim()

        private fun sha256(content: String): String = MessageDigest.getInstance("SHA-256")
            .digest(content.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

        private fun restrictToOwner(file: File) {
            file.setReadable(false, false)
            file.setWritable(false, false)
            file.setExecutable(false, false)
            file.setReadable(true, true)
            file.setWritable(true, true)
            if (file.isDirectory) file.setExecutable(true, true)
        }

        private fun revisionConflict(
            fileName: String,
            expectedRevision: String?,
            current: CortexMemorySnapshot
        ): CortexMemoryMutationResult? {
            if (expectedRevision == null || expectedRevision == current.revision) return null
            return mutationFailure(
                "$fileName changed since it was opened. Reload it before saving.",
                current
            )
        }

        private fun mutationFailure(
            message: String,
            snapshot: CortexMemorySnapshot
        ) = CortexMemoryMutationResult(false, false, message, snapshot)
    }
}
