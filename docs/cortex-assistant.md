# Cortex como asistente de Android

## Objetivo

Cortex puede registrarse como el asistente predeterminado de Android y abrir una interfaz
translúcida sobre la app actual. La sesión empieza como una burbuja compacta y se expande a una
conversación completa al tocar el texto de la burbuja.

## Configuración para el usuario

1. Abre **Ajustes → Cortex como asistente**.
2. Toca **Usar Cortex** y elige Cortex en el diálogo de Android.
3. Verifica las dos capacidades de voz:
   - Android on-device se usa primero cuando el sistema tiene disponible el idioma.
   - Si falta el paquete local, Android programa su descarga y esa sesión reintenta con el
     reconocedor gratuito del sistema, sin API key.
   - Vosk puede descargarse para garantizar que la entrada permanezca offline.
   - Para las respuestas se selecciona exclusivamente una voz TTS embebida, nunca una voz que
     requiera red.
4. En **Modelo y acciones del asistente**, elige un modelo dedicado o conserva **Usar el modelo del
   chat**. Esta preferencia sólo se aplica a la superficie de asistente.
5. Usa el gesto de asistente configurado por el fabricante (por ejemplo, mantener pulsado el botón
   de encendido o el gesto desde una esquina).

También se puede usar **Probar** sin cambiar el asistente predeterminado.

## Privacidad y coste

- STT y TTS no requieren cuentas, API keys, cuotas ni pagos por minuto.
- `SpeechRecognizer.createOnDeviceSpeechRecognizer` es la primera opción cuando Android 12 o
  posterior ofrece reconocimiento local. En Android 13 o posterior se solicita la descarga del
  modelo automáticamente si falta el idioma.
- El `SpeechRecognizer` normal del sistema mantiene funcional la sesión sin API keys mientras se
  instala el paquete; dependiendo del proveedor configurado por Android, este fallback puede usar
  red.
- Vosk es la opción descargable para garantizar reconocimiento offline.
- `TextToSpeech` filtra cualquier voz con `isNetworkConnectionRequired = true`.
- El asistente puede usar un modelo dedicado distinto de los chats. Para una experiencia totalmente
  offline, además de la voz local debe elegirse un modelo local en esa preferencia.
- El prompt breve, las skills integradas de clima y control de Android, y las herramientas de
  ubicación, recordatorios, alarmas, calendario y tareas programadas sólo se precargan en la
  superficie de asistente; no alteran los chats normales.
- El texto visible renderiza Markdown sencillo, mientras que TTS elimina sus marcadores antes de
  hablar para no pronunciar asteriscos, enlaces ni sintaxis de formato.
- STT, TTS y el prompt usan el idioma elegido en Cortex, aunque Android esté configurado en otro
  idioma. Al invocar el asistente comienza a escuchar cuando la actividad llega a estado reanudado;
  después de hablar una respuesta vuelve a escuchar mientras el ciclo de voz siga activo.

## Flujo técnico

```text
Gesto del sistema / botón de encendido
  → CortexVoiceInteractionService (proceso ligero)
  → CortexVoiceInteractionSession + assistant activity layer
  → CortexAssistantActivity translúcida
  → STT local primero (Android on-device o Vosk)
    ↳ reconocedor gratuito del sistema si falta el idioma local
  → Workspace global + WorkspaceDetailViewModel
  → Respuesta compacta / conversación expandida
  → TTS embebido de Android
```

La actividad no pide `SYSTEM_ALERT_WINDOW`: `VoiceInteractionSession.startAssistantActivity` hace
que Android la coloque en la capa oficial del asistente. El `VoiceInteractionService` registrado es
lo que permite que el botón de encendido sustituya a Gemini después de elegir Cortex en el diálogo
del sistema.
