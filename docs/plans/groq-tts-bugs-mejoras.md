# Plan: bugs, Groq TTS y mejoras inspiradas en proyectos open source

## Estado

- **Groq TTS: implementado** en esta iteración (ver `## Cambio realizado: Groq TTS`).
- **Auditoría de bugs: completada**; los hallazgos están en `## Bugs encontrados`.
- **Mejoras propuestas**: priorizadas en `## Plan de mejoras`, con aportes de
  OpenClaw, OpenCode, Kimi Code y Hermes en `## Mejoras de proyectos open source`.

## Bugs encontrados

Auditoría sobre `app/src/main` con verificación cruzada de handlers, scopes y estados.
Severidad: 🔴 crash / 🟠 UX roto / 🟡 rendimiento.

### 🔴 Críticos (crash)

1. **`data/speech/BaseSTTService.kt:63-71`** — El job de `startRecordingSession`
   usa `try/finally` sin `catch` ni `CoroutineExceptionHandler` en `serviceScope`.
   Si `onAudioCaptured` lanza (p. ej. `saveTempWavFile` con disco lleno), la
   excepción escapa del coroutine y tumba la app durante la captura de voz.
2. **`data/speech/WhisperCloudSTTService.kt:49`** — `saveTempWavFile(audioData)` se
   ejecuta fuera de cualquier try-catch; un `IOException` se propaga igual que el
   anterior durante la transcripción en la nube.
3. **`data/speech/WhisperCloudSTTService.kt` (AssemblyAI)** — El paso 1 (upload) y
   el paso 2 (crear transcript) no comprueban `response.isSuccessful`:
   `JSONObject(...)` lanza `JSONException` sin control y se pierde el detalle real
   del error. Además, el body del paso 2 se envía con `FormBody`
   (`application/x-www-form-urlencoded`) cuando AssemblyAI espera JSON.
4. **`presentation/workspaces/WorkspacesScreen.kt:166-171`** — `uiState.selectedWorkspace!!`
   en lambdas de click sin null-check (NPE en borde si el estado se anula entre
   composición y tap).

### 🟠 UX roto

5. **`presentation/workspace_detail/WorkspaceDetailViewModel.kt:3442-3448`** — El
   `catch` de `handleToolCalls` resetea `executingCommand` y `error` pero **no
   resetea `isLoading`**: cualquier excepción no capturada antes (p. ej.
   `addMessage` con disco lleno) deja el chat atascado en loading para siempre;
   solo se recupera con Stop. El mismo `catch` traga `CancellationException`, lo
   que rompe la cancelación estructurada del loop.
6. **`presentation/voice/STTViewModel.kt:102`** — `service.isListening.first { !it }`
   sin timeout: si el recognizer no emite `false`, la UI queda en "escuchando"
   indefinidamente.

### 🟡 Rendimiento

7. **`data/tts/AndroidTextToSpeechManager.kt:589-626`** — `playRemoteAudio` hacía
   `file.writeBytes(...)` y `player.prepare()` en el hilo principal
   (`remoteJob` corre en `Dispatchers.Main.immediate`), congelando la UI con
   WAV de varios MB. **Corregido en esta iteración** (ver cambio de Groq).
8. **`data/localmodels/LocalModelRepository.kt:60-61`** — `getAvailableModels()`
   usa `runBlocking(Dispatchers.IO)` desde un método no-suspend llamado en hilo
   principal desde `LocalModelsViewModel`, `DefaultChatModelViewModel` y
   `AgentRepository` (bloquea la UI al abrir pantallas de modelos).
9. **`data/speech/BaseSTTService.kt:266-271` + `STTManager.kt:36-49`** —
   `release()` hace `runBlocking { stopListening() }` desde un scope de Main;
   cambiar la configuración de STT congela el hilo principal.

### Notas verificadas como no-bug

- `toolCalls!!` en `WorkspaceDetailViewModel.kt:2906` está protegido por el guard
  de la línea 2880.
- `GitHubToolHandler`, `AgentCreatorToolHandler` y demás handlers tienen
  try-catch a nivel de `executeTool`.

## Cambio realizado: Groq TTS

Groq expone TTS OpenAI-compatible (`POST /v1/audio/speech`) con los modelos
Canopy Orpheus (`canopylabs/orpheus-v1-english`, `canopylabs/orpheus-arabic-saudi`).
La app ya tenía un cliente OpenAI-compatible (`SelfHostedVoiceApi.synthesize`), así
que la integración es una opción de primer nivel, no un cliente nuevo.

### Archivos tocados

