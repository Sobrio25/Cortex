# Plan futuro: router local de tareas para Cortex

## Estado

Propuesta guardada para una fase futura. No está activa en la aplicación.

La referencia de modelo indicada es **Gemma 4 E2B**. Antes de comenzar habrá que confirmar el nombre exacto del checkpoint, su licencia y si existe una ruta de conversión compatible con Android; el diseño no debe depender de una variante concreta.

## Objetivo

Entrenar un modelo pequeño que se ejecute completamente en el dispositivo y se especialice en:

1. Decidir si una petición necesita delegación.
2. Dividirla en tareas acotadas y verificables.
3. Elegir el agente apropiado para cada tarea.
4. Clasificar su dificultad y asignarle el nivel de modelo adecuado.
5. Elegir ejecución paralela o secuencial según las dependencias.
6. Entregar un plan estructurado al scheduler existente.

El router no redactará la respuesta final ni ejecutará tools. Cortex seguirá siendo el coordinador principal y revisará los resultados antes de responder al usuario.

## Arquitectura objetivo

```text
Petición del usuario
        │
        ▼
Router Gemma local
        │  JSON validado
        ▼
SubagentTaskEnvelope[]
        │
        ▼
Scheduler y subagentes
        │
        ▼
Cortex principal: revisión, corrección y respuesta final
```

El runtime, y no el modelo, será la autoridad final para permisos, límites, modelos disponibles, profundidad, concurrencia y acceso de escritura.

## Contrato de salida

El router debe producir únicamente JSON conforme a un esquema cerrado. Propuesta inicial:

```json
{
  "decision": "direct | delegate",
  "reason": "explicación breve",
  "mode": "parallel | sequential",
  "failure_policy": "continue | fail_fast",
  "tasks": [
    {
      "agent_name": "Researcher",
      "goal": "objetivo autocontenido",
      "context": "solo el contexto necesario",
      "acceptance_criteria": "resultado observable",
      "difficulty": "simple | medium | complex",
      "workspace_policy": "read_only_shared | write_exclusive",
      "max_iterations": 12
    }
  ]
}
```

No se permitirá que el router emita claves de API, comandos, argumentos libres de tools ni identificadores de modelos. La aplicación traducirá `difficulty` a la configuración elegida por el usuario.

## Política de dificultad futura

- `simple`: extracción, clasificación, formato, transformaciones mecánicas y comprobaciones pequeñas.
- `medium`: investigación acotada, programación ordinaria, comparación, análisis y redacción con criterios claros.
- `complex`: arquitectura, ambigüedad importante, integración, depuración difícil y decisiones de alto impacto.

La primera versión podrá usar tres niveles configurables: principal, medio y sencillo. Las tareas complejas y la síntesis final deberán usar el principal.

## Creación del dataset

### Fuentes

- Casos escritos manualmente que representen los flujos reales de Cortex.
- Ejemplos sintéticos generados por un modelo maestro y revisados automáticamente.
- `DelegationDatasetGenerator` existente, ampliado al nuevo esquema.
- Conversaciones reales únicamente con consentimiento, anonimización y exclusión de secretos.
- Casos negativos donde no se debe delegar: saludos, preguntas breves, acciones únicas y solicitudes inseguras.
- Casos adversariales: agentes inexistentes, dependencias ocultas, tareas duplicadas, recursión y escrituras concurrentes.

### Etiquetas necesarias

- `direct` frente a `delegate`.
- Agente correcto.
- Descomposición de tareas.
- Paralelo frente a secuencial.
- Dificultad.
- Política de workspace.
- Presupuesto de iteraciones.
- Criterios de aceptación.

### División

Separar train, validation y test por familias de intención, no aleatoriamente por frases, para evitar que plantillas casi idénticas aparezcan en varios conjuntos.

## Entrenamiento

1. Crear primero un baseline sin fine-tuning usando prompt compacto y few-shot.
2. Ejecutar SFT con LoRA o QLoRA sobre el contrato JSON.
3. Añadir ejemplos de reparación para JSON inválido y decisiones inseguras.
4. Evaluar preference tuning o DPO solo si SFT no calibra bien dificultad y `direct/delegate`.
5. Destilar decisiones de un modelo maestro, conservando un conjunto humano independiente para medir sesgos del maestro.
6. Congelar versiones del dataset, configuración, tokenizer, checkpoint y métricas para reproducibilidad.

