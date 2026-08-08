package com.aiagents.app.domain.model

/**
 * Modelos locales para inferencia on-device usando MediaPipe.
 *
 * NOTA IMPORTANTE: los runtimes locales usan bundles .task, .bin o .litertlm.
 * Los modelos deben estar pre-convertidos para funcionar con MediaPipe.
 *
 * Repositorios verificados (Abril 2026):
 * - google/gemma-2b-it-tflite     → archivos .bin para CPU/GPU
 * - litert-community/Gemma3-1B-IT → archivos .task (formato MediaPipe)
 * - litert-community/gemma-3-270m-it → archivos .task (modelo más pequeño)
 * - litert-community/gemma-4-E2B-it-litert-lm → archivos .litertlm (Gemma 4)
 * - litert-community/gemma-4-E4B-it-litert-lm → archivos .litertlm (Gemma 4)
 */
data class LocalModel(
    val id: String,
    val name: String,
    val huggingFaceUrl: String,
    val fileName: String,
    val sizeBytes: Long,
    val description: String,
    val contextLength: Int = 4096,
    val isDownloaded: Boolean = false,
    val localPath: String? = null,
    val requiresLicense: Boolean = false,
    val requiresHFToken: Boolean = false
)

object RecommendedModels {
    val MODELS = listOf(
        // ── SIN TOKEN REQUERIDO ──────────────────────────────────────────────

        // FunctionGemma 270M — Especializado para tool calling
        // Repo: litert-community/FunctionGemma-270M
        LocalModel(
            id = "functiongemma-270m-q8",
            name = "FunctionGemma 270M (304 MB) ⭐ Tool Calling",
            huggingFaceUrl = "https://huggingface.co/litert-community/FunctionGemma-270M/resolve/main/functiongemma-270m-q8.task",
            fileName = "functiongemma-270m-q8.task",
            sizeBytes = 304_000_000L,
            description = "Especializado para tool calling (ejecutar comandos). Libre descarga, sin token requerido.",
            contextLength = 4096,
            requiresLicense = false,
            requiresHFToken = false
        ),
        // DeepSeek-R1-Distill-Qwen-1.5B — MIT license, libre descarga
        // Repo: litert-community/DeepSeek-R1-Distill-Qwen-1.5B
        LocalModel(
            id = "deepseek-r1-qwen-1.5b-q8",
            name = "DeepSeek-R1 1.5B Qwen (1.86 GB)",
            huggingFaceUrl = "https://huggingface.co/litert-community/DeepSeek-R1-Distill-Qwen-1.5B/resolve/main/DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv1280.task",
            fileName = "deepseek-r1-qwen-1.5b-q8.task",
            sizeBytes = 1_860_000_000L,
            description = "Modelo de razonamiento de DeepSeek. Libre descarga, sin token requerido. NO soporta tool calling.",
            contextLength = 4096,
            requiresLicense = false,
            requiresHFToken = false
        ),
        // Qwen2.5-1.5B-Instruct — Apache 2.0, libre descarga
        // Repo: litert-community/Qwen2.5-1.5B-Instruct
        LocalModel(
            id = "qwen2.5-1.5b-instruct-q8",
            name = "Qwen 2.5 1.5B Instruct (1.6 GB) ✅ Tool Calling",
            huggingFaceUrl = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
            fileName = "qwen2.5-1.5b-instruct-q8.task",
            sizeBytes = 1_600_000_000L,
            description = "Modelo de Alibaba, soporta tool calling. Libre descarga, sin token requerido.",
            contextLength = 4096,
            requiresLicense = false,
            requiresHFToken = false
        ),

        // ── REQUIEREN TOKEN HF + LICENCIA ────────────────────────────────────

        // Gemma 3 270M IT — el más pequeño disponible (304 MB)
        // Repo: litert-community/gemma-3-270m-it
        LocalModel(
            id = "gemma3-270m-it-q8",
            name = "Gemma 3 270M Instruct (304 MB) ⭐ ✅ Tool Calling",
            huggingFaceUrl = "https://huggingface.co/litert-community/gemma-3-270m-it/resolve/main/gemma3-270m-it-q8.task",
            fileName = "gemma3-270m-it-q8.task",
            sizeBytes = 304_000_000L,
            description = "El más pequeño y rápido de Google. Soporta tool calling. Requiere token HF + licencia Gemma.",
            contextLength = 4096,
            requiresLicense = true,
            requiresHFToken = true
        ),
        // Gemma 3 1B IT (555 MB)
        // Repo: litert-community/Gemma3-1B-IT
        LocalModel(
            id = "gemma3-1b-it-int4",
            name = "Gemma 3 1B Instruct (555 MB) ✅ Tool Calling",
            huggingFaceUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.task",
            fileName = "gemma3-1b-it-int4.task",
            sizeBytes = 555_000_000L,
            description = "Buen equilibrio calidad/tamaño de Google. Soporta tool calling. Requiere token HF + licencia Gemma.",
            contextLength = 4096,
            requiresLicense = true,
            requiresHFToken = true
        ),

        // ── GEMMA 4 SIN TOKEN ─────────────────────────────────────────

        // Gemma 4 E2B IT — Apache 2.0 y descarga pública (2.58 GB)
        // Repo: litert-community/gemma-4-E2B-it-litert-lm
        LocalModel(
            id = "gemma-4-e2b-it-litertlm",
            name = "Gemma 4 E2B Instruct (2.58 GB) 🆕 ✅ Tool Calling",
            huggingFaceUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
            fileName = "gemma-4-E2B-it.litertlm",
            sizeBytes = 2_580_000_000L,
            description = "Gemma 4 multimodal (texto + imagen). Máximo razonamiento on-device, 32K contexto. Descarga pública bajo Apache 2.0.",
            contextLength = 32768,
            requiresLicense = false,
            requiresHFToken = false
        ),
        // Gemma 4 E4B IT — Apache 2.0 y descarga pública (3.65 GB)
        // Repo: litert-community/gemma-4-E4B-it-litert-lm
        LocalModel(
            id = "gemma-4-e4b-it-litertlm",
            name = "Gemma 4 E4B Instruct (3.65 GB) 🆕 ✅ Tool Calling",
            huggingFaceUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm",
            fileName = "gemma-4-E4B-it.litertlm",
            sizeBytes = 3_650_000_000L,
            description = "Gemma 4 multimodal 4B, máxima calidad on-device. 32K contexto y descarga pública bajo Apache 2.0.",
            contextLength = 32768,
            requiresLicense = false,
            requiresHFToken = false
        ),

        // ── REQUIEREN TOKEN HF + LICENCIA ───────────────────────────────────

        // Gemma 3n E2B IT — modelo multimodal (3.14 GB)
        // Repo: google/gemma-3n-E2B-it-litert-preview
        LocalModel(
            id = "gemma-3n-e2b-it-int4",
            name = "Gemma 3n E2B Instruct (3.14 GB)",
            huggingFaceUrl = "https://huggingface.co/google/gemma-3n-E2B-it-litert-preview/resolve/main/gemma-3n-E2B-it-int4.task",
            fileName = "gemma-3n-E2B-it-int4.task",
            sizeBytes = 3_140_000_000L,
            description = "Gemma 3n multimodal (texto + imagen). Requiere token HF + licencia Gemma.",
            contextLength = 8192,
            requiresLicense = true,
            requiresHFToken = true
        ),
        // Gemma 3n E4B IT — modelo multimodal grande (4.41 GB)
        // Repo: google/gemma-3n-E4B-it-litert-preview
        LocalModel(
            id = "gemma-3n-e4b-it-int4",
            name = "Gemma 3n E4B Instruct (4.41 GB)",
            huggingFaceUrl = "https://huggingface.co/google/gemma-3n-E4B-it-litert-preview/resolve/main/gemma-3n-E4B-it-int4.task",
            fileName = "gemma-3n-E4B-it-int4.task",
            sizeBytes = 4_410_000_000L,
            description = "Gemma 3n multimodal 4B, máxima calidad on-device. Requiere token HF + licencia Gemma.",
            contextLength = 8192,
            requiresLicense = true,
            requiresHFToken = true
        ),
        // Gemma 2B IT CPU (1.35 GB)
        // Repo: google/gemma-2b-it-tflite
        LocalModel(
            id = "gemma-2b-it-cpu-int4",
            name = "Gemma 2B Instruct CPU (1.35 GB) ✅ Tool Calling",
            huggingFaceUrl = "https://huggingface.co/google/gemma-2b-it-tflite/resolve/main/gemma-2b-it-cpu-int4.bin",
            fileName = "gemma-2b-it-cpu-int4.bin",
            sizeBytes = 1_350_000_000L,
            description = "Gemma 2B para CPU, soporta tool calling. Requiere token HF + licencia.",
            contextLength = 4096,
            requiresLicense = true,
            requiresHFToken = true
        ),
        // Gemma 2B IT GPU (1.35 GB)
        // Repo: google/gemma-2b-it-tflite
        LocalModel(
            id = "gemma-2b-it-gpu-int4",
            name = "Gemma 2B Instruct GPU (1.35 GB) ✅ Tool Calling",
            huggingFaceUrl = "https://huggingface.co/google/gemma-2b-it-tflite/resolve/main/gemma-2b-it-gpu-int4.bin",
            fileName = "gemma-2b-it-gpu-int4.bin",
            sizeBytes = 1_350_000_000L,
            description = "Gemma 2B para GPU, soporta tool calling. Más rápida en Pixel y Snapdragon. Requiere token HF + licencia.",
            contextLength = 4096,
            requiresLicense = true,
            requiresHFToken = true
        ),

        // ── IMPORTACIÓN MANUAL ───────────────────────────────────────────────
        LocalModel(
            id = "custom-model",
            name = "Importar modelo propio",
            huggingFaceUrl = "",
            fileName = "custom_model.task",
            sizeBytes = 0L,
            description = "Importa un bundle .task, .bin o .litertlm descargado manualmente.",
            contextLength = 4096,
            requiresLicense = false,
            requiresHFToken = false
        )
    )

