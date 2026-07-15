# Cortex como asistente de Android

## Objetivo

Cortex puede registrarse como el asistente predeterminado de Android y abrir una interfaz
translúcida sobre la app actual. La sesión empieza como una burbuja compacta y se expande a una
conversación completa al tocar el texto de la burbuja.

## Configuración para el usuario

1. Abre **Ajustes → Cortex como asistente**.
2. Toca **Usar Cortex** y elige Cortex en el diálogo de Android.
3. Verifica las dos capacidades de voz:
   - Android on-device se usa cuando el sistema tiene disponible el idioma.
   - Vosk puede descargarse como respaldo local garantizado.
   - Para las respuestas se selecciona exclusivamente una voz TTS embebida, nunca una voz que
     requiera red.
4. Usa el gesto de asistente configurado por el fabricante (por ejemplo, mantener pulsado el botón
   de encendido o el gesto desde una esquina).

También se puede usar **Probar** sin cambiar el asistente predeterminado.

## Privacidad y coste

- STT y TTS no requieren cuentas, API keys, cuotas ni pagos por minuto.
- `SpeechRecognizer.createOnDeviceSpeechRecognizer` es la primera opción cuando Android 12 o
  posterior ofrece reconocimiento local.
- Vosk es el fallback descargable para equipos o idiomas sin paquete on-device.
- `TextToSpeech` filtra cualquier voz con `isNetworkConnectionRequired = true`.
- El modelo que razona y genera la respuesta sigue siendo el modelo seleccionado para Cortex. Para
  una experiencia totalmente offline, además de la voz local debe elegirse un modelo local en la
  app.

## Flujo técnico

```text
Gesto ACTION_ASSIST
  → CortexAssistantActivity translúcida
  → STT local (Android on-device o Vosk)
  → Workspace global + WorkspaceDetailViewModel
  → Respuesta compacta / conversación expandida
  → TTS embebido de Android
```

La actividad no pide `SYSTEM_ALERT_WINDOW`: Android la inicia mediante el rol oficial de asistente,
lo que evita un overlay permanente y mantiene el control de invocación en manos del usuario.