El entrenamiento debe favorecer salidas cortas y estructuradas; no conviene entrenar al router para producir razonamientos extensos.

## Evaluación

### Métricas

- JSON válido y conforme al esquema.
- Exactitud de `direct/delegate`.
- Exactitud del agente y top-k.
- F1 macro de dificultad.
- Exactitud de paralelo/secuencial.
- Cobertura de requisitos y ausencia de tareas duplicadas.
- Tasa de tareas inseguras rechazadas por el runtime.
- Tokens, coste y latencia end-to-end.
- Calidad final comparada con delegación realizada por un modelo principal.

### Objetivos iniciales de salida a producción

- JSON válido: al menos 99.5%.
- `direct/delegate`: al menos 95% en el conjunto de prueba.
- F1 macro de dificultad: al menos 0.85.
- Latencia p95 en Pixel 6a: menor de 1 segundo.
- Reducción de coste cloud: al menos 40%.
- Pérdida de calidad final frente al baseline principal: menor de 3%.
- Cero bypasses de permisos en el conjunto adversarial.

Estos valores son puertas de lanzamiento, no afirmaciones sobre el rendimiento actual.

## Android e inferencia local

1. Confirmar compatibilidad del checkpoint con MediaPipe, LiteRT/TFLite u ONNX Runtime.
2. Probar FP16, int8 y 4-bit; elegir la menor cuantización que conserve la calidad de routing.
3. Medir RAM, temperatura, batería, latencia y estabilidad en dispositivos reales.
4. Mantener un contexto compacto: identidad de agentes, capacidades, petición y restricciones esenciales.
5. Cargar el router bajo demanda y descargarlo de memoria después de inactividad si el dispositivo lo necesita.
6. Usar NNAPI/GPU únicamente cuando mejore latencia sin comprometer estabilidad.

## Integración por fases

### Fase 0 — Dataset y simulador

- Definir el esquema versionado.
- Crear validador y evaluador offline.
- Ampliar el generador de ejemplos.
- Construir un benchmark representativo de Cortex.

### Fase 1 — Modelo local aislado

- Entrenar y convertir el primer checkpoint.
- Crear `LocalTaskRouter` sin conectarlo al flujo productivo.
- Ejecutarlo sobre el benchmark y registrar métricas.

### Fase 2 — Shadow mode

- El router propone decisiones, pero Cortex continúa decidiendo realmente.
- Comparar ambas rutas sin afectar al usuario.
- Guardar únicamente telemetría anónima y habilitada por el usuario.

### Fase 3 — Despliegue protegido

- Activar el router para un porcentaje pequeño de tareas.
- Validar todas las salidas y usar Cortex como fallback.
- Añadir circuit breaker ante JSON inválido, modelo ausente, latencia excesiva o baja confianza.

### Fase 4 — Configuración de tres modelos

- Añadir en Ajustes el modelo principal, el modelo medio y el sencillo.
- Mostrar en la UI la dificultad, el modelo asignado y el estado en cola.
- Mantener la revisión final siempre en el principal.
- Medir ahorro real por conversación.

## Riesgos y mitigaciones

- **Mala descomposición:** validación estructural, acceptance criteria obligatorios y fallback a Cortex.
- **Clasificación barata pero incorrecta:** umbral de confianza y escalamiento automático al principal.
- **Datos sensibles:** entrenamiento opt-in, anonimización local y filtros de secretos.
- **Modelo alucina agentes:** lista cerrada de identificadores y rechazo de valores desconocidos.
- **Recursión excesiva:** profundidad y presupuestos impuestos por el runtime.
- **Degradación por cuantización:** evaluación por checkpoint y posibilidad de usar una variante mayor en dispositivos capaces.
- **Sesgo del modelo maestro:** conjunto de evaluación humano separado.

## Decisiones pendientes

- Confirmar el checkpoint exacto de Gemma 4 E2B y sus condiciones de distribución.
- Determinar si el router necesita español e inglés desde la primera versión.
- Elegir la herramienta de entrenamiento y el formato final para Android.
- Definir cómo se estima confianza sin pedir razonamiento largo.
- Decidir si el nivel `complex` puede crear subagentes con el modelo principal o debe quedarse siempre en Cortex.
- Definir los modelos y dispositivos mínimos soportados.

## Criterio para retomar esta función

No añadir la configuración de tres modelos a producción hasta que el router local supere las puertas de evaluación en shadow mode. Hasta entonces, Cortex y el scheduler actual seguirán siendo la ruta estable.
