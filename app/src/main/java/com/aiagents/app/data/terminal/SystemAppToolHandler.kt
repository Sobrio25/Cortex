package com.aiagents.app.data.terminal

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import com.google.gson.Gson
import com.google.gson.JsonObject
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class SystemAppToolResult(
    val toolCallId: String,
    val toolName: String,
    val success: Boolean,
    val content: String,
    val requiresSettingsPermission: Boolean = false
)

@Singleton
class SystemAppToolHandler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "SystemAppToolHandler"

        private const val KEEP_PACKAGE = "com.google.android.keep"
        private const val SPOTIFY_PACKAGE = "com.spotify.music"

        const val TOOL_NAME = "device_control"

        // All actions this skill supports
        val ALL_ACTIONS = setOf(
            "open_app", "open_google_keep", "create_keep_note",
            "open_spotify", "play_spotify", "control_playback",
            "take_photo", "change_setting", "open_settings",
            "get_device_info", "set_volume", "set_brightness",
            "toggle_flashlight",
            "list_installed_apps", "uninstall_app",
            "get_app_info", "query_capable_apps"
        )

        fun getToolDefinitionsJson(): List<Map<String, Any>> {
            return listOf(
                mapOf(
                    "type" to "function",
                    "function" to mapOf(
                        "name" to TOOL_NAME,
                        "description" to """Controla el dispositivo Android. Una sola herramienta para todas las acciones del dispositivo.

Acciones disponibles (campo "action"):

APPS:
- "open_app": Abre cualquier app. Params: app_name (nombre visible, ej: "WhatsApp", "Instagram", "Chrome") o package_name (ej: "com.whatsapp").
- "open_google_keep": Abre Google Keep.
- "create_keep_note": Crea nota en Keep. Params: content (obligatorio), title (opcional).
- "open_spotify": Abre Spotify.
- "play_spotify": Busca y reproduce en Spotify. Params: query (obligatorio), type ("track"|"artist"|"album"|"search").
- "control_playback": Controla reproducción multimedia. Params: playback_action ("play_pause"|"next"|"previous").

CAMARA:
- "take_photo": Toma foto con la cámara. Params: filename (opcional).

AJUSTES DEL DISPOSITIVO:
- "change_setting": Cambia un ajuste. Params: setting_name ("wifi"|"bluetooth"|"airplane_mode"|"auto_rotate"|"nfc"|"hotspot"|"battery_saver"), setting_value ("on"|"off"|"toggle"). Nota: abre la pantalla de ajustes correspondiente para que el usuario confirme.
- "open_settings": Abre una sección de ajustes. Params: settings_page ("wifi"|"bluetooth"|"display"|"sound"|"battery"|"storage"|"apps"|"location"|"security"|"accessibility"|"date"|"language"|"developer"|"about"|"notifications"|"data_usage"|"home").
- "set_volume": Cambia el volumen. Params: volume_level (0-100), stream ("music"|"ring"|"notification"|"alarm", default "music").
- "set_brightness": Cambia el brillo de pantalla. Params: brightness_level (0-100).
- "toggle_flashlight": Enciende/apaga la linterna. Params: state ("on"|"off").

INFO:
- "get_device_info": Obtiene información del dispositivo (modelo, versión Android, batería, almacenamiento).

GESTIÓN DE APPS:
- "list_installed_apps": Lista las apps instaladas. Params: filter (opcional, texto para filtrar), limit (opcional, default 50, max 100).
- "get_app_info": Info detallada de una app: categoría, permisos, activities exportadas, intents que acepta, acciones posibles. Params: app_name o package_name.
- "query_capable_apps": Busca apps que pueden manejar una acción. Params: intent_type ("send"|"view"|"edit"|"share"|"open_file"), mime_type (opcional, ej: "application/pdf", "image/*", "text/plain"), uri (opcional, ej: "https://example.com").
- "uninstall_app": Desinstala una app. Params: package_name (obligatorio) o app_name (nombre visible). Abre el diálogo de desinstalación para que el usuario confirme.""",
                        "parameters" to mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "action" to mapOf(
                                    "type" to "string",
                                    "description" to "La acción a realizar"
                                ),
                                "params" to mapOf(
                                    "type" to "object",
                                    "description" to "Parámetros específicos de la acción (ver descripción de cada acción)"
                                )
                            ),
                            "required" to listOf("action")
                        )
                    )
                )
            )
        }
    }

    private val gson = Gson()

    suspend fun executeTool(toolCallId: String, toolName: String, arguments: String): SystemAppToolResult {
        return try {
            val args = gson.fromJson(arguments, JsonObject::class.java) ?: JsonObject()
            val action = args.get("action")?.asString
                ?: return SystemAppToolResult(toolCallId, toolName, false, "Parámetro 'action' requerido.")
            val params = args.getAsJsonObject("params") ?: JsonObject()

            when (action) {
                "open_app" -> openApp(toolCallId, params)
                "open_google_keep" -> openGoogleKeep(toolCallId)
                "create_keep_note" -> createKeepNote(toolCallId, params)
                "open_spotify" -> openSpotify(toolCallId)
                "play_spotify" -> playSpotify(toolCallId, params)
                "control_playback" -> controlPlayback(toolCallId, params)
                "take_photo" -> SystemAppToolResult(
                    toolCallId = toolCallId,
                    toolName = TOOL_NAME,
                    success = true,
                    content = "Solicitud de cámara iniciada. Esperando que el usuario tome la foto..."
                )
                "change_setting" -> changeSetting(toolCallId, params)
                "open_settings" -> openSettings(toolCallId, params)
                "set_volume" -> setVolume(toolCallId, params)
                "set_brightness" -> setBrightness(toolCallId, params)
                "toggle_flashlight" -> toggleFlashlight(toolCallId, params)
                "get_device_info" -> getDeviceInfo(toolCallId)
                "list_installed_apps" -> listInstalledApps(toolCallId, params)
                "get_app_info" -> getAppInfo(toolCallId, params)
                "query_capable_apps" -> queryCapableApps(toolCallId, params)
                "uninstall_app" -> uninstallApp(toolCallId, params)
                else -> SystemAppToolResult(
                    toolCallId = toolCallId,
                    toolName = TOOL_NAME,
                    success = false,
                    content = "Acción desconocida: '$action'. Acciones válidas: ${ALL_ACTIONS.joinToString(", ")}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing device_control: $arguments", e)
            SystemAppToolResult(
                toolCallId = toolCallId,
                toolName = TOOL_NAME,
                success = false,
                content = "Error ejecutando device_control: ${e.message}"
            )
        }
    }

    // ─── APPS ─────────────────────────────────────────────

    private fun openApp(toolCallId: String, params: JsonObject): SystemAppToolResult {
        val packageName = params.get("package_name")?.asString
        val appName = params.get("app_name")?.asString

        if (packageName == null && appName == null) {
            return SystemAppToolResult(toolCallId, TOOL_NAME, false,
                "Se requiere 'app_name' o 'package_name'.")
        }

        // If package_name provided directly, try to launch it
        if (packageName != null) {
            return launchByPackage(toolCallId, packageName)
        }

        // Search by app name
        val pm = context.packageManager
        val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val searchName = appName!!.lowercase()

        // Exact match first, then contains
        val match = installedApps.firstOrNull {
            pm.getApplicationLabel(it).toString().lowercase() == searchName
        } ?: installedApps.firstOrNull {
            pm.getApplicationLabel(it).toString().lowercase().contains(searchName)
        }

        if (match != null) {
            return launchByPackage(toolCallId, match.packageName,
                pm.getApplicationLabel(match).toString())
        }

        // Try common package name patterns (name in Spanish & English → package)
        val commonApps = mapOf(
            // Mensajería
            "whatsapp" to "com.whatsapp",
            "telegram" to "org.telegram.messenger",
            "messenger" to "com.facebook.orca",
            "facebook messenger" to "com.facebook.orca",
            "snapchat" to "com.snapchat.android",
            "discord" to "com.discord",
            "slack" to "com.Slack",
            "teams" to "com.microsoft.teams",
            "microsoft teams" to "com.microsoft.teams",
            // Redes sociales
            "instagram" to "com.instagram.android",
            "facebook" to "com.facebook.katana",
            "twitter" to "com.twitter.android",
            "x" to "com.twitter.android",
            "tiktok" to "com.zhiliaoapp.musically",
            "reddit" to "com.reddit.frontpage",
            "pinterest" to "com.pinterest",
            "linkedin" to "com.linkedin.android",
            "tumblr" to "com.tumblr",
            // Streaming y entretenimiento
            "youtube" to "com.google.android.youtube",
            "netflix" to "com.netflix.mediaclient",
            "amazon prime" to "com.amazon.avod.thirdpartyclient",
            "prime video" to "com.amazon.avod.thirdpartyclient",
            "disney+" to "com.disney.disneyplus",
            "disney plus" to "com.disney.disneyplus",
            "hbo" to "com.hbo.hbonow",
            "hbo max" to "com.hbo.hbonow",
            "max" to "com.hbo.hbonow",
            "crunchyroll" to "com.crunchyroll.crunchyroid",
            "twitch" to "tv.twitch.android.app",
            "youtube music" to "com.google.android.apps.youtube.music",
            "shazam" to "com.shazam.android",
            // Videollamadas
            "zoom" to "us.zoom.videomeetings",
            "google meet" to "com.google.android.apps.meetings",
            "meet" to "com.google.android.apps.meetings",
            // Navegadores
            "chrome" to "com.android.chrome",
            "firefox" to "org.mozilla.firefox",
            "brave" to "com.brave.browser",
            "brave browser" to "com.brave.browser",
            "opera" to "com.opera.browser",
            "edge" to "com.microsoft.emmx",
            "microsoft edge" to "com.microsoft.emmx",
            // Google apps
            "gmail" to "com.google.android.gm",
            "maps" to "com.google.android.apps.maps",
            "google maps" to "com.google.android.apps.maps",
            "photos" to "com.google.android.apps.photos",
            "fotos" to "com.google.android.apps.photos",
            "google fotos" to "com.google.android.apps.photos",
            "google photos" to "com.google.android.apps.photos",
            "google drive" to "com.google.android.apps.docs",
            "drive" to "com.google.android.apps.docs",
            "files" to "com.google.android.apps.nbu.files",
            "archivos" to "com.google.android.apps.nbu.files",
            "contacts" to "com.google.android.contacts",
            "contactos" to "com.google.android.contacts",
            "phone" to "com.google.android.dialer",
            "telefono" to "com.google.android.dialer",
            "teléfono" to "com.google.android.dialer",
            "messages" to "com.google.android.apps.messaging",
            "mensajes" to "com.google.android.apps.messaging",
            "calculator" to "com.google.android.calculator",
            "calculadora" to "com.google.android.calculator",
            "clock" to "com.google.android.deskclock",
            "reloj" to "com.google.android.deskclock",
            "calendar" to "com.google.android.calendar",
            "calendario" to "com.google.android.calendar",
            "google calendar" to "com.google.android.calendar",
            "translate" to "com.google.android.apps.translate",
            "traductor" to "com.google.android.apps.translate",
            "google translate" to "com.google.android.apps.translate",
            // Productividad
            "outlook" to "com.microsoft.office.outlook",
            "word" to "com.microsoft.office.word",
            "excel" to "com.microsoft.office.excel",
            "notion" to "com.notion.id",
            "todoist" to "com.todoist",
            "obsidian" to "md.obsidian",
            // Transporte y delivery
            "uber" to "com.ubercab",
            "uber eats" to "com.ubercab.eats",
            "doordash" to "com.dd.doordash",
            "rappi" to "com.rappi",
            // Finanzas
            "paypal" to "com.paypal.android.p2pmobile",
            "mercado libre" to "com.mercadolibre",
            "mercadolibre" to "com.mercadolibre",
            "mercado pago" to "com.mercadopago.wallet",
            "mercadopago" to "com.mercadopago.wallet",
            // Fitness
            "myfitnesspal" to "com.myfitnesspal.android",
            "strava" to "com.strava",
            // Utilidades del sistema
            "camera" to "com.android.camera",
            "camara" to "com.android.camera",
            "cámara" to "com.android.camera",
            "play store" to "com.android.vending",
            "tienda" to "com.android.vending",
            "settings" to "com.android.settings",
            "ajustes" to "com.android.settings",
            "configuracion" to "com.android.settings",
            "configuración" to "com.android.settings"
        )

        val knownPackage = commonApps[searchName]
        if (knownPackage != null) {
            return launchByPackage(toolCallId, knownPackage, appName)
        }

        return SystemAppToolResult(toolCallId, TOOL_NAME, false,
            "No se encontró la app '$appName'. Intenta con el nombre exacto o el package_name.")
    }

    private fun launchByPackage(toolCallId: String, packageName: String, displayName: String? = null): SystemAppToolResult {
        val label = displayName ?: packageName
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (intent != null) {
            context.startActivity(intent)
            return SystemAppToolResult(toolCallId, TOOL_NAME, true,
                "App '$label' abierta exitosamente.")
        }

        if (!isAppInstalled(packageName)) {
            return SystemAppToolResult(toolCallId, TOOL_NAME, false,
                "La app '$label' no está instalada en este dispositivo.")
        }

        return SystemAppToolResult(toolCallId, TOOL_NAME, false,
            "No se pudo abrir '$label'. Intenta abrirla manualmente.")
    }

    private fun openGoogleKeep(toolCallId: String): SystemAppToolResult {
        return launchByPackage(toolCallId, KEEP_PACKAGE, "Google Keep")
    }

    private fun createKeepNote(toolCallId: String, params: JsonObject): SystemAppToolResult {
        val content = params.get("content")?.asString
            ?: return SystemAppToolResult(toolCallId, TOOL_NAME, false,
                "Parámetro 'content' requerido.")

        if (!isAppInstalled(KEEP_PACKAGE)) {
            return SystemAppToolResult(toolCallId, TOOL_NAME, false,
                "Google Keep no está instalado en este dispositivo.")
        }

        val title = params.get("title")?.asString

        val intent = Intent("com.google.android.keep.action.CREATE_NOTE").apply {
            putExtra(Intent.EXTRA_TEXT, content)
            if (title != null) putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)

        val successMsg = buildString {
            append("Nota creada en Google Keep.")
            if (title != null) append(" Título: \"$title\".")
            append(" Contenido: \"${content.take(80)}${if (content.length > 80) "..." else ""}\".")
        }
        return SystemAppToolResult(toolCallId, TOOL_NAME, true, successMsg)
    }

    private fun openSpotify(toolCallId: String): SystemAppToolResult {
        return launchByPackage(toolCallId, SPOTIFY_PACKAGE, "Spotify")
    }

    private fun playSpotify(toolCallId: String, params: JsonObject): SystemAppToolResult {
        val query = params.get("query")?.asString
            ?: return SystemAppToolResult(toolCallId, TOOL_NAME, false,
                "Parámetro 'query' requerido.")

        val encodedQuery = Uri.encode(query)
        val spotifyUri = "spotify:search:$encodedQuery"

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(spotifyUri)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            return SystemAppToolResult(toolCallId, TOOL_NAME, true,
                "Búsqueda de \"$query\" iniciada en Spotify.")
        }

        if (!isAppInstalled(SPOTIFY_PACKAGE)) {
            return SystemAppToolResult(toolCallId, TOOL_NAME, false,
                "Spotify no está instalado en este dispositivo.")
        }

        return SystemAppToolResult(toolCallId, TOOL_NAME, false,
            "No se pudo iniciar la búsqueda en Spotify.")
    }

    private fun controlPlayback(toolCallId: String, params: JsonObject): SystemAppToolResult {
        val action = params.get("playback_action")?.asString
            ?: return SystemAppToolResult(toolCallId, TOOL_NAME, false,
                "Parámetro 'playback_action' requerido (play_pause, next, previous).")

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val keyCode = when (action) {
            "play_pause" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            else -> return SystemAppToolResult(toolCallId, TOOL_NAME, false,
                "Acción desconocida: $action. Usa: play_pause, next, previous.")
        }

        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))

        val desc = when (action) {
            "play_pause" -> "reproducción pausada/reanudada"
            "next" -> "avanzado a la siguiente canción"
            "previous" -> "retrocedido a la canción anterior"
            else -> action
        }
        return SystemAppToolResult(toolCallId, TOOL_NAME, true, "Control de reproducción: $desc.")
    }

    // ─── DEVICE SETTINGS ──────────────────────────────────

    private fun changeSetting(toolCallId: String, params: JsonObject): SystemAppToolResult {
        val settingName = params.get("setting_name")?.asString
            ?: return SystemAppToolResult(toolCallId, TOOL_NAME, false,
                "Parámetro 'setting_name' requerido.")

        // For most settings, we open the corresponding settings page
        // so the user can confirm the change themselves
        val settingsIntent = when (settingName) {
            "wifi" -> Intent(Settings.ACTION_WIFI_SETTINGS)
            "bluetooth" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            "airplane_mode" -> Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
            "auto_rotate" -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
            "nfc" -> Intent(Settings.ACTION_NFC_SETTINGS)
            "hotspot" -> Intent("android.settings.TETHER_SETTINGS")
            "battery_saver" -> Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
            else -> return SystemAppToolResult(toolCallId, TOOL_NAME, false,
                "Ajuste desconocido: '$settingName'. Opciones: wifi, bluetooth, airplane_mode, auto_rotate, nfc, hotspot, battery_saver.")
        }

        val settingValue = params.get("setting_value")?.asString ?: "toggle"

        settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(settingsIntent)

        return SystemAppToolResult(toolCallId, TOOL_NAME, true,
            "Se abrió la configuración de '$settingName' para que el usuario pueda ${
                when (settingValue) {
                    "on" -> "activarlo"
                    "off" -> "desactivarlo"
                    else -> "cambiarlo"
                }
            }.")
    }

    private fun openSettings(toolCallId: String, params: JsonObject): SystemAppToolResult {
        val page = params.get("settings_page")?.asString ?: "main"

        val intent = when (page) {
            "main" -> Intent(Settings.ACTION_SETTINGS)
            "wifi" -> Intent(Settings.ACTION_WIFI_SETTINGS)
            "bluetooth" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            "display" -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
            "sound" -> Intent(Settings.ACTION_SOUND_SETTINGS)
            "battery" -> Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
            "storage" -> Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
            "apps" -> Intent(Settings.ACTION_APPLICATION_SETTINGS)
            "location" -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            "security" -> Intent(Settings.ACTION_SECURITY_SETTINGS)
            "accessibility" -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            "date" -> Intent(Settings.ACTION_DATE_SETTINGS)
            "language" -> Intent(Settings.ACTION_LOCALE_SETTINGS)
            "developer" -> Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            "about" -> Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)
            "notifications" -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
            "data_usage" -> Intent(Settings.ACTION_DATA_USAGE_SETTINGS)
            "home" -> Intent(Settings.ACTION_HOME_SETTINGS)
            else -> Intent(Settings.ACTION_SETTINGS)
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)

        return SystemAppToolResult(toolCallId, TOOL_NAME, true,
            "Ajustes de '$page' abiertos.")
    }

    private fun setVolume(toolCallId: String, params: JsonObject): SystemAppToolResult {
        val level = params.get("volume_level")?.asInt
            ?: return SystemAppToolResult(toolCallId, TOOL_NAME, false,
                "Parámetro 'volume_level' requerido (0-100).")

        val streamName = params.get("stream")?.asString ?: "music"
        val streamType = when (streamName) {
            "music" -> AudioManager.STREAM_MUSIC
            "ring" -> AudioManager.STREAM_RING
            "notification" -> AudioManager.STREAM_NOTIFICATION
            "alarm" -> AudioManager.STREAM_ALARM
            else -> AudioManager.STREAM_MUSIC
        }

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(streamType)
        val targetVolume = (level.coerceIn(0, 100) * maxVolume / 100)

        audioManager.setStreamVolume(streamType, targetVolume, AudioManager.FLAG_SHOW_UI)

        return SystemAppToolResult(toolCallId, TOOL_NAME, true,
            "Volumen de $streamName ajustado a $level% ($targetVolume/$maxVolume).")
    }

    private fun setBrightness(toolCallId: String, params: JsonObject): SystemAppToolResult {
        val level = params.get("brightness_level")?.asInt
            ?: return SystemAppToolResult(toolCallId, TOOL_NAME, false,
                "Parámetro 'brightness_level' requerido (0-100).")

        // Check if we can write system settings
        if (!Settings.System.canWrite(context)) {
            // Open the permission screen
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return SystemAppToolResult(
                toolCallId = toolCallId,
                toolName = TOOL_NAME,
                success = false,
                content = "Se necesita permiso para modificar ajustes del sistema. Se abrió la pantalla de permisos para que el usuario lo conceda.",
                requiresSettingsPermission = true
            )
        }

        val brightnessValue = (level.coerceIn(0, 100) * 255 / 100)

        // Disable auto-brightness first
        Settings.System.putInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        )
        Settings.System.putInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS,
            brightnessValue
        )

        return SystemAppToolResult(toolCallId, TOOL_NAME, true,
            "Brillo de pantalla ajustado a $level%.")
    }

    private fun toggleFlashlight(toolCallId: String, params: JsonObject): SystemAppToolResult {
        val state = params.get("state")?.asString
            ?: return SystemAppToolResult(toolCallId, TOOL_NAME, false,
                "Parámetro 'state' requerido ('on' o 'off').")

        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull()
                ?: return SystemAppToolResult(toolCallId, TOOL_NAME, false,
                    "No se encontró cámara con flash.")

            cameraManager.setTorchMode(cameraId, state == "on")

            SystemAppToolResult(toolCallId, TOOL_NAME, true,
                "Linterna ${if (state == "on") "encendida" else "apagada"}.")
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling flashlight", e)
            SystemAppToolResult(toolCallId, TOOL_NAME, false,
                "Error al controlar la linterna: ${e.message}")
        }
    }

    // ─── DEVICE INFO ──────────────────────────────────────

    private fun getDeviceInfo(toolCallId: String): SystemAppToolResult {
        val info = buildString {
            appendLine("Información del dispositivo:")
            appendLine("- Modelo: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("- Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("- Producto: ${Build.PRODUCT}")

            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val musicMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val musicCurrent = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            appendLine("- Volumen música: ${musicCurrent * 100 / musicMax}%")

            val ringMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
            val ringCurrent = audioManager.getStreamVolume(AudioManager.STREAM_RING)
            appendLine("- Volumen timbre: ${ringCurrent * 100 / ringMax}%")

            try {
                val brightness = Settings.System.getInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS
                )
                appendLine("- Brillo: ${brightness * 100 / 255}%")
            } catch (_: Exception) {}

            try {
                val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
                val battery = batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
                val isCharging = batteryManager.isCharging
                appendLine("- Batería: $battery%${if (isCharging) " (cargando)" else ""}")
            } catch (_: Exception) {}
        }.trim()

        return SystemAppToolResult(toolCallId, TOOL_NAME, true, info)
    }

    // ─── APP MANAGEMENT ───────────────────────────────────

    private fun listInstalledApps(toolCallId: String, params: JsonObject): SystemAppToolResult {
        return try {
            val pm = context.packageManager
            val filter = params.get("filter")?.asString?.lowercase()
            val limit = params.get("limit")?.asInt?.coerceIn(1, 100) ?: 50

            // Get all installed apps that can be launched
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { app ->
                    // Only show apps that have a launch intent
                    pm.getLaunchIntentForPackage(app.packageName) != null
                }
                .filter { app ->
                    filter == null || pm.getApplicationLabel(app).toString().lowercase().contains(filter)
                }
                .sortedBy { pm.getApplicationLabel(it).toString() }
                .take(limit)

            val appList = apps.joinToString("\n") { app ->
                val name = pm.getApplicationLabel(app).toString()
                val packageName = app.packageName
                "- $name ($packageName)"
            }

            val result = buildString {
                appendLine("Apps instaladas (${apps.size}${if (filter != null) " filtradas" else ""}):")
                if (appList.isNotBlank()) {
                    append(appList)
                } else {
                    append("No se encontraron apps${if (filter != null) " con el filtro '$filter'" else ""}.")
                }
            }

            SystemAppToolResult(toolCallId, TOOL_NAME, true, result.trim())
        } catch (e: Exception) {
            Log.e(TAG, "Error listing installed apps", e)
            SystemAppToolResult(toolCallId, TOOL_NAME, false,
                "Error al listar apps: ${e.message}")
        }
    }

    private fun uninstallApp(toolCallId: String, params: JsonObject): SystemAppToolResult {
        val packageName = params.get("package_name")?.asString
        val appName = params.get("app_name")?.asString

        if (packageName == null && appName == null) {
            return SystemAppToolResult(toolCallId, TOOL_NAME, false,
                "Se requiere 'app_name' o 'package_name'.")
        }

        // If app_name is provided, try to find the package
        val targetPackage = if (packageName != null) {
            packageName
        } else {
            // Search by app name
            val pm = context.packageManager
            val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val searchName = appName!!.lowercase()

            val match = installedApps.firstOrNull {
                pm.getApplicationLabel(it).toString().lowercase() == searchName
            } ?: installedApps.firstOrNull {
                pm.getApplicationLabel(it).toString().lowercase().contains(searchName)
            }

            if (match == null) {
                return SystemAppToolResult(toolCallId, TOOL_NAME, false,
                    "No se encontró la app '$appName'.")
            }
            match.packageName
        }

        // Check if app is installed
        if (!isAppInstalled(targetPackage)) {
            return SystemAppToolResult(toolCallId, TOOL_NAME, false,
                "La app '$targetPackage' no está instalada.")
        }

        // Cannot uninstall system apps or ourselves
        if (targetPackage == context.packageName) {
            return SystemAppToolResult(toolCallId, TOOL_NAME, false,
                "No puedo desinstalar esta app (yo mismo).")
        }

        // Check if it's a system app
        val pm = context.packageManager
        val isSystemApp = try {
            val appInfo = pm.getApplicationInfo(targetPackage, 0)
            (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
        } catch (e: Exception) {
            false
        }

        // Open uninstall dialog
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.parse("package:$targetPackage")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (intent.resolveActivity(pm) != null) {
            context.startActivity(intent)
            val displayName = if (appName != null && packageName == null) appName else targetPackage
            val systemWarning = if (isSystemApp) " (Nota: Es una app del sistema, podría no poder desinstalarse)" else ""
            return SystemAppToolResult(toolCallId, TOOL_NAME, true,
                "Diálogo de desinstalación abierto para '$displayName'.$systemWarning El usuario debe confirmar la desinstalación.")
        }

        return SystemAppToolResult(toolCallId, TOOL_NAME, false,
            "No se pudo abrir el diálogo de desinstalación para '$targetPackage'.")
    }

    // ─── APP DISCOVERY ────────────────────────────────────

    private fun getAppInfo(toolCallId: String, params: JsonObject): SystemAppToolResult {
        val pm = context.packageManager
        val packageName = params.get("package_name")?.asString
            ?: params.get("app_name")?.asString?.let { findPackageByName(it) }
            ?: return SystemAppToolResult(toolCallId, TOOL_NAME, false, "Provide 'app_name' or 'package_name'.")

        val appInfo: ApplicationInfo
        val pkgInfo: PackageInfo
        try {
            appInfo = pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            pkgInfo = pm.getPackageInfo(packageName,
                PackageManager.GET_ACTIVITIES or PackageManager.GET_PERMISSIONS or
                PackageManager.GET_PROVIDERS or PackageManager.GET_SERVICES)
        } catch (_: PackageManager.NameNotFoundException) {
            return SystemAppToolResult(toolCallId, TOOL_NAME, false, "App '$packageName' not found.")
        }

        val label = pm.getApplicationLabel(appInfo).toString()
        val version = pkgInfo.versionName ?: "?"
        val category = getCategoryLabel(appInfo)
        val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

        // Exported activities (entry points other apps can invoke)
        val exportedActivities = pkgInfo.activities?.filter { it.exported }?.take(15) ?: emptyList()

        // Permissions the app requests (hints at capabilities)
        val permissions = pkgInfo.requestedPermissions?.toList() ?: emptyList()
        val capabilityHints = deriveCapabilityHints(permissions)

        // Check what intents we can send to this app
        val supportedIntents = probeAppIntents(packageName)

        val info = buildString {
            appendLine("$label ($packageName) v$version")
            appendLine("Category: $category | System: $isSystem")

            if (capabilityHints.isNotEmpty()) {
                appendLine("Capabilities (from permissions): ${capabilityHints.joinToString(", ")}")
            }

            if (supportedIntents.isNotEmpty()) {
                appendLine("Supported intents: ${supportedIntents.joinToString(", ")}")
            }

            if (exportedActivities.isNotEmpty()) {
                appendLine("Exported activities (${exportedActivities.size}):")
                exportedActivities.forEach { act ->
                    val shortName = act.name.substringAfterLast('.')
                    appendLine("  - $shortName")
                }
            }

            // Known deep actions for popular apps
            val knownActions = getKnownAppActions(packageName)
            if (knownActions.isNotEmpty()) {
                appendLine("Known actions via Cortex:")
                knownActions.forEach { appendLine("  - $it") }
            }
        }

        return SystemAppToolResult(toolCallId, TOOL_NAME, true, info.trim())
    }

    private fun queryCapableApps(toolCallId: String, params: JsonObject): SystemAppToolResult {
        val pm = context.packageManager
        val intentType = params.get("intent_type")?.asString ?: "view"
        val mimeType = params.get("mime_type")?.asString
        val uri = params.get("uri")?.asString

        val intent = when (intentType.lowercase()) {
            "send", "share" -> Intent(Intent.ACTION_SEND)
            "view", "open_file" -> Intent(Intent.ACTION_VIEW)
            "edit" -> Intent(Intent.ACTION_EDIT)
            "pick" -> Intent(Intent.ACTION_PICK)
            "sendto" -> Intent(Intent.ACTION_SENDTO)
            else -> Intent(intentType) // allow raw intent action
        }

        if (mimeType != null) {
            intent.type = mimeType
        }
        if (uri != null) {
            intent.data = Uri.parse(uri)
            if (mimeType != null) {
                intent.setDataAndType(Uri.parse(uri), mimeType)
            }
        }

        // If no mime or uri, default to generic
        if (mimeType == null && uri == null) {
            intent.type = "*/*"
        }

        val resolvedApps = try {
            pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        } catch (_: Exception) {
            emptyList()
        }

        if (resolvedApps.isEmpty()) {
            return SystemAppToolResult(toolCallId, TOOL_NAME, true,
                "No apps found that handle $intentType${if (mimeType != null) " ($mimeType)" else ""}.")
        }

        val appList = resolvedApps
            .distinctBy { it.activityInfo.packageName }
            .take(30)
            .joinToString("\n") { resolved ->
                val appLabel = resolved.loadLabel(pm).toString()
                val pkg = resolved.activityInfo.packageName
                "- $appLabel ($pkg)"
            }

        val desc = buildString {
            append("Apps that handle $intentType")
            if (mimeType != null) append(" [$mimeType]")
            appendLine(" (${resolvedApps.distinctBy { it.activityInfo.packageName }.size}):")
            append(appList)
        }

        return SystemAppToolResult(toolCallId, TOOL_NAME, true, desc.trim())
    }

    @Suppress("DEPRECATION")
    private fun getCategoryLabel(appInfo: ApplicationInfo): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            when (appInfo.category) {
                ApplicationInfo.CATEGORY_GAME -> "Game"
                ApplicationInfo.CATEGORY_AUDIO -> "Audio"
                ApplicationInfo.CATEGORY_VIDEO -> "Video"
                ApplicationInfo.CATEGORY_IMAGE -> "Image"
                ApplicationInfo.CATEGORY_SOCIAL -> "Social"
                ApplicationInfo.CATEGORY_NEWS -> "News"
                ApplicationInfo.CATEGORY_MAPS -> "Maps"
                ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivity"
                ApplicationInfo.CATEGORY_ACCESSIBILITY -> "Accessibility"
                else -> "Other"
            }
        } else "Unknown"
    }

    private fun deriveCapabilityHints(permissions: List<String>): List<String> = buildList {
        val p = permissions.toSet()
        if (p.any { "CAMERA" in it }) add("camera")
        if (p.any { "RECORD_AUDIO" in it || "MICROPHONE" in it }) add("microphone")
        if (p.any { "ACCESS_FINE_LOCATION" in it || "ACCESS_COARSE_LOCATION" in it }) add("location")
        if (p.any { "READ_CONTACTS" in it || "WRITE_CONTACTS" in it }) add("contacts")
        if (p.any { "READ_CALENDAR" in it || "WRITE_CALENDAR" in it }) add("calendar")
        if (p.any { "SEND_SMS" in it || "READ_SMS" in it }) add("sms")
        if (p.any { "READ_CALL_LOG" in it || "CALL_PHONE" in it }) add("phone")
        if (p.any { "READ_EXTERNAL_STORAGE" in it || "WRITE_EXTERNAL_STORAGE" in it || "MANAGE_EXTERNAL_STORAGE" in it }) add("storage")
        if (p.any { "INTERNET" in it }) add("internet")
        if (p.any { "BLUETOOTH" in it }) add("bluetooth")
        if (p.any { "NFC" in it }) add("nfc")
        if (p.any { "BODY_SENSORS" in it }) add("sensors")
        if (p.any { "ACTIVITY_RECOGNITION" in it }) add("activity_recognition")
        if (p.any { "POST_NOTIFICATIONS" in it }) add("notifications")
    }

    private fun probeAppIntents(packageName: String): List<String> {
        val pm = context.packageManager
        val supported = mutableListOf<String>()
        val probes = listOf(
            "ACTION_SEND" to Intent(Intent.ACTION_SEND).apply { type = "*/*"; `package` = packageName },
            "ACTION_VIEW" to Intent(Intent.ACTION_VIEW).apply { data = Uri.parse("https://example.com"); `package` = packageName },
            "ACTION_SENDTO" to Intent(Intent.ACTION_SENDTO).apply { data = Uri.parse("mailto:"); `package` = packageName },
        )
        for ((label, intent) in probes) {
            try {
                val results = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                if (results.isNotEmpty()) supported.add(label)
            } catch (_: Exception) { }
        }
        return supported
    }

    private fun getKnownAppActions(packageName: String): List<String> = when (packageName) {
        "com.spotify.music" -> listOf("open_spotify", "play_spotify (query + type)", "control_playback (play_pause/next/previous)")
        "com.google.android.keep" -> listOf("open_google_keep", "create_keep_note (title + content)")
        "com.whatsapp" -> listOf("open_app, ACTION_SEND (share text/image to contact)")
        "com.google.android.apps.maps" -> listOf("open_app, ACTION_VIEW with geo: URI for navigation")
        "com.google.android.gm" -> listOf("open_app, ACTION_SENDTO with mailto: URI")
        "com.google.android.youtube" -> listOf("open_app, ACTION_VIEW with youtube URL")
        "com.android.chrome", "org.mozilla.firefox", "com.brave.browser" -> listOf("open_app, ACTION_VIEW with https: URL")
        "com.google.android.calendar" -> listOf("open_app, read/add events via calendar tools")
        "com.google.android.dialer" -> listOf("ACTION_DIAL with tel: URI")
        "com.google.android.apps.messaging" -> listOf("ACTION_SENDTO with sms: URI")
        "com.instagram.android" -> listOf("open_app, ACTION_SEND (share image)")
        "com.twitter.android" -> listOf("open_app, ACTION_SEND (share text)")
        "com.google.android.apps.photos" -> listOf("open_app, ACTION_VIEW (view image/video)")
        "com.ubercab" -> listOf("open_app")
        "com.mercadolibre", "com.mercadopago.wallet" -> listOf("open_app")
        else -> emptyList()
    }

    private fun findPackageByName(appName: String): String? {
        val pm = context.packageManager
        val search = appName.lowercase()
        return pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .firstOrNull { pm.getApplicationLabel(it).toString().lowercase() == search }?.packageName
            ?: pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .firstOrNull { pm.getApplicationLabel(it).toString().lowercase().contains(search) }?.packageName
    }

    // ─── UTILS ────────────────────────────────────────────

    private fun isAppInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            launchIntent != null
        } catch (e: Exception) {
            try {
                context.packageManager.getLaunchIntentForPackage(packageName) != null
            } catch (_: Exception) {
                false
            }
        }
    }
}