- `data/speech/VoiceCatalog.kt` — `AssistantTtsMode.GROQ`, `GroqTtsConfig`
  (apiKey/voice/model), constantes `GROQ_TTS_ENDPOINT`, `GROQ_TTS_DEFAULT_MODEL`,
  `GROQ_TTS_DEFAULT_VOICE` y `GROQ_TTS_VOICES` (tara, leah, jess, leo, dan, mia,
  zac, zoe, troy, drea, autumn, austin, daniel, diana, hannah).
- `data/local/SecurePreferences.kt` — `saveVoiceTtsApiKey` / `getVoiceTtsApiKey`
  (cifradas, mismo patrón que las STT).
- `data/local/VoicePreferences.kt` — `groqTtsConfig` StateFlow + `setGroqTtsConfig`.
- `data/speech/AndroidTextToSpeechManager.kt` — `speak()` enruta `GROQ` a
  `speakWithGroq()` (síntesis vía `SelfHostedVoiceApi` apuntando a Groq, WAV
  completo); `previewGroqVoice()`; branch en `refreshVoice()`.
- `presentation/voice/VoiceSettingsViewModel.kt` — `saveGroqTtsConfig(config, preview)`
  y validación en `selectTtsMode`.
- `presentation/assistant/AssistantSettingsScreen.kt` — `GroqTtsCard` (API key
  cifrada, selector de voz Orpheus, modelo, botones Guardar/Probar).
- `presentation/voice/VoiceSettingsScreen.kt` — tarjeta Groq en la sección TTS.

### Fix incluido

`playRemoteAudio` pasó a `suspend`: la escritura del WAV temporal y `player.prepare()`
se mueven a `Dispatchers.IO`; el `MediaPlayer` se sigue creando en el hilo con Looper
para que los callbacks de completion/error sigan disparándose. Esto beneficia también
al TTS de servidor propio.

### Limitaciones conocidas

- Orpheus English es **solo inglés** (la app es principalmente en español); la UI lo
  indica. Es útil para respuestas en inglés y para probar latencia de Groq.
- No usa PCM streaming (Groq entrega WAV/MP3 completo); el modo está forzado a
  `BUFFERED_WAV`.

### Verificación

- Pendiente: `:app:compileDebugKotlin` (el entorno no tenía `JAVA_HOME`; se usa el
  JBR de Android Studio, JDK 21).
- No se ha probado en dispositivo contra la API real de Groq (requiere API key).

## Ronda 2 — Implementación (bugs + 5 features)

Iteración posterior: se arreglaron los 7 bugs, se implementó el learning loop de
skills, nudges de memoria, recuperación con resumen LLM, UX de contexto y permisos
con globs. Todo compila y la suite de tests unitarios pasa.

### Bugs arreglados (7/7)

1. `data/speech/BaseSTTService.kt` — job de `startRecordingSession` con
   `catch (CancellationException) { throw }` + `catch (Exception) { Log.e }` +
   `finally { started.complete(false) }`.
2. `data/speech/WhisperCloudSTTService.kt` — `transcribeAudio` con `runCatching`/
   `fold`, borrado del WAV temporal en `finally`, AssemblyAI con JSON real y
   `isSuccessful`, timeout de 60 polls.
3. `presentation/workspaces/WorkspacesScreen.kt` — `selectedWorkspace` sin `!!`.
4. `presentation/workspace_detail/WorkspaceDetailViewModel.kt` — el catch de
   `handleToolCalls` resetea `isLoading`/`currentReasoning` y relanza
   `CancellationException`.
5. `presentation/voice/STTViewModel.kt` — espera de `isListening` envuelta en
   `withTimeoutOrNull(120_000)` con error `voice_timeout`.
6. `data/localmodels/LocalModelRepository.kt` — caché @Volatile + invalidación en
   cada mutación; sin `runBlocking` en rutas calientes (se mantuvo no-suspend por
   `getContextWindowForModel`/`hasApiKey`).
7. `data/speech/STTManager.kt` — `release()` corre en `Dispatchers.IO` (antes
   bloqueaba Main con `runBlocking { stopListening() }`).

### Features implementadas

**UX de contexto (`/compress`, `/usage`, `/undo`, `/retry`, `/recall`, `/help`)**
- `WorkspaceDetailViewModel.kt`: intercept de comandos `"/"` en `sendMessage()`;
  `/compress` → compactación existente; `/usage` → estimación de
  `contextInfo` (tokens en uso/ventana/disponibles); `/undo` → borra el último
  turno de usuario en adelante (vía `AgentRepository.deleteMessages`);
  `/retry` → borra turno y reenvía; `/recall <tema>` → busca en memoria y resume
  con LLM; `/help` lista los comandos. Feedback vía `infoMessage` + snackbar en
  `WorkspaceDetailScreen.kt`.

