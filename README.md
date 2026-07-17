# Cortex

Aplicación Android nativa para gestionar múltiples agentes de IA con workspaces especializados.

## Características

### 🤖 Multi-agente
- Crea agentes con roles especializados (contador, abogado, programador, etc.)
- **Edita y elimina** agentes existentes fácilmente
- Cada agente tiene instrucciones de sistema personalizables
- Configuración de temperatura y tokens máximos

### 📁 Workspaces (Espacios de trabajo)
- Crea múltiples workspaces para diferentes proyectos
- Cada workspace puede tener un agente activo asignado
- Historial de conversaciones persistente por workspace
- Gestión de archivos adjuntos por workspace

### 🔌 Múltiples proveedores de IA
Soporta 7 proveedores con sus logos oficiales:
- **OpenRouter** - Acceso a múltiples modelos (Claude, GPT, Llama, etc.)
- **Google AI (Gemini)** - Modelos Gemini 2.5/3.0
- **OpenAI** - GPT-4, GPT-3.5, o1
- **Anthropic** - Claude 4.6
- **Moonshot AI** - Modelos Moonshot
- **MiniMax** - Modelos MiniMax
- **Ollama** - Modelos locales (sin API Key)

### 🔒 Seguridad
- API Keys almacenadas con **EncryptedSharedPreferences**
- Sistema de permisos para ejecución de comandos
- Niveles de riesgo para comandos del terminal

### 🎙️ Cortex como asistente
- Invocación mediante el rol oficial de asistente de Android
- Burbuja translúcida compacta y conversación expandida por voz o texto
- Reconocimiento local con Android on-device y fallback Vosk
- Respuestas con voces TTS embebidas, sin API keys de voz

### 💻 Terminal integrado
- Los agentes pueden ejecutar comandos del sistema (opcional)
- Sistema de aprobación de comandos con niveles de riesgo
- Visualización de salida de comandos en el chat

### 📊 Gestión de recursos
- Contador de tokens por conversación
- Límites de contexto configurables
- Múltiples modelos por proveedor

## Arquitectura

- **MVVM** + Clean Architecture
- **Jetpack Compose** para UI declarativa
- **Hilt** para inyección de dependencias
- **Room** para base de datos local
- **Retrofit** para APIs REST
- **KSP** para procesamiento de anotaciones

## Compilar

```bash
./gradlew assembleDebug
```

O instalar directamente en un dispositivo conectado:

```bash
./gradlew installDebug
```

## Configuración

### 1. Configurar proveedores
1. Abre la app y ve a la pestaña **"Proveedores"**
2. Toca el proveedor que deseas configurar
3. Ingresa tu API Key (no requerida para Ollama)
4. Para OpenAI y Ollama puedes configurar URL base personalizada
5. Selecciona el proveedor activo con el botón radial

### 2. Crear agentes
1. Ve a la pestaña **"Agentes"**
2. Toca el botón **+** para crear un nuevo agente
3. Ingresa nombre, rol e instrucciones del sistema
4. Los agentes pueden editarse o eliminarse en cualquier momento

### 3. Crear workspaces
1. Ve a la pestaña **"Workspaces"**
2. Crea un nuevo workspace con nombre y descripción
3. Asigna un agente y selecciona el modelo a usar
4. ¡Comienza a chatear!

## Proveedores soportados

| Proveedor | API Key | Modelos |
|-----------|---------|---------|
| OpenRouter | Requerida | Claude, GPT, Llama, Mistral, etc. |
| Google AI | Requerida | Gemini 3.1 Flash/Pro |
| OpenAI | Requerida | GPT-4o, GPT-4, GPT-3.5, o1 |
| Anthropic | Requerida | Claude 4.6 Sonnet, Claude 4.6 Opus |
| Moonshot | Requerida | Moonshot v1 |
| MiniMax | Requerida | MiniMax-M2.5 |
| Ollama | No requerida | Modelos locales (Llama, Mistral, etc.) |

## Estructura del proyecto

```
app/src/main/java/com/aiagents/app/
├── data/
│   ├── local/         # Base de datos Room, DAOs, EncryptedSharedPreferences
│   ├── remote/        # Clientes API (Retrofit)
│   ├── repository/    # Repositorios de datos
│   └── terminal/      # Ejecución de comandos del sistema
├── domain/
│   └── model/         # Modelos de dominio (Agent, Workspace, Provider, etc.)
├── presentation/
│   ├── agents/        # Pantalla de gestión de agentes
│   ├── providers/     # Pantalla de configuración de proveedores
│   ├── workspaces/    # Pantalla de workspaces
│   ├── workspace_detail/  # Chat y detalle del workspace
│   └── MainScreen.kt  # Navegación principal
└── di/                # Módulos de Hilt para inyección de dependencias
```

## Licencia

MIT License - Libre para uso personal y comercial.
