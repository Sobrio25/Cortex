package com.aiagents.app.data.speech

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.aiagents.app.BuildConfig
import com.google.android.play.core.splitcompat.SplitCompat
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

internal data class ExternalVoicePackManifest(
    val versionCode: Int,
    val splitName: String,
    val url: String,
    val sha256: String,
    val bytes: Long
)

internal fun parseExternalVoicePackManifest(
    json: String,
    manifestUrl: String,
    expectedVersionCode: Int
): ExternalVoicePackManifest {
    val root = JsonParser.parseString(json).asJsonObject
    val manifest = ExternalVoicePackManifest(
        versionCode = root.requiredInt("versionCode"),
        splitName = root.requiredString("splitName"),
        url = root.requiredString("url"),
        sha256 = root.requiredString("sha256").lowercase(),
        bytes = root.requiredLong("bytes")
    )
    require(manifest.versionCode == expectedVersionCode) {
        "El Cortex Voice Pack no corresponde a esta version de la app"
    }
    require(manifest.splitName == OnDemandVoiceFeatureLoader.MODULE_NAME) {
        "El paquete de voz publicado no es valido"
    }
    require(manifest.sha256.matches(Regex("[0-9a-f]{64}"))) {
        "La firma de integridad del paquete de voz no es valida"
    }
    require(manifest.bytes > 0L) { "El tamano publicado del paquete de voz no es valido" }

    val manifestUri = URI(manifestUrl)
    val packUri = URI(manifest.url)
    require(packUri.scheme == "https" && packUri.host == manifestUri.host) {
        "El origen del Cortex Voice Pack no es confiable"
    }
    return manifest
}

private fun JsonObject.requiredString(name: String): String =
    get(name)?.takeUnless { it.isJsonNull }?.asString?.trim()?.takeIf { it.isNotEmpty() }
        ?: throw IllegalArgumentException("Falta $name en el manifiesto del paquete de voz")

private fun JsonObject.requiredInt(name: String): Int =
    runCatching { get(name).asInt }.getOrElse {
        throw IllegalArgumentException("$name no es valido en el manifiesto del paquete de voz")
    }

private fun JsonObject.requiredLong(name: String): Long =
    runCatching { get(name).asLong }.getOrElse {
        throw IllegalArgumentException("$name no es valido en el manifiesto del paquete de voz")
    }

