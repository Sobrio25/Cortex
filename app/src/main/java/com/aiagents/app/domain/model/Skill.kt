package com.aiagents.app.domain.model

enum class SkillStatus {
    DRAFT,
    ACTIVE,
    ARCHIVED
}

enum class SkillOrigin {
    USER,
    AUTO,
    IMPORTED,
    BUILTIN
}

enum class SkillReviewStatus {
    PENDING,
    DRAFT_CREATED,
    CHANGES_APPLIED,
    SKIPPED,
    FAILED
}

data class Skill(
    val id: Long,
    val slug: String,
    val name: String,
    val description: String,
    val whenToUse: String,
    val instructions: String,
    val status: SkillStatus,
    val origin: SkillOrigin,
    val isImmutable: Boolean,
    val version: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val activatedAt: Long?,
    val archivedAt: Long?
)

data class SkillDraftInput(
    val name: String,
    val description: String,
    val whenToUse: String,
    val instructions: String
)

data class SkillReview(
    val id: Long,
    val status: SkillReviewStatus,
    val summary: String,
    val candidateSkillId: Long?,
    val messageCount: Int,
    val createdAt: Long,
    val completedAt: Long?
)

/**
 * Canonical built-in used by the agent runtime when skill creation is wired into
 * the central dispatcher. It is seeded by Room and cannot be edited or archived.
 */
object SkillCreatorBuiltin {
    const val SLUG = "skill-creator"
    const val NAME = "Skill Creator"
    const val DESCRIPTION = "Diseña skills reutilizables, seguras y fáciles de revisar."
    const val WHEN_TO_USE =
        "crear skill,crear habilidad,nueva skill,automatizar flujo,create skill,reusable workflow"
    const val VERSION = 2

    val instructions: String = """
        # Skill Creator

        Convierte una tarea repetible en una skill clara y limitada.

        ## Proceso
        1. Confirma el resultado esperado, las entradas necesarias y cuándo debe usarse.
        2. Reutiliza capacidades existentes antes de proponer herramientas nuevas.
        3. Define pasos verificables, casos de error y criterios de finalización.
        4. Aplica mínimo privilegio: no incluyas secretos, credenciales ni permisos innecesarios.
        5. Crea siempre la skill como DRAFT para que el usuario la revise antes de activarla.

        ## Campos requeridos
        - name: nombre breve y específico.
        - description: resultado que produce.
        - whenToUse: señales concretas para seleccionarla.
        - instructions: flujo completo, restricciones y validación.

        ## Límites
        - No inventes acceso a tools, apps, archivos o red.
        - No copies datos personales del chat dentro de la skill.
        - No actives una skill automática sin una acción explícita del usuario.
    """.trimIndent()
}

/** Built-in workflow for discovering and controlling installed Android apps through safe intents. */
object AndroidAppControlBuiltin {
    const val SLUG = "android-app-control"
    const val NAME = "Android App Control"
    const val DESCRIPTION = "Descubre apps instaladas y abre acciones Android mediante intents seguros y confirmables."
    const val WHEN_TO_USE =
        "abrir app,listar apps,controlar aplicación,compartir texto,navegar,mapa,marcar teléfono,app instalada,android app"
    const val VERSION = 1

    val instructions: String = """
        # Android App Control

        Usa `device_control` para descubrir y operar aplicaciones instaladas en el dispositivo Android.

        ## Flujo
        1. Si no conoces la app o su package, usa `list_installed_apps` con filtro.
        2. Usa `get_app_info` para ver únicamente intents realmente resolubles; los permisos solicitados por una app no significan que puedas controlarla.
        3. Para descubrir handlers usa `query_capable_apps` con tipos allowlisted.
        4. Ejecuta solo acciones soportadas: `open_app`, `open_url`, `map_search`, `navigate`, `dial_phone`, `call_phone`, `prepare_whatsapp_message`, `share_text` y `open_app_settings`.
        5. Explica al usuario cuándo Android abrirá un chooser o pantalla de confirmación.

        ## Seguridad
        - `dial_phone` abre ACTION_DIAL; nunca realiza una llamada directamente.
        - `call_phone` usa ACTION_CALL sólo después de obtener permisos y resolver una coincidencia inequívoca.
        - `prepare_whatsapp_message` crea un borrador confirmable; no afirma que WhatsApp lo envió.
        - `share_text` siempre abre el chooser y requiere la acción visible del usuario.
        - Desinstalar, compartir, crear notas o cambiar ajustes siempre conserva confirmación/UI del sistema.
        - No uses intents arbitrarios, Accessibility, `file://`, esquemas no allowlisted ni supongas capacidades por permisos.
        - Una skill no amplía permisos: si la tool o acción no está disponible, dilo con claridad.
    """.trimIndent()
}

/** Built-in workflow that makes the configured assistant use native weather cards consistently. */
object WeatherWidgetsBuiltin {
    const val SLUG = "weather-widgets"
    const val NAME = "Weather Widgets"
    const val DESCRIPTION =
        "Consulta clima real y activa tarjetas meteorológicas nativas. En modo asistente responde muy breve y sin emojis."
    const val WHEN_TO_USE =
        "clima,tiempo,temperatura,pronóstico,va a llover,lluvia,calidad del aire,weather,forecast,air quality"
    const val VERSION = 5

    val instructions: String = """
        # Weather Widgets

        Usa las tools meteorológicas para que la app renderice el resultado como un widget nativo.

        ## Flujo
        1. Usa `weather_current` para condiciones actuales o preguntas ambiguas sobre el clima.
        2. Usa `weather_forecast` para mañana, una fecha concreta, próximos días o probabilidad de lluvia.
           - Para hoy usa `day_offset: 0`; para mañana usa `day_offset: 1`. Devuelve solo ese día.
           - Para una fecha indicada usa `target_date` en formato `yyyy-MM-dd`. Devuelve solo esa fecha.
           - Usa `days` únicamente cuando el usuario pida explícitamente varios días.
        3. Usa `weather_air_quality` cuando el usuario pregunte por contaminación o calidad del aire.
        4. Si el usuario no indica un lugar, omite `location`, `lat` y `lon`: la tool usará privadamente la ubicación del dispositivo Android.
        5. Cuando la tool devuelva `WEATHER_DATA`, termina el turno sin escribir ninguna respuesta adicional: el widget es la respuesta completa.

        ## Contrato visual
        - El resultado de la tool ya contiene `WEATHER_DATA` para el widget bonito; no copies, edites ni vuelvas a imprimir ese marcador.
        - No escribas introducción, resumen, despedida ni texto debajo del widget.
        - No conviertas el resultado en una tabla Markdown ni en un bloque de código.
        - Muestra y pronuncia todas las temperaturas como enteros redondeados, sin decimales.
        - Si falla el permiso de ubicación, explica cómo concederlo o pide una ciudad.
        - Conserva la atribución mostrada por la tool a Open-Meteo y, para calidad del aire, a CAMS.

        ## Modo asistente
        - La tarjeta meteorológica es la respuesta completa.
        - La app construye el resumen hablado desde `WEATHER_DATA`; no uses el resumen visual como texto para TTS.
        - Toda salida destinada a voz debe ser una frase natural: di "grados Celsius", "máxima", "mínima" y "por ciento" completos.
        - No envíes al lector abreviaturas o separadores visuales como `°C`, `máx.`, `mín.`, `%`, `/` o `·`.
        - Si un error impide mostrarla y necesitas responder con texto, usa una sola línea muy breve y sin emojis.

        Las skills no amplían permisos ni acceso a red: usa únicamente las tools meteorológicas disponibles.
    """.trimIndent()
}
