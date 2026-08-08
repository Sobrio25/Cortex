# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug

# Clean build artifacts
./gradlew clean

# Run all tests
./gradlew test

# Run connected device tests
./gradlew connectedAndroidTest
```

## Distribution Variants

Google Play uses the `release` Android App Bundle and Play Feature Delivery for `:voice`. A dedicated upload key in ignored `keystore.properties` is mandatory:

```bash
./gradlew bundlePlayRelease
```

For direct APK installs outside Google Play, always use the `sideload` build variant rather than `release`:

```bash
# Build the directly distributed app and refresh the hosted voice pack artifacts
./gradlew prepareSideloadVoicePack

# Install the directly distributed app on a connected device
./gradlew installSideload

# Publish the STT/TTS voice pack used by sideload builds
firebase deploy --only hosting:voicepack --project cortex-agents-ai
```

The `sideload` variant sets `BuildConfig.EXTERNAL_VOICE_PACK=true` and downloads the installable STT/TTS voice feature from Firebase Hosting using `https://cortex-agents-voice-pack.web.app/cortex-voice-pack.json`. Do not use this variant in Play Console.

`prepareSideloadVoicePack` copies the signed `voice-sideload.apk` to `dashboard/downloads/cortex-voice-pack.apk` and regenerates `dashboard/downloads/cortex-voice-pack.json` with the current app `versionCode`, byte size, and SHA-256. The hosted manifest and app must have matching version codes, and the base APK and voice pack must be signed with the same certificate or Android will reject installation.

## Architecture

**Native Android app** — MVVM + Clean Architecture with Jetpack Compose, Hilt DI, Room database, Retrofit.

**Tech stack:** Kotlin 2.0.21, Compose BOM 2025.02.00, minSDK 26, targetSDK 35, JVM 21, Gradle 9.3.1.

**Key layers:**
- `data/` — Room DB (v51), Retrofit API clients, repositories, orchestration, tool handlers, STT services
- `domain/model/` — Pure domain models (Agent, Workspace, Message, Conversation, Provider, MCPServer, LocalModel)
- `presentation/` — Compose screens + ViewModels, one package per screen (13 modules)
- `di/` — Hilt modules: DatabaseModule, NetworkModule, TerminalModule
- `ui/` — Shared Compose components and Material 3 theme

**Navigation:** `MainScreen.kt` defines routes for onboarding/chat/workspaces plus settings screens
for subscriptions, agents, providers, default model, local models, MCP, memory, skills, scheduled
tasks, assistant, voice, capabilities, Google Workspace, and diagnostics.

## Key Data Flow

1. `AIClientFactory` creates provider-specific clients (managed Cortex, OpenRouter, Google AI,
   OpenAI, NVIDIA, Anthropic, Moonshot, MiniMax, DeepSeek, Grok, Ollama, LM Studio, Kilo, Alibaba,
   OpenCode, Z.AI, and LocalLLM)
2. `AgentRepository` is the single source of truth — exposes Flow streams from Room DAOs, coordinates LLM requests with tool execution
3. `AgentOrchestrator` coordinates multi-agent delegation; Cortex (central coordinator) uses delegation patterns:
   - `DELEGATE: [AgentName]` — single agent
   - `DELEGATE_SEQ: [Agent1] -> [Agent2]` — sequential (output chains)
   - `DELEGATE_PAR: [Agent1], [Agent2]` — parallel execution
4. Tool calls from LLMs are parsed as structured JSON and dispatched through handlers in
   `data/terminal/` (search, files, shell, calendar, GitHub, Notion, Slack, Google Drive, memory,
   todos, finance, skills, delegation, app controls, etc.). Use the source tree rather than a fixed
   handler count because capabilities are added independently.

## Database

Primary Room database `ai_agents_db` is version 52 with 17 entities: agents, messages, files,
workspaces, command permissions, MCP servers, conversations, memory (+ FTS + links), custom local
models, todos, scheduled tasks, downloads, skills, skill reviews, and subagent executions. Finance
uses its own `FinanceDatabase`.

Migrations are defined as companion object methods in `AppDatabase.kt` and registered in `DatabaseModule.kt`. Always add a migration when changing schema.

8 default system agents are inserted on DB creation (Cortex, Programmer, Writer, Researcher, Data Analyst, Tutor, Health Advisor, Agent Architect).

## Speech-to-Text

Four STT backends managed by `STTManager` in `data/speech/`:
- `SherpaOnnxSTTService` — offline Whisper via ONNX with NNAPI/NPU on Snapdragon
- `WhisperLocalSTTService` — offline via MediaPipe
- `VoskSTTService` — lightweight offline for low-end devices
- `AndroidSpeechRecognizerSTTService` / `WhisperCloudSTTService` — cloud-based (AssemblyAI, Deepgram, Google Cloud, OpenAI)

Hardware detection in `STTManager` selects the best backend automatically (Snapdragon NPU detection, RAM-based model sizing).

## Security Patterns

- API keys stored via `EncryptedSharedPreferences` (AndroidX Security Crypto, wrapped in `SecurePreferences`)
- Terminal command execution requires user approval via `CommandPermissionDao` with risk levels
- Tool execution requests flow through `PermissionRequest`/`ToolExecutionRequest` before shell access

## Local LLM

On-device inference uses Google MediaPipe (`LocalLLMClient`). Models are managed in the `local_models/` presentation package. Model downloads handled by `ModelDownloader` from HuggingFace.
