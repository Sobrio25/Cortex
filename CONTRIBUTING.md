# Contribuir a Cortex

## Requisitos locales

- JDK 21.
- Android SDK 35 y un `local.properties` que apunte al SDK.
- Node.js 22 y npm para `functions/`.

No se necesitan credenciales de proveedores para ejecutar los gates de CI. El archivo
`app/google-services.json` incluido corresponde a la configuración pública del cliente Firebase;
las claves privadas del backend se inyectan únicamente al desplegar Functions.

## Gates antes de enviar cambios

Desde la raíz del repositorio:

```bash
./gradlew lintDebug testDebugUnitTest assembleDebug
```

Para el backend administrado:

```bash
cd functions
npm ci
npm test
```

`npm test` limpia y compila TypeScript antes de ejecutar todos los tests de Node. El lockfile es
obligatorio: no sustituir `npm ci` por una instalación que lo modifique dentro de CI.

## Pruebas con dispositivo

Las pruebas de Room, almacenamiento cifrado y otras integraciones Android requieren un dispositivo
o emulador:

```bash
./gradlew :app:connectedDebugAndroidTest
```

GitHub Actions no las ejecuta en cada pull request por su coste y tiempo de arranque. El workflow
**Android instrumentation** se puede iniciar manualmente desde la pestaña Actions; crea un emulador
Google APIs de Android 35 y publica los informes aunque falle alguna prueba. Debe ejecutarse antes de
publicar una versión y siempre que cambien Room, migraciones, almacenamiento seguro o permisos.

## Migraciones Room

Todo cambio de esquema debe incluir, en el mismo cambio:

1. incremento de la versión de la base de datos;
2. una migración registrada en `DatabaseModule`;
3. el esquema JSON exportado bajo `app/schemas/`;
4. una prueba en `AppDatabaseMigrationTest` que parta de la última versión publicada y demuestre que
   los datos relevantes se conservan.

No se acepta `fallbackToDestructiveMigration`: una instalación existente debe poder actualizarse sin
perder conversaciones, configuración ni tareas.

## Qué valida CI

El workflow **CI** se ejecuta en cada pull request y en cada push a `main`, con cachés separadas de
Gradle y npm:

- Android con Java 21: `lintDebug`, `testDebugUnitTest` y `assembleDebug`.
- Functions con Node 22: instalación reproducible, compilación TypeScript y tests.
- Publicación de reportes y del APK debug como artefactos temporales.

Un cambio solo está listo para integrar cuando ambos jobs han terminado correctamente y, si toca una
integración Android, también ha pasado **Android instrumentation**.
