# Cortex — Native AI Agent Manager for Android

A native Android app that turns your device into a multi-agent AI workspace: specialized agents with editable system prompts, persistent per-project workspaces, **17+ LLM providers**, **on-device voice and local models**, and **multi-agent delegation** orchestrated from a central coordinator.

[![CI](https://github.com/Sobrio25/Cortex/actions/workflows/ci.yml/badge.svg)](https://github.com/Sobrio25/Cortex/actions/workflows/ci.yml)
[![Quality gate](https://github.com/Sobrio25/Cortex/actions/workflows/beta-quality-gate.yml/badge.svg)](https://github.com/Sobrio25/Cortex/actions/workflows/beta-quality-gate.yml)
[![Instrumentation](https://github.com/Sobrio25/Cortex/actions/workflows/android-instrumentation.yml/badge.svg)](https://github.com/Sobrio25/Cortex/actions/workflows/android-instrumentation.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

## Highlights

### 🤖 Multi-agent system
- Create agents with specialized roles (developer, accountant, lawyer, tutor, researcher, …) and fully editable system prompts
- Per-agent temperature and max-token configuration
- A central coordinator delegates work across agents:
  - `DELEGATE: [Agent]` — single agent
  - `DELEGATE_SEQ: [A] -> [B]` — sequential chains (output chaining)
  - `DELEGATE_PAR: [A], [B]` — parallel execution

### 🗂️ Workspaces
- Separate workspaces per project, each with its own active agent and model
- Persistent conversation history and file attachments per workspace

### 🔌 17+ LLM providers
Managed Cortex, OpenRouter, Google AI, OpenAI, NVIDIA, Anthropic, Moonshot, MiniMax, DeepSeek, xAI (Grok), Ollama, LM Studio, Kilo, Alibaba, OpenCode, Z.AI — plus **on-device local models** (MediaPipe, downloaded from HuggingFace) for fully offline inference.

### 🎙️ Voice, on-device first
- 4 STT backends with automatic hardware detection: Sherpa-ONNX (Snapdragon NPU), MediaPipe Whisper, Vosk, and cloud fallback
- Embedded TTS voices — no voice API keys required
- Invoke Cortex as the Android system assistant role, or use the floating bubble for hands-free conversation

### 🔒 Security
- API keys stored in `EncryptedSharedPreferences` (AndroidX Security Crypto)
- Integrated terminal with **risk-level command approval** before any shell access
- Optional per-tool execution permissions

### 🧩 Extensible
- MCP server support, persistent memory (FTS + links), scheduled tasks, skills, and subagent executions
- Tool integrations: shell, files, search, calendar, GitHub, Notion, Slack, Google Drive, memory, todos, finance, skills, delegation, app controls

### 🧠 Knowledge base (on-device RAG)
- Semantic search over your own documents and notes, **fully on-device** (MediaPipe Text Embedder, Universal Sentence Encoder — multilingual)
- Documents are chunked, embedded, and stored in Room; retrieval is tool-called with **mandatory SOURCE citations**, and retrieved content is treated as user data, never as instructions
- No API keys, no cloud calls, no data leaves the device; brute-force cosine search is fine for personal-scale collections
- Add documents from *Settings → Knowledge base (RAG)*, then ask Cortex about them in any chat

### ☁️ Backend + dashboard
- Firebase Cloud Functions backend (Node.js 22) with automated tests
- Web dashboard for monitoring and voice-pack distribution

## Architecture

**MVVM + Clean Architecture** — Jetpack Compose · Hilt · Room (v53, 19 entities) · Retrofit · KSP

- `data/` — Room database + migrations, Retrofit clients, repositories, orchestration, tool handlers, STT services
- `domain/` — pure domain models (Agent, Workspace, Message, Provider, MCPServer, LocalModel)
- `presentation/` — Compose screens + ViewModels, one package per screen (13 modules)
- `di/` — Hilt modules

## Build

```bash
./gradlew assembleDebug          # debug APK
./gradlew installDebug           # install on a connected device
./gradlew test                   # unit tests
./gradlew connectedAndroidTest   # on-device instrumentation tests
```

A `sideload` build variant distributes the app outside Google Play together with a hosted STT/TTS voice pack (`./gradlew prepareSideloadVoicePack`).

## Firebase setup

1. Copy `app/google-services.json.example` → `app/google-services.json` and fill in your Firebase Android app values (package `com.aiagents.app`).
2. **Never commit the real `google-services.json`** — it is gitignored; CI injects it from the `GOOGLE_SERVICES_JSON` repository secret.
3. Deploy backend secrets with `firebase functions:secrets:set`.

## CI

Three GitHub Actions workflows: `ci` (build + unit tests), `beta-quality-gate`, and `android-instrumentation` (on-device tests).

## Project layout

```
app/          Android app (Kotlin, Jetpack Compose)
functions/    Firebase Cloud Functions backend (Node.js 22)
dashboard/    Web dashboard
voice/        Voice pack sources
docs/         Design docs and plans
benchmark/    Performance benchmarks
```

## License

[MIT](LICENSE) © 2026 Gabriel Hernandez