**Recuperación de memoria (FTS + resumen LLM)**
- `data/terminal/MemoryExtractor.kt`: nuevo `recallAndSummarize(query, modelId,
  provider)` — FTS (`MemoryDao.searchFts`, sufijos `*`), bumps `accessCount`, y
  `AIClientFactory.chat` con `RECALL_SYSTEM_PROMPT` para resumir en narrativa.
  `WorkspaceDetailViewModel.runRecallCommand` inserta el resumen como mensaje
  ASSISTANT.
- **Decisión FTS4 vs FTS5**: se mantiene **FTS4** (el FTS externo ya soporta
  `MATCH`). Migrar a FTS5 implica replicar el `createSql` exacto que Room espera
  para validación de esquema (tabla virtual externa + `content_rowid`) y recrear
  los triggers con el naming de Room (`cortex_memory_fts_ai/bd/bu/au`); un desajuste
  rompería el arranque de toda instalación existente (v51) y no hay entorno de
  dispositivo para verificarlo aquí. Queda como tarea pendiente con prueba en
  dispositivo.

**Learning loop skills + skill_reviews**
- El bucle ya existía (turno completado → `SkillReviewEntity` PENDING → worker →
  reviewer LLM/heurístico → skill AUTO activa o actualizada). Se añadió la señal
  de uso que faltaba:
  - `SkillEntity` + dominio `Skill`: columnas `usageCount`/`lastUsedAt`.
  - Migración **51→52** en `AppDatabase.kt` (`ALTER TABLE skills ADD COLUMN ...`),
    registrada en `DatabaseModule.kt`.
  - `SkillDao.recordUsage(id, now)`; `SkillRepository.recordSkillUsage(id)`.
  - `SkillToolHandler`: `skill_view` exitoso cuenta como uso; `skill_list` muestra
    `usos: N`.
  - UI: `SkillsScreen` muestra "· N usos" en la tarjeta.
- Nota de diseño: los auto-skills nacen `ACTIVE` (`createAutomaticActive`, patrón
  Hermes); el estado `DRAFT_CREATED` del enum de reviews sigue sin escribirse.

**Nudges de memoria vía WorkManager**
- Nuevo `data/terminal/MemoryNudgeWorker.kt` (@HiltWorker): al cerrar la pantalla
  de chat, `WorkspaceDetailViewModel.onCleared()` encola (unique "memory_nudge",
  REPLACE, constraints CONNECTED salvo provider local) y el worker ejecuta
  `MemoryExtractor.checkConversationOnResume(conversationId, model, provider)`.

**Permisos ask/allow/deny con globs**
- `data/terminal/CommandPermissionManager.kt`: `checkPermission` filtra reglas
  persistidas con `matchesGlob` (regex de `*`/`?`, longest-pattern-wins);
  ALLOWED_ALWAYS y BLOCKED persisten como `"$comando*"`; ALLOWED_ONCE exacto.
- `ToolHandler.blockCommand(command)`; `WorkspaceDetailViewModel.denyPermissionAlways()`
  y limpieza de `terminalPendingCommand`; diálogo con botón "Denegar siempre".

### Verificación

- `:app:compileDebugKotlin` → BUILD SUCCESSFUL.
- `:app:testDebugUnitTest` → suite completa en verde.
- Pendiente en dispositivo: Groq TTS con API key real; migración 51→52
  (ALTER TABLE, de bajo riesgo); comandos slash y diálogo de permisos.


### Fase 1 — Estabilidad (bugs críticos/UX)

1. `BaseSTTService`: añadir `CoroutineExceptionHandler` al `serviceScope` o
   try/catch alrededor de `onAudioCaptured`; reportar el error vía `_error` en vez
   de crashear.
2. `WhisperCloudSTTService`: envolver `saveTempWavFile` y toda la cadena de
   transcripción en try-catch; comprobar `isSuccessful` en upload y transcript de
   AssemblyAI y enviar JSON real (cambiar `FormBody` por `toRequestBody`).
3. `WorkspaceDetailViewModel` catch de `handleToolCalls`: resetear `isLoading` y
   relanzar `CancellationException` (no tragarla).
4. `STTViewModel`: añadir timeout (p. ej. `withTimeout`) al `first { !it }`.
5. `WorkspacesScreen`: reemplazar `!!` por el estado nulo como pantalla/load.

### Fase 2 — Voz

6. Groq STT (mismo patrón que Groq TTS, usando `whisper-large-v3-turbo` /
   `whisper-large-v3` en `WhisperCloudSTTService`): casi gratis porque el endpoint
   es idéntico al de OpenAI.
7. Usar el idioma solicitado en Deepgram y Google en lugar de `es`/`es-ES`
   hardcodeados (`WhisperCloudSTTService`).
8. Fallback automático de STT cloud a local ante error de red.

### Fase 3 — Rendimiento

9. `LocalModelRepository.getAvailableModels`: convertir a `suspend` y emitir por
   flujo en vez de `runBlocking` en Main.
