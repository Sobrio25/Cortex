# Puerta de calidad para beta interna

Esta lista convierte la validación de Cortex en un proceso repetible. No publica artefactos, no
requiere secretos y debe ejecutarse sobre el mismo commit que se entrega a testers.

## 1. Preparación

- Java 21 y Android SDK 35 disponibles.
- `JAVA_HOME` apunta a Java 21.
- Emulador API 35 limpio y un dispositivo físico API 35 o posterior disponibles para las pruebas
  que dependen de hardware.
- No usar API keys, cuentas personales ni datos reales en pruebas. Los recorridos automatizados
  de onboarding utilizan BYOK/local sin completar autenticaciones externas.
- Registrar commit, variante, dispositivo, ABI, versión de Android y hora de la ejecución.

## 2. Puerta automatizada sin dispositivo

```bash
./gradlew --no-daemon --stacktrace \
  :app:testDebugUnitTest \
  :app:lintDebug \
  :app:assembleRelease \
  :app:reportReleaseApkSize \
  :app:checkReleaseNativeAlignment
```

Criterios: cero fallos de pruebas/lint, APK dentro del presupuesto vigente y ninguna biblioteca
arm64 con segmentos `LOAD` alineados por debajo de 16 KiB.

## 3. Instrumentación y migraciones

Con un emulador API 35 arrancado:

```bash
./gradlew --no-daemon --stacktrace :app:connectedDebugAndroidTest
```

La suite debe cubrir, como mínimo:

- Onboarding administrado, BYOK y local; BYOK/local no requieren Google ni consentimiento del
  plan administrado.
- Navegación chat → ajustes → proveedor → volver.
- Estado de borrador tras reconstruir la dependencia de preferencias.
- Conversión de fallo offline a estado recuperable, seguida de un reintento exitoso.
- Ausencia de permisos de almacenamiento heredado y `USE_EXACT_ALARM`.
- Migraciones 38→41, 41→46, 43→44, 44→46, 46→47, 47→48, 48→49, 49→50 y 50→51,
  incluyendo FTS, agentes personalizados, tareas programadas, skills, ejecuciones de subagentes y
  conversaciones fijadas.

Para una versión candidata, repetir la migración instalando primero el último APK publicado con
una base representativa, crear conversaciones/tareas/memorias, actualizar encima con el APK nuevo
y verificar que los datos siguen accesibles. Nunca desinstalar entre ambos pasos.

## 4. Permisos denegados y modo offline

En un dispositivo/emulador limpio:

1. Denegar notificaciones, micrófono, cámara, ubicación y calendario.
2. Abrir chat, ajustes, proveedores y modelos locales. Ninguna pantalla debe cerrarse ni entrar en
   un bucle de solicitud de permiso.
3. Activar modo avión y desactivar Wi-Fi. Enviar una solicitud con un proveedor remoto: debe
   mostrarse un error recuperable, conservar el borrador y permitir reintentar.
4. Reactivar la red y reintentar sin recrear la conversación. No debe duplicarse el mensaje ni el
   cargo lógico del turno.
5. Probar una función que sí necesita cada permiso. La app debe explicar por qué lo necesita y
   ofrecer volver a Ajustes; no debe inventar un resultado exitoso.
6. Intentar guardar una URL HTTP pública con credenciales para un proveedor remoto: debe
   rechazarse. Verificar que Ollama y LM Studio sí aceptan HTTP para `localhost`, el emulador y una
   dirección privada de la LAN.

## 5. Compatibilidad de páginas de 16 KiB

Además de `:app:checkReleaseNativeAlignment`:

- Instalar el release en un dispositivo/emulador configurado con páginas de 16 KiB.
- Abrir la aplicación y ejercitar cada runtime nativo que se distribuya en esa variante.
- Revisar `adb logcat` en busca de errores de linker, `dlopen`, `SIGBUS` o `SIGSEGV`.
- Rechazar la candidata si una biblioteca nativa sólo funciona en páginas de 4 KiB.

## 6. Carga, contexto largo y herramientas

- Ejecutar `ContextCompactionStressTest` y `ToolLoopPolicyStressTest` como parte de unit tests.
- Conversación manual de al menos 200 turnos, con archivos y resultados de herramientas grandes.
- Confirmar que el historial visible conserva el orden y que el contexto del modelo comienza en el
  checkpoint más reciente sin duplicar mensajes.
- Confirmar que un cambio de archivo no se declara completo sin un `write_file` exitoso.
- Ejecutar el macrobenchmark en el mismo dispositivo de referencia:

```bash
./gradlew :benchmark:connectedBenchmarkAndroidTest
```

Registrar mediana de arranque, máximo, versión del dispositivo y cualquier regresión frente a la
última candidata.

## 7. Criterio de salida

La beta puede avanzar sólo si:

- La puerta automatizada y la instrumentación están verdes sobre el commit candidato.
- El Play Pre-launch Report no contiene crashes, ANR ni bloqueadores de accesibilidad.
- No hay regresiones de migración ni pérdida de borradores/conversaciones.
- Los flujos con permisos denegados y sin red terminan en estados recuperables.
- Todas las bibliotecas nativas de release superan la comprobación de 16 KiB.
- Se adjuntan reportes de lint, pruebas, tamaño y benchmark a la evidencia de la candidata.

Si falla un punto, registrar propietario, severidad, reproducción y decisión explícita. No marcar
una excepción crítica como "conocida" para saltar esta puerta.