/** Downloads and adds the signed voice split to a directly distributed Cortex installation. */
@Singleton
class ExternalVoicePackInstaller @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .build()
    private val _state = MutableStateFlow(
        VoiceFeatureInstallState(installed = isVoiceSplitInstalled(context))
    )
    val state: StateFlow<VoiceFeatureInstallState> = _state.asStateFlow()

    fun requestInstall() {
        if (isVoiceSplitInstalled(context)) {
            _state.value = VoiceFeatureInstallState(installed = true, progress = 1f)
            return
        }
        if (_state.value.installing) return
        if (!context.packageManager.canRequestPackageInstalls()) {
            _state.value = VoiceFeatureInstallState(
                installed = false,
                requiresInstallPermission = true
            )
            return
        }

        _state.value = VoiceFeatureInstallState(installed = false, installing = true)
        scope.launch {
            runCatching {
                val manifest = fetchManifest()
                val apk = downloadPack(manifest)
                stageInstall(apk, manifest)
            }.onFailure { error ->
                _state.value = VoiceFeatureInstallState(
                    installed = false,
                    error = error.message ?: "No se pudo instalar Cortex Voice Pack"
                )
            }
        }
    }

    fun permissionIntent(): Intent = Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${context.packageName}")
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun resumeAfterPermission() {
        if (isVoiceSplitInstalled(context)) {
            _state.value = VoiceFeatureInstallState(installed = true, progress = 1f)
        } else if (context.packageManager.canRequestPackageInstalls()) {
            requestInstall()
        }
    }

    fun handleInstallResult(intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                _state.value = _state.value.copy(installing = true)
                pendingUserAction(intent)?.let { confirmation ->
                    confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(confirmation)
                } ?: failInstall("Android no pudo abrir la confirmacion del Cortex Voice Pack")
            }
            PackageInstaller.STATUS_SUCCESS -> {
                SplitCompat.install(context)
                _state.value = VoiceFeatureInstallState(installed = true, progress = 1f)
                packDirectory().deleteRecursively()
            }
            else -> failInstall(
                intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    ?: "Android rechazo la instalacion del Cortex Voice Pack"
            )
        }
    }

    private fun fetchManifest(): ExternalVoicePackManifest {
        val manifestUrl = BuildConfig.VOICE_PACK_MANIFEST_URL
        check(manifestUrl.startsWith("https://")) {
            "Esta build no tiene configurado Cortex Voice Pack"
        }
        val request = Request.Builder()
            .url(manifestUrl)
            .header("Accept", "application/json")
            .header("User-Agent", "Cortex-Android/${BuildConfig.VERSION_NAME}")
            .build()
        val json = client.newCall(request).execute().use { response ->
            check(response.isSuccessful) {
                "HTTP ${response.code} al consultar Cortex Voice Pack"
            }
            checkNotNull(response.body).string()
        }
        _state.value = _state.value.copy(progress = MANIFEST_PROGRESS)
        return parseExternalVoicePackManifest(json, manifestUrl, BuildConfig.VERSION_CODE)
    }

    private fun downloadPack(manifest: ExternalVoicePackManifest): File {
        val directory = packDirectory().apply { mkdirs() }
        val destination = File(directory, "${manifest.splitName}-${manifest.versionCode}.apk")
        if (destination.length() == manifest.bytes && destination.sha256() == manifest.sha256) {
            _state.value = _state.value.copy(progress = DOWNLOAD_COMPLETE_PROGRESS)
            return destination
        }
        if (destination.exists()) destination.delete()

        val partial = File(directory, "${destination.name}.part")
        if (partial.length() > manifest.bytes) partial.delete()
        val existingBytes = partial.length()
        val request = Request.Builder()
            .url(manifest.url)
            .header("User-Agent", "Cortex-Android/${BuildConfig.VERSION_NAME}")
            .apply { if (existingBytes > 0L) header("Range", "bytes=$existingBytes-") }
            .build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) {
                "HTTP ${response.code} al descargar Cortex Voice Pack"
            }
            val body = checkNotNull(response.body) { "Cortex Voice Pack devolvio una respuesta vacia" }
            val append = existingBytes > 0L && response.code == 206
            var downloaded = if (append) existingBytes else 0L
            body.byteStream().use { input ->
                FileOutputStream(partial, append).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        val fraction = (downloaded.toFloat() / manifest.bytes.toFloat())
                            .coerceIn(0f, 1f)
                        _state.value = _state.value.copy(
                            progress = MANIFEST_PROGRESS + fraction * DOWNLOAD_PROGRESS_WEIGHT
                        )
                    }
                }
            }
        }
        check(partial.length() == manifest.bytes) {
            "Cortex Voice Pack quedo incompleto (${partial.length()} de ${manifest.bytes} bytes)"
        }
        check(partial.sha256() == manifest.sha256) {
            partial.delete()
            "La verificacion de Cortex Voice Pack fallo; vuelve a descargarlo"
        }
        check(partial.renameTo(destination)) { "No se pudo preparar Cortex Voice Pack" }
        _state.value = _state.value.copy(progress = DOWNLOAD_COMPLETE_PROGRESS)
        return destination
    }

    private fun stageInstall(apk: File, manifest: ExternalVoicePackManifest) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_INHERIT_EXISTING).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            setInstallReason(PackageManager.INSTALL_REASON_USER)
            setOriginatingUri(Uri.parse(manifest.url))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("${manifest.splitName}.apk", 0L, apk.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
            val callback = Intent(context, VoicePackInstallReceiver::class.java).apply {
                action = ACTION_INSTALL_RESULT
                putExtra(EXTRA_SESSION_ID, sessionId)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                sessionId,
                callback,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            _state.value = _state.value.copy(progress = 1f)
            session.commit(pendingIntent.intentSender)
        }
    }

    private fun failInstall(message: String) {
        _state.value = VoiceFeatureInstallState(installed = false, error = message)
    }

    private fun packDirectory(): File = File(context.cacheDir, "cortex_voice_pack")

    @Suppress("DEPRECATION")
    private fun pendingUserAction(intent: Intent): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_INTENT)
        }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val MANIFEST_PROGRESS = 0.05f
        const val DOWNLOAD_PROGRESS_WEIGHT = 0.90f
        const val DOWNLOAD_COMPLETE_PROGRESS = 0.95f
        const val ACTION_INSTALL_RESULT = "com.aiagents.app.action.VOICE_PACK_INSTALL_RESULT"
        const val EXTRA_SESSION_ID = "session_id"
    }
}

internal fun isVoiceSplitInstalled(context: Context): Boolean = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0)
        .splitNames
        ?.contains(OnDemandVoiceFeatureLoader.MODULE_NAME) == true
}.getOrDefault(false)

@AndroidEntryPoint
class VoicePackInstallReceiver : BroadcastReceiver() {
    @Inject
    lateinit var installer: ExternalVoicePackInstaller

    override fun onReceive(context: Context, intent: Intent) {
        installer.handleInstallResult(intent)
    }
}