    const val HELP_URL_HF_TOKEN = "https://huggingface.co/settings/tokens"
    const val HELP_URL_GEMMA_LICENSE = "https://huggingface.co/google/gemma-3-1b-it"
    const val HELP_URL_MEDIAPIPE_MODELS = "https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference#models"
    const val HELP_URL_GALLERY_APP = "https://github.com/google-ai-edge/gallery"
    const val HELP_URL_LITERT_COMMUNITY = "https://huggingface.co/litert-community"
}

/**
 * Información sobre cómo obtener modelos manualmente
 */
object ModelDownloadHelp {
    const val TITLE = "¿Cómo descargar modelos?"

    const val INSTRUCTIONS = """
    ━━━ PASO 1: Token de Hugging Face ━━━
    1. Ve a: huggingface.co/settings/tokens
    2. Crea un token de tipo "Read"
    3. Cópialo y pégalo en el ícono de llave (🔑) de esta pantalla

    ━━━ PASO 2: Aceptar licencia de Gemma ━━━
    1. Ve a: huggingface.co/google/gemma-3-1b-it
    2. Inicia sesión en Hugging Face
    3. Acepta los términos de la licencia
    (Aplica a toda la familia Gemma)

    ━━━ PASO 3: Descargar ━━━
    Con token configurado y licencia aceptada,
    presiona "Descargar" en cualquier modelo.

    ✅ Recomendado para empezar:
       Gemma 3 270M (304 MB) - el más rápido
       Gemma 3 1B (555 MB) - mejor calidad

    ━━━ ALTERNATIVA sin token ━━━
    1. Descarga "Google AI Edge Gallery" del Play Store
    2. Descarga un modelo desde esa app
    3. Transfiere el archivo .task, .bin o .litertlm a tu teléfono
    4. Usa el botón "Importar" en esta pantalla

    NOTA: Necesitas WiFi y espacio libre (300MB-1.4GB).
    """
}
