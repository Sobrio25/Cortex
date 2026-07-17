# Sprint de estabilización

## Semanas 1–2

La primera fase prioriza conversión, publicación y observabilidad sobre nuevas funciones.

- El onboarding ofrece tres rutas deliberadas: Cortex Cloud, API keys propias y ejecución local.
  Sólo Cortex Cloud exige Google y el consentimiento específico del plan administrado.
- Release no solicita permisos heredados de almacenamiento, no declara `USE_EXACT_ALARM`, no
  permite HTTP global y mantiene los archivos internos privados.
- Sherpa usa un AAR oficial compatible con páginas de 16 KiB. Vosk queda fuera de release hasta
  disponer de binarios compatibles.
- Diagnóstico local registra tiempo hasta el primer dibujo, última razón de salida del proceso y un
  marcador mínimo del último error no controlado. Nunca persiste prompts, respuestas o argumentos.
- La puerta de release incluye tests, lint, APK, auditoría ELF y pruebas instrumentadas críticas.

## Separación de runtimes nativos

El APK base debe contener la experiencia cloud/BYOK y las capacidades Android que no dependan de
motores pesados. Los runtimes locales se moverán detrás de una interfaz estable y paquetes
descargables:

1. `local-llm-mediapipe`
2. `local-llm-litertlm`
3. `stt-sherpa`
4. `stt-vosk`, únicamente cuando sea compatible con 16 KiB

Cada paquete debe declarar versión, ABIs, tamaño, requisitos de RAM, alineación ELF y checksum. La
app base consulta el catálogo, descarga sólo el motor elegido y conserva un fallback funcional. El
objetivo de distribución es un módulo base menor de 40 MiB; los modelos y runtimes opcionales no se
contabilizan dentro de ese presupuesto.

## Criterios de salida

- Primera conversación posible sin Google mediante BYOK o local.
- Cero permisos heredados de almacenamiento y cero `USE_EXACT_ALARM` en el manifest combinado.
- Cero binarios arm64 con alineación ELF inferior a 16 KiB en release.
- Métrica local de primer dibujo y razón de salida visible en Diagnóstico.
- Tests unitarios, instrumentados, lint y ensamble release aprobados.
