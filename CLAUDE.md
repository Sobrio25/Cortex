# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

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

## Architecture

**Native Android app** — MVVM + Clean Architecture with Jetpack Compose, Hilt DI, Room database, Retrofit.

**Tech stack:** Kotlin 2.0.21, Compose BOM 2025.02.00, minSDK 26, targetSDK 35, JVM 21, Gradle 9.3.1.

**Key layers:**
- `data/` — Room DB (v38), Retrofit API clients, repositories, orchestration, tool handlers, STT services
- `domain/model/` — Pure domain models (Agent, Workspace, Message, Conversation, Provider, MCPServer, LocalModel)
- `presentation/` — Compose screens + ViewModels, one package per screen (13 modules)
- `di/` — Hilt modules: DatabaseModule, NetworkModule, TerminalModule
- `ui/` — Shared Compose components and Material 3 theme

**Navigation:** `MainScreen.kt` defines routes: Onboarding, Chat, Workspaces, WorkspaceDetail (chat interface), Settings, Agents, Providers, LocalModels, MCP, Memory, GoogleWorkspace.

## Key Data Flow

1. `AIClientFactory` creates provider-specific clients (OpenRouter, Google AI, OpenAI, Anthropic, Moonshot, MiniMax, DeepSeek, Grok, Ollama, Kilo, Alibaba, OpenCode, Z.AI, LocalLLM)
2. `AgentRepository` is the single source of truth — exposes Flow streams from Room DAOs, coordinates LLM requests with tool execution
3. `AgentOrchestrator` coordinates multi-agent delegation; Cortex (central coordinator) uses delegation patterns:
   - `DELEGATE: [AgentName]` — single agent
   - `DELEGATE_SEQ: [Agent1] -> [Agent2]` — sequential (output chains)
   - `DELEGATE_PAR: [Agent1], [Agent2]` — parallel execution
4. Tool calls from LLMs are parsed as structured JSON and dispatched to 31 handlers in `data/terminal/` (search, files, shell, calendar, GitHub, Notion, Slack, Google Drive, memory, todos, finance, etc.)

## Database

Room database named `ai_agents_db`, version 38 with 37 migrations. 15 entities: agents, messages, files, workspaces, command_permissions, stt_settings, mcp_servers, conversations, memory (+ FTS + links), custom_local_models, finance_transactions, todos, scheduled_tasks.

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
