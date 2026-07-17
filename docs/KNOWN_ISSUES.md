# Incidencias técnicas

## Mitigado en release: compatibilidad nativa con páginas de 16 KiB

El AAR oficial de Sherpa se actualizó a 1.13.2. Sus binarios arm64 de ONNX Runtime y Sherpa declaran
ahora segmentos ELF alineados a 16 KiB (`0x4000`). El runtime publicado de Vosk 0.3.47 continúa
declarando `0x1000`, por lo que se incluye únicamente en builds `debug`; `release` usa el
reconocedor local de Android como fallback y no empaqueta `libvosk.so`.

Antes de publicar cada release se debe ejecutar la auditoría `checkReleaseNativeAlignment`, comprobar
que ninguna biblioteca arm64 marque menos de `0x4000` y validar el App Bundle en una imagen Android
de 16 KiB. Vosk no volverá a release hasta que exista un artefacto oficial compatible o se aísle en
un módulo descargable independiente.

## Resuelto en 0.3.1: Firebase en el proceso de voz

La primera ejecución de `connectedDebugAndroidTest` detectó que el proceso secundario
`com.aiagents.app:cortex_voice` podía solicitar `FirebaseAuth` antes de disponer del `FirebaseApp`
predeterminado:

```text
IllegalStateException: Default FirebaseApp is not initialized in this process
com.aiagents.app:cortex_voice
```

`FirebaseBootstrap` inicializa ahora Firebase de forma idempotente en cada proceso antes de la
inyección de Hilt, y `NetworkModule` obtiene `FirebaseAuth` a partir de esa instancia explícita. La
prueba instrumental valida tanto la idempotencia como la creación de `FirebaseAuth`; las ocho
pruebas conectadas pasaron y el Logcat posterior a la instalación no contiene el crash.
