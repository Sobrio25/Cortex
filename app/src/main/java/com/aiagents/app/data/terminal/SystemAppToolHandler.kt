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
    val requiresSettingsPermission: Boolean = false,
    /** True when the handler only opened Android UI and the user still controls the final action. */
    val requiresUserInteraction: Boolean = false,
    val interactionReason: String? = null
)

@Singleton
class SystemAppToolHandler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val assistantActionCoordinator: AssistantActionCoordinator
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
            "open_url", "map_search", "navigate", "dial_phone", "call_phone",
            "prepare_whatsapp_message", "share_text",
            "open_app_settings",
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
- "create_keep_note": Prepara una nota en Keep para revisión. Params: content (obligatorio), title (opcional). Nunca la guarda silenciosamente.
- "open_spotify": Abre Spotify.
- "play_spotify": Prepara una búsqueda en Spotify. Params: query (obligatorio). Muestra selector antes de entregar la consulta.
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
- "get_app_info": Info detallada y capacidades verificadas por intents resolubles. Params: app_name o package_name. Los permisos solicitados por una app no se consideran capacidades controlables.
- "query_capable_apps": Busca apps que resuelven una acción segura. Params: intent_type ("share_text"|"open_url"|"open_file"|"edit"|"map_search"|"navigate"|"dial"; también acepta aliases "send", "share" y "view"), mime_type opcional y uri opcional. No acepta acciones ni esquemas arbitrarios.
- "open_url": Abre un enlace http/https. Params: url. Siempre muestra selector antes de entregar el enlace a otra app.
- "map_search": Busca un lugar. Params: query. Siempre muestra selector de mapas.
- "navigate": Prepara navegación. Params: destination, mode opcional ("driving"|"walking"|"bicycling"). Siempre muestra selector de mapas.
- "dial_phone": Prepara un número en el marcador. Params: phone_number. Usa ACTION_DIAL: nunca inicia la llamada.
- "call_phone": Llama directamente. Params: contact o phone_number. Requiere permisos de contactos/teléfono; si hay varias coincidencias muestra una tarjeta para elegir.
- "prepare_whatsapp_message": Prepara una tarjeta de WhatsApp. Params: contact y message. La confirmación abre el chat con el texto listo; WhatsApp conserva el envío final.
- "share_text": Comparte texto. Params: text y title opcional. Siempre abre el selector de Android; nunca envía silenciosamente.
- "open_app_settings": Abre los ajustes Android de una app. Params: package_name o app_name.
- "uninstall_app": Solicita desinstalar una app. Params: package_name o app_name. Solo abre el diálogo Android; el usuario debe confirmar.""",
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
        if (arguments.length > SafeAppIntentPolicy.MAX_ARGUMENTS_LENGTH) {
            return SystemAppToolResult(
                toolCallId,
                toolName,
                false,
                "Argumentos demasiado grandes. Máximo: ${SafeAppIntentPolicy.MAX_ARGUMENTS_LENGTH} caracteres."
            )
        }
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
                "open_url" -> openUrl(toolCallId, params)
                "map_search" -> mapSearch(toolCallId, params)
                "navigate" -> navigate(toolCallId, params)
                "dial_phone" -> dialPhone(toolCallId, params)
                "call_phone" -> callPhone(toolCallId, params)
                "prepare_whatsapp_message" -> prepareWhatsAppMessage(toolCallId, params)
                "share_text" -> shareText(toolCallId, params)
                "open_app_settings" -> openAppSettings(toolCallId, params)
                "take_photo" -> SystemAppToolResult(
                    toolCallId = toolCallId,
                    toolName = TOOL_NAME,
                    success = true,
                    content = "Solicitud de cámara iniciada. Esperando que el usuario tome la foto...",
                    requiresUserInteraction = true,
                    interactionReason = "La cámara requiere que el usuario capture o cancele la foto."
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
            // Arguments may contain note/share text or a phone number; never write them to logs.
            Log.e(TAG, "Error executing device_control", e)
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

        if (packageName != null && !SafeAppIntentPolicy.isValidPackageName(packageName)) {
            return SystemAppToolResult(toolCallId, TOOL_NAME, false,
                "'package_name' no tiene un formato Android válido o excede ${SafeAppIntentPolicy.MAX_PACKAGE_NAME_LENGTH} caracteres.")
        }
        if (appName != null && !SafeAppIntentPolicy.isValidAppName(appName)) {
            return SystemAppToolResult(toolCallId, TOOL_NAME, false,
                "'app_name' debe tener entre 1 y ${SafeAppIntentPolicy.MAX_APP_NAME_LENGTH} caracteres sin controles.")
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
        if (!SafeAppIntentPolicy.isValidPackageName(packageName)) {
            return SystemAppToolResult(toolCallId, TOOL_NAME, false, "Nombre de paquete Android no válido.")
        }
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

        if (!SafeAppIntentPolicy.isValidShareText(content)) {
            return SystemAppToolResult(
                toolCallId,
                TOOL_NAME,
                false,
                "El contenido debe tener entre 1 y ${SafeAppIntentPolicy.MAX_SHARE_TEXT_LENGTH} caracteres y no incluir controles binarios."
            )
        }

        if (!isAppInstalled(KEEP_PACKAGE)) {
            return SystemAppToolResult(toolCallId, TOOL_NAME, false,
                "Google Keep no está instalado en este dispositivo.")
        }

        val title = params.get("title")?.asString

        if (title != null && !SafeAppIntentPolicy.isValidShareTitle(title)) {
            return SystemAppToolResult(
                toolCallId,
                TOOL_NAME,
                false,
                "El título no puede superar ${SafeAppIntentPolicy.MAX_SHARE_TITLE_LENGTH} caracteres ni incluir caracteres de control."
            )
        }

        // Do not invoke Keep's direct CREATE_NOTE action: it can persist model-provided data
        // without a review step. ACTION_SEND through Android's chooser keeps the user in control.
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            `package` = KEEP_PACKAGE
            putExtra(Intent.EXTRA_TEXT, content)
            if (title != null) putExtra(Intent.EXTRA_SUBJECT, title)
        }
        return openChooser(
            toolCallId = toolCallId,
            intent = intent,
            chooserTitle = "Revisar nota en Google Keep",
            successMessage = "Google Keep fue preparado mediante el selector de Android. La nota no se guardó silenciosamente; el usuario debe revisar y confirmar.",
            interactionReason = "El contenido solo se entrega después de una elección explícita del usuario."
        )
    }

    private fun openSpotify(toolCallId: String): SystemAppToolResult {
        return launchByPackage(toolCallId, SPOTIFY_PACKAGE, "Spotify")
    }

    private fun playSpotify(toolCallId: String, params: JsonObject): SystemAppToolResult {
        val query = params.get("query")?.asString
            ?: return SystemAppToolResult(toolCallId, TOOL_NAME, false,
                "Parámetro 'query' requerido.")

        if (!SafeAppIntentPolicy.isValidMapQuery(query)) {
            return SystemAppToolResult(
                toolCallId,
                TOOL_NAME,
                false,
                "La búsqueda debe tener entre 1 y ${SafeAppIntentPolicy.MAX_MAP_QUERY_LENGTH} caracteres y no incluir caracteres de control."
            )
        }

        val encodedQuery = Uri.encode(query)
        val spotifyUri = "spotify:search:$encodedQuery"

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(spotifyUri)).apply {
            `package` = SPOTIFY_PACKAGE
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (intent.resolveActivity(context.packageManager) != null) {
            return openChooser(
                toolCallId = toolCallId,
                intent = intent,
                chooserTitle = "Abrir búsqueda en Spotify",
                successMessage = "Spotify fue preparado mediante el selector de Android; el usuario debe confirmar que quiere abrir la búsqueda.",
                interactionReason = "La consulta no se entrega silenciosamente a Spotify."
            )
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

    // ─── SAFE EXTERNAL INTENTS ─────────────────────────────

    private fun openUrl(toolCallId: String, params: JsonObject): SystemAppToolResult {
        val url = params.get("url")?.asString
            ?: return SystemAppToolResult(toolCallId, TOOL_NAME, false, "Parámetro 'url' requerido.")
        if (!SafeAppIntentPolicy.isValidWebUrl(url)) {
            return SystemAppToolResult(
                toolCallId,
                TOOL_NAME,
                false,
                "URL no permitida. Usa una URL http/https válida, sin credenciales y de máximo ${SafeAppIntentPolicy.MAX_URL_LENGTH} caracteres."
            )
        }

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        return openChooser(
            toolCallId = toolCallId,
            intent = intent,
            chooserTitle = "Abrir enlace con…",
            successMessage = "Selector abierto para revisar y abrir el enlace. Ninguna app recibió el enlace silenciosamente.",
            interactionReason = "El usuario debe elegir una app antes de abrir el enlace."
        )
    }

    private fun mapSearch(toolCallId: String, params: JsonObject): SystemAppToolResult {
        val query = params.get("query")?.asString
            ?: return SystemAppToolResult(toolCallId, TOOL_NAME, false, "Parámetro 'query' requerido.")
        if (!SafeAppIntentPolicy.isValidMapQuery(query)) {
            return SystemAppToolResult(
                toolCallId,
                TOOL_NAME,
                false,
                "La búsqueda debe tener entre 1 y ${SafeAppIntentPolicy.MAX_MAP_QUERY_LENGTH} caracteres y no incluir caracteres de control."
            )
        }

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(query)}"))
        return openChooser(
            toolCallId = toolCallId,
            intent = intent,
            chooserTitle = "Buscar lugar con…",
            successMessage = "Selector de mapas abierto con la búsqueda preparada. El usuario debe elegir la app.",
            interactionReason = "El destino solo se entrega a la app que el usuario elija."
        )
    }

    private fun navigate(toolCallId: String, params: JsonObject): SystemAppToolResult {
        val destination = params.get("destination")?.asString
            ?: return SystemAppToolResult(toolCallId, TOOL_NAME, false, "Parámetro 'destination' requerido.")
        if (!SafeAppIntentPolicy.isValidMapQuery(destination)) {
            return SystemAppToolResult(
                toolCallId,
                TOOL_NAME,
                false,
                "El destino debe tener entre 1 y ${SafeAppIntentPolicy.MAX_MAP_QUERY_LENGTH} caracteres y no incluir caracteres de control."
            )
        }

        val mode = when (params.get("mode")?.asString?.lowercase() ?: "driving") {
            "driving" -> "d"
            "walking" -> "w"
            "bicycling" -> "b"
            else -> return SystemAppToolResult(
                toolCallId,
                TOOL_NAME,
                false,
                "Modo no permitido. Usa: driving, walking o bicycling."
            )
        }
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("google.navigation:q=${Uri.encode(destination)}&mode=$mode")
        )
        return openChooser(
            toolCallId = toolCallId,
            intent = intent,
            chooserTitle = "Preparar navegación con…",
            successMessage = "Selector de navegación abierto con el destino preparado. El usuario debe elegir la app y revisar la ruta.",
            interactionReason = "La navegación no se inicia silenciosamente."
        )
    }

    private fun dialPhone(toolCallId: String, params: JsonObject): SystemAppToolResult {
        val rawNumber = params.get("phone_number")?.asString
            ?: return SystemAppToolResult(toolCallId, TOOL_NAME, false, "Parámetro 'phone_number' requerido.")
        val phoneNumber = SafeAppIntentPolicy.normalizePhoneNumber(rawNumber)
            ?: return SystemAppToolResult(
                toolCallId,
                TOOL_NAME,
                false,
                "Número no válido. Se permiten dígitos, espacios, paréntesis, punto, guion y un '+' inicial; no se permiten códigos USSD."
            )

        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            SystemAppToolResult(
                toolCallId = toolCallId,
                toolName = TOOL_NAME,
                success = true,
                content = "Marcador abierto con el número preparado. No se realizó ninguna llamada; el usuario debe iniciarla manualmente.",
                requiresUserInteraction = true,
                interactionReason = "ACTION_DIAL requiere que el usuario pulse el botón de llamada."
            )
        } catch (_: Exception) {
            SystemAppToolResult(toolCallId, TOOL_NAME, false, "No hay una app visible que pueda abrir el marcador.")
        }
    }

    private fun callPhone(toolCallId: String, params: JsonObject): SystemAppToolResult {
        val result = assistantActionCoordinator.callPhone(
            contactQuery = params.get("contact")?.asString,
            rawNumber = params.get("phone_number")?.asString
        )
        return SystemAppToolResult(
            toolCallId = toolCallId,
            toolName = TOOL_NAME,
            success = result.success,
            content = result.message,
            requiresUserInteraction = result.success && result.message.contains("elige", ignoreCase = true),
            interactionReason = result.message.takeIf { result.success }
        )
    }

    private fun prepareWhatsAppMessage(
        toolCallId: String,
        params: JsonObject
    ): SystemAppToolResult {
        val contact = params.get("contact")?.asString
            ?: return SystemAppToolResult(toolCallId, TOOL_NAME, false, "Parámetro 'contact' requerido.")
        val message = params.get("message")?.asString
            ?: return SystemAppToolResult(toolCallId, TOOL_NAME, false, "Parámetro 'message' requerido.")
        val result = assistantActionCoordinator.prepareWhatsApp(contact, message)
        return SystemAppToolResult(
            toolCallId = toolCallId,
            toolName = TOOL_NAME,
            success = result.success,
            content = result.message,
            requiresUserInteraction = result.success,
            interactionReason = if (result.success) {
                "El usuario debe confirmar el contacto y el mensaje en la tarjeta del asistente."
            } else {
                null
            }
        )
    }

    private fun shareText(toolCallId: String, params: JsonObject): SystemAppToolResult {
        val sharedText = params.get("text")?.asString
            ?: return SystemAppToolResult(toolCallId, TOOL_NAME, false, "Parámetro 'text' requerido.")
        if (!SafeAppIntentPolicy.isValidShareText(sharedText)) {
            return SystemAppToolResult(
                toolCallId,
                TOOL_NAME,
                false,
                "El texto debe tener entre 1 y ${SafeAppIntentPolicy.MAX_SHARE_TEXT_LENGTH} caracteres y no incluir controles binarios."
            )
        }

        val title = params.get("title")?.asString
        if (title != null && !SafeAppIntentPolicy.isValidShareTitle(title)) {
            return SystemAppToolResult(
                toolCallId,
                TOOL_NAME,
                false,
                "El título no puede superar ${SafeAppIntentPolicy.MAX_SHARE_TITLE_LENGTH} caracteres ni incluir caracteres de control."
            )
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, sharedText)
            if (!title.isNullOrBlank()) putExtra(Intent.EXTRA_SUBJECT, title)
        }
        return openChooser(
            toolCallId = toolCallId,
            intent = intent,
            chooserTitle = title?.takeIf { it.isNotBlank() } ?: "Compartir texto con…",
            successMessage = "Selector de Android abierto. El texto no se envió: el usuario debe elegir destino y confirmar dentro de la app seleccionada.",
            interactionReason = "Compartir datos siempre requiere una elección explícita del usuario."
        )
    }

    private fun openAppSettings(toolCallId: String, params: JsonObject): SystemAppToolResult {
        val packageNameResult = resolveRequestedPackage(params)
        val packageName = packageNameResult.first
            ?: return SystemAppToolResult(toolCallId, TOOL_NAME, false, packageNameResult.second)
        if (!isAppInstalled(packageName)) {
            return SystemAppToolResult(toolCallId, TOOL_NAME, false, "La app '$packageName' no está instalada o no es visible para Android.")
        }

        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            SystemAppToolResult(
                toolCallId = toolCallId,
                toolName = TOOL_NAME,
                success = true,
                content = "Ajustes Android de '$packageName' abiertos. Cualquier cambio requiere interacción del usuario.",
                requiresUserInteraction = true,
                interactionReason = "El handler solo abre la pantalla oficial de ajustes."
            )
        } catch (_: Exception) {
            SystemAppToolResult(toolCallId, TOOL_NAME, false, "No se pudieron abrir los ajustes de '$packageName'.")
        }
    }

    private fun openChooser(
        toolCallId: String,
        intent: Intent,
        chooserTitle: String,
        successMessage: String,
        interactionReason: String
    ): SystemAppToolResult {
        return try {
            val chooser = Intent.createChooser(intent, chooserTitle).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            SystemAppToolResult(
                toolCallId = toolCallId,
                toolName = TOOL_NAME,
                success = true,
                content = successMessage,
                requiresUserInteraction = true,
                interactionReason = interactionReason
            )
        } catch (_: Exception) {
            SystemAppToolResult(toolCallId, TOOL_NAME, false, "No hay una app visible que pueda realizar esta acción.")
        }
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
        if (settingValue !in setOf("on", "off", "toggle")) {
            return SystemAppToolResult(toolCallId, TOOL_NAME, false,
                "Valor no permitido. Usa: on, off o toggle.")
        }

        settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(settingsIntent)

        return SystemAppToolResult(
            toolCallId = toolCallId,
            toolName = TOOL_NAME,
            success = true,
            content = "Se abrió la configuración de '$settingName' para que el usuario pueda ${
                when (settingValue) {
                    "on" -> "activarlo"
                    "off" -> "desactivarlo"
                    else -> "cambiarlo"
                }
            }. El agente no cambió el ajuste.",
            requiresUserInteraction = true,
            interactionReason = "Los cambios se realizan en la UI oficial de Ajustes."
        )
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
            else -> return SystemAppToolResult(
                toolCallId,
                TOOL_NAME,
                false,
                "Página de ajustes no permitida: '$page'."
            )
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)

        return SystemAppToolResult(
            toolCallId = toolCallId,
            toolName = TOOL_NAME,
            success = true,
            content = "Ajustes de '$page' abiertos. Cualquier cambio requiere interacción del usuario.",
            requiresUserInteraction = true,
            interactionReason = "El handler solo abrió la UI oficial de Ajustes."
        )
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
            else -> return SystemAppToolResult(toolCallId, TOOL_NAME, false,
                "Stream no permitido. Usa: music, ring, notification o alarm.")
        }

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(streamType)
        val targetVolume = (level.coerceIn(0, 100) * maxVolume / 100)

        audioManager.setStreamVolume(streamType, targetVolume, AudioManager.FLAG_SHOW_UI)

        val safeLevel = level.coerceIn(0, 100)
        return SystemAppToolResult(toolCallId, TOOL_NAME, true,
            "Volumen de $streamName ajustado a $safeLevel% ($targetVolume/$maxVolume).")
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
                requiresSettingsPermission = true,
                requiresUserInteraction = true,
                interactionReason = "El permiso WRITE_SETTINGS solo puede concederlo el usuario."
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
            "Brillo de pantalla ajustado a ${level.coerceIn(0, 100)}%.")
    }

    private fun toggleFlashlight(toolCallId: String, params: JsonObject): SystemAppToolResult {
        val state = params.get("state")?.asString
            ?: return SystemAppToolResult(toolCallId, TOOL_NAME, false,
                "Parámetro 'state' requerido ('on' o 'off').")
        if (state !in setOf("on", "off")) {
            return SystemAppToolResult(toolCallId, TOOL_NAME, false,
                "Estado no permitido. Usa 'on' o 'off'.")
        }

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

            if (filter != null && !SafeAppIntentPolicy.isValidFilter(filter)) {
                return SystemAppToolResult(
                    toolCallId,
                    TOOL_NAME,
                    false,
                    "El filtro no puede superar ${SafeAppIntentPolicy.MAX_FILTER_LENGTH} caracteres ni incluir caracteres de control."
                )
            }

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
                appendLine()
                append("Nota: Android solo permite ver apps declaradas o resolubles mediante intents; esta herramienta no solicita QUERY_ALL_PACKAGES.")
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
        val packageResult = resolveRequestedPackage(params)
        val targetPackage = packageResult.first
            ?: return SystemAppToolResult(toolCallId, TOOL_NAME, false, packageResult.second)

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

        return try {
            context.startActivity(intent)
            val displayName = if (appName != null && packageName == null) appName else targetPackage
            val systemWarning = if (isSystemApp) " (Nota: Es una app del sistema, podría no poder desinstalarse)" else ""
            SystemAppToolResult(
                toolCallId = toolCallId,
                toolName = TOOL_NAME,
                success = true,
                content = "Diálogo de desinstalación abierto para '$displayName'.$systemWarning No se eliminó nada: el usuario debe confirmar la desinstalación.",
                requiresUserInteraction = true,
                interactionReason = "Android muestra su diálogo de confirmación; el agente no puede aceptarlo."
            )
        } catch (_: Exception) {
            SystemAppToolResult(toolCallId, TOOL_NAME, false,
                "No se pudo abrir el diálogo de desinstalación para '$targetPackage'.")
        }
    }

    // ─── APP DISCOVERY ────────────────────────────────────

    private fun getAppInfo(toolCallId: String, params: JsonObject): SystemAppToolResult {
        val pm = context.packageManager
        val packageResult = resolveRequestedPackage(params)
        val packageName = packageResult.first
            ?: return SystemAppToolResult(toolCallId, TOOL_NAME, false, packageResult.second)

        val appInfo: ApplicationInfo
        val pkgInfo: PackageInfo
        try {
            appInfo = pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            pkgInfo = pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
        } catch (_: PackageManager.NameNotFoundException) {
            return SystemAppToolResult(toolCallId, TOOL_NAME, false, "No se encontró la app '$packageName'.")
        }

        val label = pm.getApplicationLabel(appInfo).toString()
        val version = pkgInfo.versionName ?: "?"
        val category = getCategoryLabel(appInfo)
        val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

        // Exported activities (entry points other apps can invoke)
        val exportedActivities = pkgInfo.activities?.filter { it.exported }?.take(15) ?: emptyList()

        // Capabilities are only reported after Android resolves an allowlisted intent to the app.
        // Requested permissions deliberately are not used: they do not imply external control.
        val verifiedCapabilities = probeAppIntents(packageName)

        val info = buildString {
            appendLine("$label ($packageName) v$version")
            appendLine("Categoría: $category | Sistema: $isSystem")

            if (verifiedCapabilities.isNotEmpty()) {
                appendLine("Acciones verificadas por intents resolubles:")
                verifiedCapabilities.forEach { appendLine("  - $it") }
            } else {
                appendLine("Acciones verificadas: ninguna dentro de la allowlist segura.")
            }

            if (exportedActivities.isNotEmpty()) {
                appendLine("Activities exportadas visibles (${exportedActivities.size}):")
                exportedActivities.forEach { act ->
                    val shortName = act.name.substringAfterLast('.')
                    appendLine("  - $shortName")
                }
            }

            append("La lista está limitada por la visibilidad de paquetes de Android; no se usa QUERY_ALL_PACKAGES.")
        }

        return SystemAppToolResult(toolCallId, TOOL_NAME, true, info.trim())
    }

    private fun queryCapableApps(toolCallId: String, params: JsonObject): SystemAppToolResult {
        val pm = context.packageManager
        val intentType = params.get("intent_type")?.asString?.lowercase() ?: "open_url"
        val mimeType = params.get("mime_type")?.asString
        val uri = params.get("uri")?.asString

        if (intentType !in SafeAppIntentPolicy.allowedCapabilityQueries) {
            return SystemAppToolResult(
                toolCallId,
                TOOL_NAME,
                false,
                "Tipo de intent no permitido. Usa: ${SafeAppIntentPolicy.allowedCapabilityQueries.sorted().joinToString(", ")}"
            )
        }
        if (mimeType != null && !SafeAppIntentPolicy.isValidMimeType(mimeType)) {
            return SystemAppToolResult(
                toolCallId,
                TOOL_NAME,
                false,
                "MIME type no válido o demasiado largo."
            )
        }
        if (uri != null && !SafeAppIntentPolicy.isValidCapabilityUri(intentType, uri)) {
            return SystemAppToolResult(
                toolCallId,
                TOOL_NAME,
                false,
                when (intentType) {
                    "view", "open_url" -> "Para esta consulta solo se aceptan URLs http/https válidas."
                    "open_file", "edit" -> "Para archivos solo se aceptan URI content://; file:// y esquemas arbitrarios están bloqueados."
                    else -> "El parámetro 'uri' no está permitido para '$intentType'."
                }
            )
        }

        val intent = when (intentType) {
            "send", "share", "share_text" -> Intent(Intent.ACTION_SEND).apply {
                type = mimeType ?: "text/plain"
                putExtra(Intent.EXTRA_TEXT, "capability-probe")
            }
            "view", "open_url" -> Intent(
                Intent.ACTION_VIEW,
                Uri.parse(uri ?: "https://example.com")
            )
            "open_file" -> Intent(Intent.ACTION_VIEW).apply {
                val contentUri = Uri.parse(uri ?: "content://com.aiagents.app.capability/document")
                setDataAndType(contentUri, mimeType ?: "*/*")
            }
            "edit" -> Intent(Intent.ACTION_EDIT).apply {
                if (uri != null) {
                    setDataAndType(Uri.parse(uri), mimeType ?: "*/*")
                } else {
                    type = mimeType ?: "*/*"
                }
            }
            "map_search" -> Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=place"))
            "navigate" -> Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=place&mode=d"))
            "dial" -> Intent(Intent.ACTION_DIAL, Uri.parse("tel:5551234567"))
            else -> error("Intent type was validated against the allowlist")
        }

        val resolvedApps = try {
            pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        } catch (_: Exception) {
            emptyList()
        }
        val distinctApps = resolvedApps.distinctBy { it.activityInfo.packageName }

        if (distinctApps.isEmpty()) {
            return SystemAppToolResult(
                toolCallId,
                TOOL_NAME,
                true,
                "No hay apps visibles que resuelvan '$intentType'${if (mimeType != null) " ($mimeType)" else ""}. No se ejecutó ninguna acción."
            )
        }

        val appList = distinctApps
            .take(30)
            .joinToString("\n") { resolved ->
                val appLabel = resolved.loadLabel(pm).toString()
                val pkg = resolved.activityInfo.packageName
                "- $appLabel ($pkg)"
            }

        val desc = buildString {
            append("Apps visibles que resuelven '$intentType'")
            if (mimeType != null) append(" [$mimeType]")
            appendLine(" (${distinctApps.size}):")
            appendLine(appList)
            append("Sondeo solamente: no se ejecutó ninguna acción. Resultado sujeto a la visibilidad de paquetes de Android.")
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

    private fun probeAppIntents(packageName: String): List<String> {
        val pm = context.packageManager
        return buildList {
            if (pm.getLaunchIntentForPackage(packageName) != null) {
                add("open_app — launcher oficial resoluble")
            }

            val probes = listOf(
                "open_url — ACTION_VIEW https; usa selector" to
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")),
                "share_text — ACTION_SEND text/plain; usa selector" to
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "capability-probe")
                    },
                "open_file — ACTION_VIEW content://" to
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(
                            Uri.parse("content://com.aiagents.app.capability/document"),
                            "application/pdf"
                        )
                    },
                "edit — ACTION_EDIT content://" to
                    Intent(Intent.ACTION_EDIT).apply {
                        setDataAndType(
                            Uri.parse("content://com.aiagents.app.capability/document"),
                            "text/plain"
                        )
                    },
                "map_search — ACTION_VIEW geo; usa selector" to
                    Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=place")),
                "navigate — ACTION_VIEW google.navigation; usa selector" to
                    Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=place&mode=d")),
                "dial_phone — ACTION_DIAL; nunca llama automáticamente" to
                    Intent(Intent.ACTION_DIAL, Uri.parse("tel:5551234567"))
            )
            probes.forEach { (label, baseIntent) ->
                val explicitIntent = Intent(baseIntent).apply { `package` = packageName }
                if (canResolve(pm, explicitIntent)) add(label)
            }
        }
    }

    private fun canResolve(pm: PackageManager, intent: Intent): Boolean {
        return try {
            pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    private fun resolveRequestedPackage(params: JsonObject): Pair<String?, String> {
        val packageName = params.get("package_name")?.asString
        if (packageName != null) {
            return if (SafeAppIntentPolicy.isValidPackageName(packageName)) {
                packageName to ""
            } else {
                null to "'package_name' no tiene un formato Android válido o excede ${SafeAppIntentPolicy.MAX_PACKAGE_NAME_LENGTH} caracteres."
            }
        }

        val appName = params.get("app_name")?.asString
            ?: return null to "Se requiere 'app_name' o 'package_name'."
        if (!SafeAppIntentPolicy.isValidAppName(appName)) {
            return null to "'app_name' debe tener entre 1 y ${SafeAppIntentPolicy.MAX_APP_NAME_LENGTH} caracteres sin controles."
        }
        return findPackageByName(appName)?.let { it to "" }
            ?: (null to "No se encontró una app visible llamada '$appName'.")
    }

    private fun findPackageByName(appName: String): String? {
        if (!SafeAppIntentPolicy.isValidAppName(appName)) return null
        val pm = context.packageManager
        val search = appName.lowercase()
        val installedApplications = try {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
        } catch (_: Exception) {
            emptyList()
        }
        return installedApplications
            .firstOrNull { pm.getApplicationLabel(it).toString().lowercase() == search }?.packageName
            ?: installedApplications
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
