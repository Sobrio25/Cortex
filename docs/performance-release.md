# Rendimiento y tamaño de release

Esta guía deja dos verificaciones reproducibles para Cortex 0.3.2: tiempo de arranque real en
Android y tamaño del APK de release. Ninguna prueba envía telemetría.

## Macrobenchmark de arranque

Requisitos: dispositivo físico o emulador con API 28 o superior, desbloqueado y visible en
`adb devices`. La variante `benchmark` usa el código optimizado de release, es `profileable` y
se firma únicamente con la clave de depuración local para poder instalarla durante la medición.

```bash
./gradlew :benchmark:connectedBenchmarkAndroidTest
```

`StartupBenchmark` realiza cinco arranques fríos y reporta `timeToInitialDisplayMs` y
`timeToFullDisplayMs` cuando la actividad informa que terminó de dibujar. Los resultados quedan
en `benchmark/build/outputs/connected_android_test_additional_output/benchmark/connected/`.
Ejecuta la prueba en el mismo dispositivo, sin ahorro de batería y sin otras cargas, para comparar
dos versiones.

El mismo módulo incluye `BaselineProfileGenerator`. En un dispositivo físico API 33+ libre,
ejecuta sólo esa regla y copia el archivo `*-baseline-prof.txt` generado a
`app/src/main/baseline-prof.txt` antes de comparar arranques con `CompilationMode.Partial()`:

```bash
./gradlew :benchmark:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.aiagents.app.benchmark.BaselineProfileGenerator
```

## Presupuesto y desglose del APK

```bash
./gradlew :app:reportReleaseApkSize
```

La tarea construye el release, muestra tamaño comprimido y sin comprimir, SHA-256, categorías y
las diez entradas más grandes. Falla si el APK universal supera **240 MiB**. Este umbral parte del
tamaño actual y evita regresiones; no representa el objetivo final de descarga.

Las bibliotecas nativas de inferencia local dominan el APK universal. Para distribución se debe
publicar un Android App Bundle, que entrega solo el ABI del dispositivo. La reducción adicional
debe medirse antes de excluir ABIs porque el desarrollo y algunos emuladores aún los necesitan.

## Puerta de release

Antes de publicar:

```bash
./gradlew test lint assembleRelease :app:reportReleaseApkSize
./gradlew :app:checkReleaseNativeAlignment
./gradlew :benchmark:connectedBenchmarkAndroidTest
```

Registra en las notas de release el dispositivo/API, la mediana de arranque y el tamaño comprimido
del APK. No compares mediciones obtenidas en dispositivos distintos.