10. `STTManager.release`: ejecutar `stopListening` fuera del hilo principal.

## Mejoras de proyectos open source

Investigación de cuatro proyectos (fuentes citadas) y qué aporta cada uno a esta app.

### 1. OpenClaw — memoria en capas y autonomía

Gateway self-hosted de agente personal (MIT). Aportes:

- **Memoria en capas en Room** en vez de tabla plana: diario (append-only, se
  inyectan ~2 días) vs. memoria curada (facts/preferencias, solo en la sesión
  privada principal) vs. perfil de usuario.
- **Búsqueda híbrida**: complementar el FTS5 existente con embeddings on-device,
  re-ranking MMR y decaimiento temporal.
- **Skills que el agente escribe**: que el agente cree/actualice `SKILL.md` en
  ejecución (patrón "memoria de fallos" con log de errores + skill de remediación).
- **Enrutamiento por contexto** y reglas por remitente/canal sobre
  `CommandPermissionDao` ("mention rules").
- **Tareas autónomas con entrega multi-canal** (notificación push / voz) sobre
  `scheduled_tasks`.

### 2. OpenCode — permisos granulares y sesiones hijas

Agente de coding (MIT) con arquitectura cliente/servidor. Aportes:

- **Matriz ask/allow/deny por tool y por patrón** (globs por comando: `rm *` → ask,
  `grep *` → allow) sobre `CommandPermissionDao`.
- **Modos Plan/Build como permission sets**: toggle read-only vs. acceso completo
  reutilizando las mismas tools.
- **Subagentes con sesiones hijas aisladas**: solo el resultado final vuelve al
  padre; intermedios en su propia fila de Room (`subagent_executions` ya modelable).
- **Agentes de sistema ocultos**: uno barato para títulos de conversación, otro
  para compactar, otro de limpieza.
- **Snapshots/undo** de acciones del agente (transacción en Room o copia de archivo
  antes de cada write) con `/undo` `/redo` en la UI.

### 3. Kimi Code — ciclo de vida de subagentes

Agente de coding de Moonshot AI (MIT), documenta mejor el ciclo subagente. Aportes:

- **Subagentes como perfiles de datos** con `tools` allowlist, `disallowedTools`,
  `subagents` allowlist y `model_preference` — pasar los 8 agentes por defecto de
  lógica hardcodeada a config en Room.
- **Delegación con aprobación visible y background**: mostrar la task description
  como confirmación y ejecutar en background con opción de reanudar.
- **Herencia de permisos en delegación**: lo aprobado en el principal vale para sus
  subagentes; lo denegado se mantiene estricto.
- **Prompt como plantilla con variables** (`${memory}`, `${skills}`, `${now}`).
- **Hooks de ciclo de vida** (`before/after tool execution`, `subagent complete`)
  para notificaciones y auditoría desacopladas.

### 4. Hermes (Nous Research) — learning loop y control de contexto

Framework de agente self-improving (MIT). Aportes:

- **Learning loop conectado a `skills` + `skill_reviews`**: tras una tarea compleja
  el agente genera/actualiza una skill; cada uso registra éxito/fallo y ajusta el
  prompt (las tablas ya existen, falta el bucle).
- **Nudges de memoria** (WorkManager): al cerrar sesión o tras N mensajes, el
  agente decide qué persistir y con qué nivel.
- **Recuperación de sesiones pasadas con FTS5 + resumen LLM** ("¿qué hicimos el
  martes?").
- **UX de contexto**: `/compress`, `/usage`, `/insights`, interrupt-and-redirect,
  undo/retry en el chat.
- **Transcripción de notas de voz → mensajes** con continuidad en el historial
  (los 4 backends STT ya lo permiten).

### Prioridad de mayor ROI

1. Sesiones hijas aisladas para subagentes (OpenCode/Kimi) — ahorro de contexto.
2. Permisos ask/allow/deny + herencia a subagentes (OpenCode/Kimi).
3. Memoria en capas + búsqueda híbrida (OpenClaw).
4. Compaction controlada con agente interno de resumen (OpenCode/Hermes).
5. Learning loop de skills con reviews (Hermes).

## Fuentes

- Groq TTS: https://console.groq.com/docs/text-to-speech
- Groq STT: https://console.groq.com/docs/speech-to-text
- OpenClaw: https://github.com/openclaw/openclaw · https://docs.openclaw.ai
- OpenCode: https://opencode.ai/docs · https://opencode.ai/docs/agents · https://opencode.ai/docs/tools
- Kimi Code: https://github.com/MoonshotAI/kimi-code · https://moonshotai.github.io/kimi-code/en/customization/agents
- Hermes: https://github.com/nousresearch/hermes-agent
