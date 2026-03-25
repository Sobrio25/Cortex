# Speech-to-Text (STT) Implementation

## Resumen

Se ha implementado un sistema completo de Speech-to-Text que permite al usuario elegir entre procesamiento **local** (Whisper) o en la **nube** (múltiples proveedores gratuitos). El sistema detecta automáticamente si el dispositivo tiene un procesador Snapdragon con NPU para optimizar el modelo descargado.

## Características

### 1. Detección Automática de Hardware
- **Snapdragon con NPU**: Usa modelos optimizados para NPU (más rápidos)
- **Snapdragon sin NPU**: Usa modelos CPU optimizados
- **Otros procesadores**: Selecciona modelo según RAM disponible

### 2. Modos de Procesamiento

#### Local (Whisper)
- **Ventajas**: Sin internet, privado, sin costos
- **Modelos disponibles**:
  - `Snapdragon NPU` - Optimizado para chips Snapdragon 8 Gen 2+
  - `Tiny` - 39MB, más rápido, menos preciso
  - `Base` - 74MB, balance velocidad/precisión
  - `Small` - 244MB, más preciso, requiere más RAM

#### Cloud (Gratis)
| Proveedor | Tier Gratuito | Requiere Tarjeta |
|-----------|---------------|------------------|
| **AssemblyAI** | 100 horas/mes | No |
| **Deepgram** | $200 créditos (~45h) | Sí |
| **Google Cloud** | 60 minutos/mes | Sí |
| **OpenAI Whisper** | $5 créditos iniciales | Sí |

**Recomendado**: AssemblyAI (no requiere tarjeta de crédito)

## Archivos Creados

### Data Layer
```
data/model/STTSettingsEntity.kt       # Entidad Room para configuración
data/local/STTSettingsDao.kt          # DAO para STT settings
data/speech/BaseSTTService.kt         # Servicio base con grabación de audio
data/speech/WhisperLocalSTTService.kt # Implementación local con Whisper
data/speech/WhisperCloudSTTService.kt # Implementación cloud con múltiples proveedores
data/speech/STTManager.kt             # Manager para gestionar cloud/local
```

### Domain Layer
```
domain/service/STTService.kt          # Interfaz abstracta del servicio STT
```

### UI Layer
```
presentation/stt/STTSettingsScreen.kt      # Pantalla de configuración STT
presentation/stt/VoiceInputComponents.kt   # Componentes UI (botón micrófono, etc.)
presentation/stt/STTViewModel.kt           # ViewModel para manejar STT
```

### Utilidades
```
util/HardwareDetector.kt              # Detecta Snapdragon/NPU/CPU
```

## Integración con WorkspaceDetailScreen

Para integrar el botón de micrófono en el chat input:

```kotlin
// En WorkspaceDetailScreen.kt
val sttViewModel: STTViewModel = hiltViewModel()
val isListening by sttViewModel.isListening.collectAsState()
val pendingTranscription by sttViewModel.pendingTranscription.collectAsState()

// En el ChatInput, agregar:
VoiceInputButton(
    isListening = isListening,
    onStartListening = { sttViewModel.startListening() },
    onStopListening = { sttViewModel.stopListening() },
    enabled = !isLoading
)

// Mostrar transcripción pendiente:
pendingTranscription?.let { text ->
    TranscriptionPreview(
        text = text,
        isProcessing = false,
        onAccept = {
            viewModel.updateInputText(text)
            sttViewModel.acceptTranscription()
        },
        onCancel = { sttViewModel.cancelTranscription() }
    )
}
```

## Configuración de STT

```kotlin
// Mostrar diálogo de configuración
var showSTTSettings by remember { mutableStateOf(false) }

if (showSTTSettings) {
    STTSettingsDialog(
        currentSettings = sttViewModel.currentSettings.value?.toUiState() 
            ?: STTSettingsUiState(),
        deviceInfo = sttViewModel.deviceInfo.value,
        onSave = { settings ->
            sttViewModel.saveSettings(workspaceId, settings)
            showSTTSettings = false
        },
        onDismiss = { showSTTSettings = false },
        onDownloadModel = { sttViewModel.downloadModel({ progress -> }, { success -> }) }
    )
}
```

## Uso

1. **Primera vez**: El usuario debe configurar el modo STT (local o cloud)
2. **Modo Local**: Descargar el modelo Whisper recomendado para su dispositivo
3. **Modo Cloud**: Obtener API key del proveedor elegido
4. **Hablar**: Presionar el botón del micrófono y hablar
5. **Confirmar**: Revisar la transcripción y aceptar o cancelar

## Permisos Requeridos

Agregado en `AndroidManifest.xml`:
```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

## Notas Técnicas

1. **Modelos Whisper**: Se descargan desde HuggingFace (ggml format)
2. **Formato de audio**: WAV, 16kHz, mono, 16-bit PCM
3. **NPU Snapdragon**: Requiere ONNX Runtime con QNN Execution Provider
4. **Idiomas**: Soporta español, inglés, portugués, francés, alemán, italiano

## Próximos Pasos (Opcional)

1. Implementar JNI bindings para whisper.cpp (mejor rendimiento local)
2. Agregar soporte para streaming de audio (transcripción en tiempo real)
3. Implementar corrección automática con LLM
4. Agregar comandos de voz específicos
