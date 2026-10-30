# Implementation Plan: Recibir vaciado sanitario

**Fecha**: 2026-10-02
**Spec**: [recibirVaciadoSanitario.md](recibirVaciadoSanitario.md)
**Arquitectura y tecnologías**: [arquitectura.md](../arquitectura/arquitectura.md)
**Plan general**: [plan-modulo-1.md](../plan-general/plan-modulo-1.md) (historia US7, prioridad P1)

## Summary

El Módulo 2 publica un evento Kafka de vaciado; el galpón EN_COSECHA pasa a VACIADO_SANITARIO, el lote se desvincula y, cumplido el período configurado, un job devuelve el galpón a DISPONIBLE.

**Enfoque técnico:**

1. `VaciadoSanitarioConsumer` (tópico `avicontrol.vaciado-sanitario`) deserializa `EventoEnvelope<VaciadoSanitarioMessage>` y delega en `RecibirVaciadoSanitarioUseCase` (FR-001, FR-002).
2. `VaciadoSanitarioService.recibir` corre en **una transacción** (FR-011): consulta la alerta por `alertaId` (idempotencia, FR-003, FR-013), valida galpón, relación lote-galpón, estado EN_COSECHA y fecha (FR-004 a FR-006, FR-016).
3. Solicita EN_COSECHA -> VACIADO_SANITARIO a `GalponEstadoService` con origen `ALERTA_VACIADO_SANITARIO` (FR-007); luego guarda la alerta aceptada y marca `desvinculado_en` en el lote (FR-008 a FR-010).
4. Si cualquier paso falla, la transacción revierte y no quedan cambios parciales (FR-012, FR-017).
5. `VaciadoSanitarioJob` revisa los galpones en VACIADO_SANITARIO cuyo período configurado ya se cumplió y solicita VACIADO_SANITARIO -> DISPONIBLE con origen `PROCESO_AUTOMATICO` (FR-014).

## Contexto específico

- **Tareas previas del plan general**: T006, T009, T014, T016, T086 (`FixtureJson`), T087 (`EventoEnvelope`).

## Estructura del proyecto

```text
src/main/java/com/unimag/avicontrol/
├── application/
│   ├── port/in/RecibirVaciadoSanitarioUseCase.java      # T065
│   └── usecase/VaciadoSanitarioService.java              # T066 (recibir), T067 (finalizarPeriodosCumplidos)
└── infrastructure/in/
    ├── kafka/
    │   ├── VaciadoSanitarioConsumer.java                 # T068
    │   └── dto/VaciadoSanitarioMessage.java              # T064
    └── job/VaciadoSanitarioJob.java                      # T067

src/test/java/com/unimag/avicontrol/
├── application/VaciadoSanitarioServiceTest.java          # T062
└── infrastructure/in/
    ├── kafka/VaciadoSanitarioConsumerTest.java           # T095
    └── job/VaciadoSanitarioJobTest.java                  # T096
```

Además de los archivos del árbol, el código completo de este caso de uso incluye: `FinalizarVaciadoSanitarioUseCase.java`, `AlertaVaciadoSanitarioJpaRepository.java`, `AlertaVaciadoPersistenceAdapter.java`. Están en la sección *Código de implementación*.

`EventoEnvelope` (T087), `AlertaVaciadoSanitario` (T009), `KafkaConsumerConfig` (T014) y `GalponEstadoService` (T016) ya existen por el plan general.

## Decisiones de diseño

**Idempotencia por clave primaria.** `alertaId` es la clave de la tabla de alertas de vaciado aceptadas. Mismo `alertaId` y mismos datos: se ignora sin otro registro, desvinculación ni cambio de estado (SC-003). Mismos `alertaId` y datos distintos: se rechaza y el registro original no se sobrescribe.

**Orden dentro de la transacción.** Primero se solicita el cambio de estado, que valida bajo el bloqueo del galpón; después se guarda la alerta y se desvincula el lote. Así dos alertas distintas simultáneas para el mismo galpón no pueden aplicar las dos: la segunda ve el estado ya cambiado y se rechaza como `GALPON_NO_EN_COSECHA`.

**La desvinculación conserva la trazabilidad.** `desvinculado_en` se marca en el lote y la alerta guarda `galponId` y `loteId` (FR-008, FR-009, SC-007). El lote sigue apareciendo en el historial de lotes del galpón.

**Estados rechazados.** Cualquier estado distinto de EN_COSECHA se rechaza, incluidos VACIADO_SANITARIO y DISPONIBLE (FR-006).

**Job.** `@Scheduled(cron = "${avicontrol.vaciado-sanitario.cron:0 0 * * * *}", zone = "America/Bogota")`, por defecto cada hora. Consulta los galpones en VACIADO_SANITARIO con `fechaHoraRecepcion + dias <= ahora` y procesa cada uno en su propia transacción, de modo que el fallo de uno no frena a los demás. Si `avicontrol.vaciado-sanitario.dias` no está configurado, no ejecuta ninguna transición y deja la configuración faltante en el log (FR-015). Se asume una sola instancia de la aplicación (una granja).

**Período.** Se cuenta desde la recepción de la alerta aceptada (P-02).

## Contrato

> Los nombres de campo son propuestos (P-06) y deben acordarse con el Módulo 2 (ver *Coherencia entre módulos*). El JSON de ejemplo es el `payload` del [sobre de eventos](../arquitectura/arquitectura.md#sobre-de-eventos).

### Mensaje Kafka — Vaciado sanitario

| Elemento | Definición |
|---|---|
| Topic | `avicontrol.vaciado-sanitario` |
| Productor / consumidor | Módulo 2 / Módulo 1 (grupo `modulo-1`) |
| Clave de partición | `galponId` |
| Mensaje (request) | `VaciadoSanitarioMessage` (JSON) |
| Respuesta | Ninguna. Éxito: el mensaje se confirma. Rechazo: se registra en el log y sigue la política de errores del consumer (P-05). |

**Campos del mensaje**

| Campo | Tipo | Obligatorio | Regla |
|---|---|---|---|
| alertaId | UUID | sí | Clave de idempotencia; no puede reutilizarse con datos distintos |
| galponId | UUID | sí | El galpón debe existir y estar EN_COSECHA |
| loteId | UUID | sí | El lote debe referenciar al galpón |
| fechaHoraEvento | datetime | sí | No futura y del mismo día de recepción (America/Bogota) |

**Resultados**

| Caso (spec) | Resultado |
|---|---|
| Datos válidos con galpón EN_COSECHA | En **una transacción**: registra la alerta aceptada, marca `desvinculado_en` del lote y solicita EN_COSECHA -> VACIADO_SANITARIO (origen `ALERTA_VACIADO_SANITARIO`) |
| Falta un campo obligatorio; galpón o lote inexistente; lote sin relación con el galpón; fecha futura o de otro día | Rechazado |
| Galpón en otro estado (incluye VACIADO_SANITARIO y DISPONIBLE) | Rechazado |
| Mismo `alertaId` con datos distintos | Rechazado; el registro original no se sobrescribe |
| Mismo `alertaId` y mismos datos | Ignorado: sin otro registro, desvinculación ni cambio de estado |
| Dos alertas distintas simultáneas para el mismo galpón | Solo una aplica; la otra falla al validar el estado vigente |

La alerta aceptada guarda `alertaId`, `galponId`, `loteId`, fecha del evento, fecha de recepción y resultado; la desvinculación no borra esos UUID.

**Proceso automático (`VaciadoSanitarioJob`, no HTTP):** revisa los galpones en VACIADO_SANITARIO cuyo período (`avicontrol.vaciado-sanitario.dias`) ya se cumplió y solicita VACIADO_SANITARIO -> DISPONIBLE con origen `PROCESO_AUTOMATICO`. Si el período no está configurado, no ejecuta la transición y reporta la configuración faltante en el log.

Ejemplo de mensaje (`payload`):

```json
{
  "alertaId": "0c8e2a11-5d4f-4b7a-a3e9-1f6b2c9d8e47",
  "galponId": "550e8400-e29b-41d4-a716-446655440000",
  "loteId": "2fc03a21-84c4-4c83-bb15-6607bb75cb90",
  "fechaHoraEvento": "2026-10-05T16:00:00-05:00"
}
```

### Códigos de rechazo

| Código | Mensaje (log y, si se adopta, respuesta al Módulo 2) |
|---|---|
| `ALERTA_INCOMPLETA` | Falta un campo obligatorio de la alerta. |
| `VERSION_NO_SOPORTADA` | La versión del evento no es soportada. |
| `GALPON_NO_ENCONTRADO` | El galpón de la alerta no existe. |
| `LOTE_NO_ENCONTRADO` | El lote de la alerta no existe. |
| `LOTE_NO_PERTENECE_AL_GALPON` | El lote no corresponde al galpón de la alerta. |
| `GALPON_NO_EN_COSECHA` | El galpón no está en estado EN_COSECHA. |
| `FECHA_INVALIDA` | La fecha del evento es futura o corresponde a otro día. |
| `ALERTA_INCONSISTENTE` | El alertaId ya fue procesado con datos distintos. |
| `MENSAJE_ILEGIBLE` | El mensaje no es un JSON válido o tiene campos con tipos incorrectos. |
| `TIPO_EVENTO_NO_SOPORTADO` | El tipo de evento no corresponde a este tópico. |

Línea de log de una alerta repetida (ignorada):

```text
INFO VaciadoSanitarioService ignorada alertaId=0c8e2a11-5d4f-4b7a-a3e9-1f6b2c9d8e47 motivo=YA_PROCESADA
```

Línea de log del job cuando el período no está configurado:

```text
WARN VaciadoSanitarioService avicontrol.vaciado-sanitario.dias sin configurar: no se ejecuta la transicion a DISPONIBLE
```

## Peticiones y respuestas por escenario

Es un mensaje Kafka: no hay path ni query params ni respuesta HTTP. Cada escenario muestra el mensaje completo (sobre + `payload`) y su resultado; al final, el proceso automático que devuelve el galpón a DISPONIBLE.

**1. Vaciado válido con el galpón EN_COSECHA**

- **Topic**: `avicontrol.vaciado-sanitario`
- **Clave de partición**: `550e8400-e29b-41d4-a716-446655440000`
- **Cabecera/sobre**: `eventType=VACIADO_SANITARIO_SOLICITADO`, `eventVersion=1`

Valor del mensaje (sobre del Módulo 2 con el `payload` de este escenario):

```json
{
  "eventId": "c4b8a2d1-6e3f-4a07-9b15-8d2e7f1c0a93",
  "eventType": "VACIADO_SANITARIO_SOLICITADO",
  "eventVersion": 1,
  "occurredAt": "2026-10-05T14:30:00-05:00",
  "publishedAt": "2026-10-05T14:30:02-05:00",
  "producer": "modulo-2",
  "aggregateType": "Galpon",
  "aggregateId": "550e8400-e29b-41d4-a716-446655440000",
  "payload": {
    "alertaId": "0c8e2a11-5d4f-4b7a-a3e9-1f6b2c9d8e47",
    "galponId": "550e8400-e29b-41d4-a716-446655440000",
    "loteId": "2fc03a21-84c4-4c83-bb15-6607bb75cb90",
    "fechaHoraEvento": "2026-10-05T16:00:00-05:00"
  }
}
```

**Resultado** (el Módulo 1 no responde al productor):

- En una transacción: se solicita `EN_COSECHA -> VACIADO_SANITARIO` (origen `ALERTA_VACIADO_SANITARIO`), se guarda la alerta aceptada (`alertaId`, `galponId`, `loteId`, fecha del evento, fecha de recepción, resultado `ACEPTADA`) y se marca `desvinculado_en` del lote.
- El lote sigue en el historial de lotes del galpón.

**2. Reentrega: mismo `alertaId` con los mismos datos**

- **Topic**: `avicontrol.vaciado-sanitario`
- **Clave de partición**: `550e8400-e29b-41d4-a716-446655440000`
- **Cabecera/sobre**: `eventType=VACIADO_SANITARIO_SOLICITADO`, `eventVersion=1`

Valor del mensaje (sobre del Módulo 2 con el `payload` de este escenario):

```json
{
  "eventId": "c4b8a2d1-6e3f-4a07-9b15-8d2e7f1c0a93",
  "eventType": "VACIADO_SANITARIO_SOLICITADO",
  "eventVersion": 1,
  "occurredAt": "2026-10-05T14:30:00-05:00",
  "publishedAt": "2026-10-05T14:30:02-05:00",
  "producer": "modulo-2",
  "aggregateType": "Galpon",
  "aggregateId": "550e8400-e29b-41d4-a716-446655440000",
  "payload": {
    "alertaId": "0c8e2a11-5d4f-4b7a-a3e9-1f6b2c9d8e47",
    "galponId": "550e8400-e29b-41d4-a716-446655440000",
    "loteId": "2fc03a21-84c4-4c83-bb15-6607bb75cb90",
    "fechaHoraEvento": "2026-10-05T16:00:00-05:00"
  }
}
```

**Resultado** (el Módulo 1 no responde al productor):

- Se ignora: sin otro registro, desvinculación ni cambio de estado. El offset se confirma.
- **Log** (`INFO`): `INFO VaciadoSanitarioService ignorada alertaId=0c8e2a11-5d4f-4b7a-a3e9-1f6b2c9d8e47 motivo=YA_PROCESADA`

**3. Falta un campo obligatorio (sin `loteId`)**

- **Topic**: `avicontrol.vaciado-sanitario`
- **Clave de partición**: `550e8400-e29b-41d4-a716-446655440000`
- **Cabecera/sobre**: `eventType=VACIADO_SANITARIO_SOLICITADO`, `eventVersion=1`

Valor del mensaje (sobre del Módulo 2 con el `payload` de este escenario):

```json
{
  "eventId": "c4b8a2d1-6e3f-4a07-9b15-8d2e7f1c0a93",
  "eventType": "VACIADO_SANITARIO_SOLICITADO",
  "eventVersion": 1,
  "occurredAt": "2026-10-05T14:30:00-05:00",
  "publishedAt": "2026-10-05T14:30:02-05:00",
  "producer": "modulo-2",
  "aggregateType": "Galpon",
  "aggregateId": "550e8400-e29b-41d4-a716-446655440000",
  "payload": {
    "alertaId": "0c8e2a11-5d4f-4b7a-a3e9-1f6b2c9d8e47",
    "galponId": "550e8400-e29b-41d4-a716-446655440000",
    "fechaHoraEvento": "2026-10-05T16:00:00-05:00"
  }
}
```

**Resultado** (el Módulo 1 no responde al productor):

- Rechazo de negocio: no se guarda nada, el galpón conserva su estado y **no se reintenta**.
- **Log** (`WARN`): `WARN VaciadoSanitarioConsumer rechazada alertaId=0c8e2a11-5d4f-4b7a-a3e9-1f6b2c9d8e47 galponId=550e8400-e29b-41d4-a716-446655440000 codigo=ALERTA_INCOMPLETA mensaje="Falta un campo obligatorio de la alerta."`

**4. Galpón inexistente**

- **Topic**: `avicontrol.vaciado-sanitario`
- **Clave de partición**: `550e8400-e29b-41d4-a716-446655440000`
- **Cabecera/sobre**: `eventType=VACIADO_SANITARIO_SOLICITADO`, `eventVersion=1`

Valor del mensaje (sobre del Módulo 2 con el `payload` de este escenario):

```json
{
  "eventId": "c4b8a2d1-6e3f-4a07-9b15-8d2e7f1c0a93",
  "eventType": "VACIADO_SANITARIO_SOLICITADO",
  "eventVersion": 1,
  "occurredAt": "2026-10-05T14:30:00-05:00",
  "publishedAt": "2026-10-05T14:30:02-05:00",
  "producer": "modulo-2",
  "aggregateType": "Galpon",
  "aggregateId": "550e8400-e29b-41d4-a716-446655440000",
  "payload": {
    "alertaId": "0c8e2a11-5d4f-4b7a-a3e9-1f6b2c9d8e47",
    "galponId": "d9b2d63d-a233-4123-847a-3f2c0c1e6b55",
    "loteId": "2fc03a21-84c4-4c83-bb15-6607bb75cb90",
    "fechaHoraEvento": "2026-10-05T16:00:00-05:00"
  }
}
```

**Resultado** (el Módulo 1 no responde al productor):

- Rechazo de negocio: no se guarda nada, el galpón conserva su estado y **no se reintenta**.
- **Log** (`WARN`): `WARN VaciadoSanitarioConsumer rechazada alertaId=0c8e2a11-5d4f-4b7a-a3e9-1f6b2c9d8e47 galponId=d9b2d63d-a233-4123-847a-3f2c0c1e6b55 codigo=GALPON_NO_ENCONTRADO mensaje="El galpón de la alerta no existe."`

**5. Lote inexistente**

- **Topic**: `avicontrol.vaciado-sanitario`
- **Clave de partición**: `550e8400-e29b-41d4-a716-446655440000`
- **Cabecera/sobre**: `eventType=VACIADO_SANITARIO_SOLICITADO`, `eventVersion=1`

Valor del mensaje (sobre del Módulo 2 con el `payload` de este escenario):

```json
{
  "eventId": "c4b8a2d1-6e3f-4a07-9b15-8d2e7f1c0a93",
  "eventType": "VACIADO_SANITARIO_SOLICITADO",
  "eventVersion": 1,
  "occurredAt": "2026-10-05T14:30:00-05:00",
  "publishedAt": "2026-10-05T14:30:02-05:00",
  "producer": "modulo-2",
  "aggregateType": "Galpon",
  "aggregateId": "550e8400-e29b-41d4-a716-446655440000",
  "payload": {
    "alertaId": "0c8e2a11-5d4f-4b7a-a3e9-1f6b2c9d8e47",
    "galponId": "550e8400-e29b-41d4-a716-446655440000",
    "loteId": "d9b2d63d-a233-4123-847a-3f2c0c1e6b55",
    "fechaHoraEvento": "2026-10-05T16:00:00-05:00"
  }
}
```

**Resultado** (el Módulo 1 no responde al productor):

- Rechazo de negocio: no se guarda nada, el galpón conserva su estado y **no se reintenta**.
- **Log** (`WARN`): `WARN VaciadoSanitarioConsumer rechazada alertaId=0c8e2a11-5d4f-4b7a-a3e9-1f6b2c9d8e47 galponId=550e8400-e29b-41d4-a716-446655440000 codigo=LOTE_NO_ENCONTRADO mensaje="El lote de la alerta no existe."`

**6. El lote no corresponde al galpón**

- **Topic**: `avicontrol.vaciado-sanitario`
- **Clave de partición**: `550e8400-e29b-41d4-a716-446655440000`
- **Cabecera/sobre**: `eventType=VACIADO_SANITARIO_SOLICITADO`, `eventVersion=1`

Valor del mensaje (sobre del Módulo 2 con el `payload` de este escenario):

```json
{
  "eventId": "c4b8a2d1-6e3f-4a07-9b15-8d2e7f1c0a93",
  "eventType": "VACIADO_SANITARIO_SOLICITADO",
  "eventVersion": 1,
  "occurredAt": "2026-10-05T14:30:00-05:00",
  "publishedAt": "2026-10-05T14:30:02-05:00",
  "producer": "modulo-2",
  "aggregateType": "Galpon",
  "aggregateId": "550e8400-e29b-41d4-a716-446655440000",
  "payload": {
    "alertaId": "0c8e2a11-5d4f-4b7a-a3e9-1f6b2c9d8e47",
    "galponId": "6ba7b810-9dad-41d1-80b4-00c04fd430c8",
    "loteId": "2fc03a21-84c4-4c83-bb15-6607bb75cb90",
    "fechaHoraEvento": "2026-10-05T16:00:00-05:00"
  }
}
```

**Resultado** (el Módulo 1 no responde al productor):

- Rechazo de negocio: no se guarda nada, el galpón conserva su estado y **no se reintenta**.
- **Log** (`WARN`): `WARN VaciadoSanitarioConsumer rechazada alertaId=0c8e2a11-5d4f-4b7a-a3e9-1f6b2c9d8e47 galponId=6ba7b810-9dad-41d1-80b4-00c04fd430c8 codigo=LOTE_NO_PERTENECE_AL_GALPON mensaje="El lote no corresponde al galpón de la alerta."`

**7. El galpón no está EN_COSECHA (PRODUCTIVO, AISLAMIENTO, VACIADO_SANITARIO, DISPONIBLE, ...)**

- **Topic**: `avicontrol.vaciado-sanitario`
- **Clave de partición**: `550e8400-e29b-41d4-a716-446655440000`
- **Cabecera/sobre**: `eventType=VACIADO_SANITARIO_SOLICITADO`, `eventVersion=1`
- **Nota**: También es el resultado de la segunda de dos alertas distintas simultáneas: la primera ya lo dejó en VACIADO_SANITARIO.

Valor del mensaje (sobre del Módulo 2 con el `payload` de este escenario):

```json
{
  "eventId": "c4b8a2d1-6e3f-4a07-9b15-8d2e7f1c0a93",
  "eventType": "VACIADO_SANITARIO_SOLICITADO",
  "eventVersion": 1,
  "occurredAt": "2026-10-05T14:30:00-05:00",
  "publishedAt": "2026-10-05T14:30:02-05:00",
  "producer": "modulo-2",
  "aggregateType": "Galpon",
  "aggregateId": "550e8400-e29b-41d4-a716-446655440000",
  "payload": {
    "alertaId": "0c8e2a11-5d4f-4b7a-a3e9-1f6b2c9d8e47",
    "galponId": "550e8400-e29b-41d4-a716-446655440000",
    "loteId": "2fc03a21-84c4-4c83-bb15-6607bb75cb90",
    "fechaHoraEvento": "2026-10-05T16:00:00-05:00"
  }
}
```

**Resultado** (el Módulo 1 no responde al productor):

- Rechazo de negocio: no se guarda nada, el galpón conserva su estado y **no se reintenta**.
- **Log** (`WARN`): `WARN VaciadoSanitarioConsumer rechazada alertaId=0c8e2a11-5d4f-4b7a-a3e9-1f6b2c9d8e47 galponId=550e8400-e29b-41d4-a716-446655440000 codigo=GALPON_NO_EN_COSECHA mensaje="El galpón no está en estado EN_COSECHA."`

**8. Fecha futura**

- **Topic**: `avicontrol.vaciado-sanitario`
- **Clave de partición**: `550e8400-e29b-41d4-a716-446655440000`
- **Cabecera/sobre**: `eventType=VACIADO_SANITARIO_SOLICITADO`, `eventVersion=1`
- **Nota**: Igual con una fecha de otro día.

Valor del mensaje (sobre del Módulo 2 con el `payload` de este escenario):

```json
{
  "eventId": "c4b8a2d1-6e3f-4a07-9b15-8d2e7f1c0a93",
  "eventType": "VACIADO_SANITARIO_SOLICITADO",
  "eventVersion": 1,
  "occurredAt": "2026-10-05T14:30:00-05:00",
  "publishedAt": "2026-10-05T14:30:02-05:00",
  "producer": "modulo-2",
  "aggregateType": "Galpon",
  "aggregateId": "550e8400-e29b-41d4-a716-446655440000",
  "payload": {
    "alertaId": "0c8e2a11-5d4f-4b7a-a3e9-1f6b2c9d8e47",
    "galponId": "550e8400-e29b-41d4-a716-446655440000",
    "loteId": "2fc03a21-84c4-4c83-bb15-6607bb75cb90",
    "fechaHoraEvento": "2026-10-05T23:30:00-05:00"
  }
}
```

**Resultado** (el Módulo 1 no responde al productor):

- Rechazo de negocio: no se guarda nada, el galpón conserva su estado y **no se reintenta**.
- **Log** (`WARN`): `WARN VaciadoSanitarioConsumer rechazada alertaId=0c8e2a11-5d4f-4b7a-a3e9-1f6b2c9d8e47 galponId=550e8400-e29b-41d4-a716-446655440000 codigo=FECHA_INVALIDA mensaje="La fecha del evento es futura o corresponde a otro día."`

**9. Mismo `alertaId` con datos distintos**

- **Topic**: `avicontrol.vaciado-sanitario`
- **Clave de partición**: `550e8400-e29b-41d4-a716-446655440000`
- **Cabecera/sobre**: `eventType=VACIADO_SANITARIO_SOLICITADO`, `eventVersion=1`
- **Nota**: El registro original no se sobrescribe.

Valor del mensaje (sobre del Módulo 2 con el `payload` de este escenario):

```json
{
  "eventId": "c4b8a2d1-6e3f-4a07-9b15-8d2e7f1c0a93",
  "eventType": "VACIADO_SANITARIO_SOLICITADO",
  "eventVersion": 1,
  "occurredAt": "2026-10-05T14:30:00-05:00",
  "publishedAt": "2026-10-05T14:30:02-05:00",
  "producer": "modulo-2",
  "aggregateType": "Galpon",
  "aggregateId": "550e8400-e29b-41d4-a716-446655440000",
  "payload": {
    "alertaId": "0c8e2a11-5d4f-4b7a-a3e9-1f6b2c9d8e47",
    "galponId": "550e8400-e29b-41d4-a716-446655440000",
    "loteId": "9a1f4c70-2d3b-4a55-8e61-0b7f3c9d2e18",
    "fechaHoraEvento": "2026-10-05T16:00:00-05:00"
  }
}
```

**Resultado** (el Módulo 1 no responde al productor):

- Rechazo de negocio: no se guarda nada, el galpón conserva su estado y **no se reintenta**.
- **Log** (`WARN`): `WARN VaciadoSanitarioConsumer rechazada alertaId=0c8e2a11-5d4f-4b7a-a3e9-1f6b2c9d8e47 galponId=550e8400-e29b-41d4-a716-446655440000 codigo=ALERTA_INCONSISTENTE mensaje="El alertaId ya fue procesado con datos distintos."`

**10. Valor que no es un JSON válido**

- **Topic**: `avicontrol.vaciado-sanitario`
- **Clave de partición**: `550e8400-e29b-41d4-a716-446655440000`
- **Cabecera/sobre**: `eventType=VACIADO_SANITARIO_SOLICITADO`, `eventVersion=1`

Valor del mensaje (texto crudo):

```
{"eventId": "c4b8a2d1", "payload": 
```

**Resultado** (el Módulo 1 no responde al productor):

- Rechazo sin reintento.
- **Log** (`WARN`): `WARN VaciadoSanitarioConsumer rechazada alertaId=null galponId=null codigo=MENSAJE_ILEGIBLE mensaje="El mensaje no es un JSON válido o tiene campos con tipos incorrectos."`

**11. Versión del evento no soportada**

- **Topic**: `avicontrol.vaciado-sanitario`
- **Clave de partición**: `550e8400-e29b-41d4-a716-446655440000`
- **Cabecera/sobre**: `eventType=VACIADO_SANITARIO_SOLICITADO`, `eventVersion=1`

Valor del mensaje (sobre del Módulo 2 con el `payload` de este escenario):

```json
{
  "eventId": "6f2d9c1a-0b7e-4e4a-9d51-3c8a7b1e2f90",
  "eventType": "VACIADO_SANITARIO_SOLICITADO",
  "eventVersion": 2,
  "occurredAt": "2026-10-05T14:30:00-05:00",
  "publishedAt": "2026-10-05T14:30:02-05:00",
  "producer": "modulo-2",
  "aggregateType": "Galpon",
  "aggregateId": "550e8400-e29b-41d4-a716-446655440000",
  "payload": {
    "alertaId": "0c8e2a11-5d4f-4b7a-a3e9-1f6b2c9d8e47",
    "galponId": "550e8400-e29b-41d4-a716-446655440000",
    "loteId": "2fc03a21-84c4-4c83-bb15-6607bb75cb90",
    "fechaHoraEvento": "2026-10-05T16:00:00-05:00"
  }
}
```

**Resultado** (el Módulo 1 no responde al productor):

- Rechazo sin reintento.
- **Log** (`WARN`): `WARN VaciadoSanitarioConsumer rechazada alertaId=null galponId=null codigo=VERSION_NO_SOPORTADA mensaje="La versión del evento no es soportada."`

**12. `eventType` que no corresponde al tópico**

- **Topic**: `avicontrol.vaciado-sanitario`
- **Clave de partición**: `550e8400-e29b-41d4-a716-446655440000`
- **Cabecera/sobre**: `eventType=MORTALIDAD_REGISTRADA`, `eventVersion=1`

Valor del mensaje (sobre del Módulo 2 con el `payload` de este escenario):

```json
{
  "eventId": "6f2d9c1a-0b7e-4e4a-9d51-3c8a7b1e2f90",
  "eventType": "MORTALIDAD_REGISTRADA",
  "eventVersion": 1,
  "occurredAt": "2026-10-05T14:30:00-05:00",
  "publishedAt": "2026-10-05T14:30:02-05:00",
  "producer": "modulo-2",
  "aggregateType": "Galpon",
  "aggregateId": "550e8400-e29b-41d4-a716-446655440000",
  "payload": {
    "alertaId": "0c8e2a11-5d4f-4b7a-a3e9-1f6b2c9d8e47",
    "galponId": "550e8400-e29b-41d4-a716-446655440000",
    "loteId": "2fc03a21-84c4-4c83-bb15-6607bb75cb90",
    "fechaHoraEvento": "2026-10-05T16:00:00-05:00"
  }
}
```

**Resultado** (el Módulo 1 no responde al productor):

- Rechazo sin reintento.
- **Log** (`WARN`): `WARN VaciadoSanitarioConsumer rechazada alertaId=null galponId=null codigo=TIPO_EVENTO_NO_SOPORTADO mensaje="El tipo de evento no corresponde a este tópico."`

**13. Error técnico (base de datos no disponible)**

- **Topic**: `avicontrol.vaciado-sanitario`
- **Clave de partición**: `550e8400-e29b-41d4-a716-446655440000`
- **Cabecera/sobre**: `eventType=VACIADO_SANITARIO_SOLICITADO`, `eventVersion=1`

Valor del mensaje (sobre del Módulo 2 con el `payload` de este escenario):

```json
{
  "eventId": "6f2d9c1a-0b7e-4e4a-9d51-3c8a7b1e2f90",
  "eventType": "VACIADO_SANITARIO_SOLICITADO",
  "eventVersion": 1,
  "occurredAt": "2026-10-05T14:30:00-05:00",
  "publishedAt": "2026-10-05T14:30:02-05:00",
  "producer": "modulo-2",
  "aggregateType": "Galpon",
  "aggregateId": "550e8400-e29b-41d4-a716-446655440000",
  "payload": {
    "alertaId": "0c8e2a11-5d4f-4b7a-a3e9-1f6b2c9d8e47",
    "galponId": "550e8400-e29b-41d4-a716-446655440000",
    "loteId": "2fc03a21-84c4-4c83-bb15-6607bb75cb90",
    "fechaHoraEvento": "2026-10-05T16:00:00-05:00"
  }
}
```

**Resultado** (el Módulo 1 no responde al productor):

- Se revierte toda la transacción (ni estado, ni alerta, ni desvinculación), no se confirma el offset y se **reintenta** (P-05).

#### Proceso automático (`VaciadoSanitarioJob`, no HTTP)

Se ejecuta con `@Scheduled(cron = "${avicontrol.vaciado-sanitario.cron:0 0 * * * *}", zone = "America/Bogota")`: sin entrada. Cada galpón se procesa en su propia transacción.

**14. Período cumplido (`avicontrol.vaciado-sanitario.dias=3`, alerta recibida hace 3 días o más)**

- **Llamada**: `VaciadoSanitarioService.finalizarPeriodosCumplidos()`
- **Nota**: Log: `INFO VaciadoSanitarioService galpon=550e8400-e29b-41d4-a716-446655440000 VACIADO_SANITARIO -> DISPONIBLE por periodo cumplido`. Queda una fila en el historial de estados.

Argumentos:

```json
{
  "galponId": "550e8400-e29b-41d4-a716-446655440000",
  "estadoEsperado": "VACIADO_SANITARIO",
  "estadoDestino": "DISPONIBLE",
  "origen": "PROCESO_AUTOMATICO"
}
```

Resultado (cambio de estado):

```json
{
  "galponId": "550e8400-e29b-41d4-a716-446655440000",
  "estadoAnterior": "VACIADO_SANITARIO",
  "estadoNuevo": "DISPONIBLE",
  "origen": "PROCESO_AUTOMATICO",
  "fechaHora": "2026-10-08T16:00:00-05:00"
}
```

**15. Período aún no cumplido**

- **Llamada**: `VaciadoSanitarioService.finalizarPeriodosCumplidos()`
- **Nota**: La consulta del job no lo incluye; se revisa de nuevo en la siguiente ejecución.

Argumentos:

```json
{
  "galponId": "550e8400-e29b-41d4-a716-446655440000",
  "recibidaEn": "2026-10-06T16:00:00-05:00",
  "ahora": "2026-10-08T16:00:00-05:00",
  "dias": 3
}
```

Resultado:

```json
{
  "accion": "ninguna",
  "estadoDelGalpon": "VACIADO_SANITARIO"
}
```

**16. Período no configurado**

- **Llamada**: `VaciadoSanitarioService.finalizarPeriodosCumplidos()`

Argumentos:

```json
{
  "avicontrol.vaciado-sanitario.dias": null
}
```

Resultado:

```json
{
  "accion": "ninguna",
  "log": "WARN VaciadoSanitarioService avicontrol.vaciado-sanitario.dias sin configurar: no se ejecuta la transicion a DISPONIBLE"
}
```

**17. Falla un galpón (por ejemplo su estado cambió entre la consulta y el bloqueo)**

- **Llamada**: `VaciadoSanitarioService.finalizarPeriodosCumplidos()`

Argumentos:

```json
{
  "galponId": "f47ac10b-58cc-4372-a567-0e02b2c3d479"
}
```

Resultado:

```json
{
  "accion": "se continúa con los demás galpones",
  "log": "ERROR VaciadoSanitarioService galpon=f47ac10b-58cc-4372-a567-0e02b2c3d479 no se pudo devolver a DISPONIBLE: <mensaje de la excepción>"
}
```

## Código de implementación

Código completo de este caso de uso, tal como compila en el proyecto (paquete base `com.unimag.avicontrol`). Las clases compartidas por varios casos de uso (`GalponService`, `GalponController`, los mappers y los adaptadores) muestran aquí solo los métodos de este plan; el resto está en el plan de cada caso de uso.

**`src/main/java/com/unimag/avicontrol/application/port/in/RecibirVaciadoSanitarioUseCase.java`**

```java
package com.unimag.avicontrol.application.port.in;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface RecibirVaciadoSanitarioUseCase {

    void recibir(Comando comando);

    record Comando(UUID alertaId, UUID galponId, UUID loteId, OffsetDateTime fechaHoraEvento) {
    }
}
```

**`src/main/java/com/unimag/avicontrol/application/port/in/FinalizarVaciadoSanitarioUseCase.java`**

```java
package com.unimag.avicontrol.application.port.in;

public interface FinalizarVaciadoSanitarioUseCase {

    /** Devuelve a DISPONIBLE los galpones cuyo período de vaciado ya se cumplió. */
    void finalizarPeriodosCumplidos();
}
```

**`src/main/java/com/unimag/avicontrol/infrastructure/in/kafka/dto/VaciadoSanitarioMessage.java`**

```java
package com.unimag.avicontrol.infrastructure.in.kafka.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/** `payload` del evento de vaciado sanitario. */
public record VaciadoSanitarioMessage(UUID alertaId, UUID galponId, UUID loteId, OffsetDateTime fechaHoraEvento) {
}
```

**`src/main/java/com/unimag/avicontrol/infrastructure/in/kafka/VaciadoSanitarioConsumer.java`**

```java
package com.unimag.avicontrol.infrastructure.in.kafka;

import com.unimag.avicontrol.application.port.in.RecibirVaciadoSanitarioUseCase;
import com.unimag.avicontrol.domain.exception.NegocioException;
import com.unimag.avicontrol.infrastructure.in.kafka.dto.EventoEnvelope;
import com.unimag.avicontrol.infrastructure.in.kafka.dto.VaciadoSanitarioMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;

@Component
public class VaciadoSanitarioConsumer {

    static final String TOPIC = "avicontrol.vaciado-sanitario";
    static final String EVENT_TYPE = "VACIADO_SANITARIO_SOLICITADO";
    private static final TypeReference<EventoEnvelope<VaciadoSanitarioMessage>> TIPO = new TypeReference<>() {
    };
    private static final Logger log = LoggerFactory.getLogger(VaciadoSanitarioConsumer.class);

    private final LectorEventos lector;
    private final RecibirVaciadoSanitarioUseCase useCase;

    public VaciadoSanitarioConsumer(LectorEventos lector, RecibirVaciadoSanitarioUseCase useCase) {
        this.lector = lector;
        this.useCase = useCase;
    }

    @KafkaListener(topics = TOPIC)
    public void consumir(String json) {
        VaciadoSanitarioMessage m = null;
        try {
            m = lector.leer(json, TIPO, EVENT_TYPE).payload();
            useCase.recibir(new RecibirVaciadoSanitarioUseCase.Comando(m.alertaId(), m.galponId(), m.loteId(),
                    m.fechaHoraEvento()));
        } catch (NegocioException e) {
            log.warn("rechazada alertaId={} galponId={} codigo={} mensaje=\"{}\"", m == null ? null : m.alertaId(),
                    m == null ? null : m.galponId(), e.codigo(), e.getMessage());
        }
    }
}
```

**`src/main/java/com/unimag/avicontrol/application/usecase/VaciadoSanitarioService.java`**

```java
package com.unimag.avicontrol.application.usecase;

import com.unimag.avicontrol.application.port.in.FinalizarVaciadoSanitarioUseCase;
import com.unimag.avicontrol.application.port.in.RecibirVaciadoSanitarioUseCase;
import com.unimag.avicontrol.domain.exception.ConflictoException;
import com.unimag.avicontrol.domain.exception.RecursoNoEncontradoException;
import com.unimag.avicontrol.domain.exception.ValidacionNegocioException;
import com.unimag.avicontrol.domain.model.AlertaVaciadoSanitario;
import com.unimag.avicontrol.domain.model.EstadoGalpon;
import com.unimag.avicontrol.domain.model.Galpon;
import com.unimag.avicontrol.domain.model.Lote;
import com.unimag.avicontrol.domain.model.OrigenCambioEstado;
import com.unimag.avicontrol.domain.policy.ReglasFecha;
import com.unimag.avicontrol.domain.port.out.AlertaVaciadoSanitarioRepository;
import com.unimag.avicontrol.domain.port.out.GalponRepository;
import com.unimag.avicontrol.domain.port.out.LoteRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VaciadoSanitarioService implements RecibirVaciadoSanitarioUseCase, FinalizarVaciadoSanitarioUseCase {

    private static final Logger log = LoggerFactory.getLogger(VaciadoSanitarioService.class);
    private static final String GALPON_NO_EN_COSECHA = "El galpón no está en estado EN_COSECHA.";

    private final GalponRepository galpones;
    private final LoteRepository lotes;
    private final AlertaVaciadoSanitarioRepository alertas;
    private final GalponEstadoService estados;
    private final Clock clock;
    private final Integer diasVaciado;

    public VaciadoSanitarioService(GalponRepository galpones, LoteRepository lotes,
                                   AlertaVaciadoSanitarioRepository alertas, GalponEstadoService estados, Clock clock,
                                   @Value("${avicontrol.vaciado-sanitario.dias:#{null}}") Integer diasVaciado) {
        this.galpones = galpones;
        this.lotes = lotes;
        this.alertas = alertas;
        this.estados = estados;
        this.clock = clock;
        this.diasVaciado = diasVaciado;
    }

    /**
     * Una sola transacción (FR-011): estado, alerta aceptada y desvinculación del lote se confirman juntos o no
     * se confirma nada. Primero se pide el cambio de estado, que valida bajo el bloqueo del galpón, para que dos
     * alertas distintas simultáneas no apliquen las dos.
     */
    @Override
    @Transactional
    public void recibir(Comando comando) {
        if (comando.alertaId() == null || comando.galponId() == null || comando.loteId() == null
                || comando.fechaHoraEvento() == null) {
            throw new ValidacionNegocioException("ALERTA_INCOMPLETA", "Falta un campo obligatorio de la alerta.");
        }
        Instant momento = comando.fechaHoraEvento().toInstant();

        var previa = alertas.findById(comando.alertaId());
        if (previa.isPresent()) {
            if (previa.get().mismosDatos(comando.galponId(), comando.loteId(), momento)) {
                log.info("ignorada alertaId={} motivo=YA_PROCESADA", comando.alertaId());
                return;
            }
            throw new ConflictoException("ALERTA_INCONSISTENTE", "El alertaId ya fue procesado con datos distintos.");
        }

        Galpon galpon = galpones.findById(comando.galponId()).orElseThrow(() ->
                new RecursoNoEncontradoException("GALPON_NO_ENCONTRADO", "El galpón de la alerta no existe."));
        Lote lote = lotes.findById(comando.loteId()).orElseThrow(() ->
                new RecursoNoEncontradoException("LOTE_NO_ENCONTRADO", "El lote de la alerta no existe."));
        if (!lote.galponId().equals(galpon.id())) {
            throw new ValidacionNegocioException("LOTE_NO_PERTENECE_AL_GALPON",
                    "El lote no corresponde al galpón de la alerta.");
        }
        if (galpon.estado() != EstadoGalpon.EN_COSECHA) {
            throw new ValidacionNegocioException("GALPON_NO_EN_COSECHA", GALPON_NO_EN_COSECHA);
        }
        if (ReglasFecha.esFutura(comando.fechaHoraEvento(), clock)
                || !ReglasFecha.esDeHoy(comando.fechaHoraEvento(), clock)) {
            throw new ValidacionNegocioException("FECHA_INVALIDA",
                    "La fecha del evento es futura o corresponde a otro día.");
        }

        try {
            estados.solicitarCambio(galpon.id(), EstadoGalpon.EN_COSECHA, EstadoGalpon.VACIADO_SANITARIO,
                    OrigenCambioEstado.ALERTA_VACIADO_SANITARIO);
        } catch (ConflictoException e) {
            if ("ESTADO_ESPERADO_CAMBIADO".equals(e.codigo())) {
                throw new ValidacionNegocioException("GALPON_NO_EN_COSECHA", GALPON_NO_EN_COSECHA);
            }
            throw e;
        }
        Instant ahora = clock.instant();
        alertas.save(new AlertaVaciadoSanitario(comando.alertaId(), galpon.id(), lote.id(), momento, ahora, "ACEPTADA"));
        lotes.save(lote.desvincular(ahora));
    }

    /**
     * Cada galpón se procesa en su propia transacción (la de GalponEstadoService): el fallo de uno no frena a los
     * demás. Sin período configurado no se ejecuta ninguna transición (FR-015, P-02).
     */
    @Override
    public void finalizarPeriodosCumplidos() {
        if (diasVaciado == null) {
            log.warn("avicontrol.vaciado-sanitario.dias sin configurar: no se ejecuta la transicion a DISPONIBLE");
            return;
        }
        Instant limite = clock.instant().minus(Duration.ofDays(diasVaciado));
        for (UUID galponId : alertas.findGalponIdsConVaciadoCumplido(limite)) {
            try {
                estados.solicitarCambio(galponId, EstadoGalpon.VACIADO_SANITARIO, EstadoGalpon.DISPONIBLE,
                        OrigenCambioEstado.PROCESO_AUTOMATICO);
                log.info("galpon={} VACIADO_SANITARIO -> DISPONIBLE por periodo cumplido", galponId);
            } catch (RuntimeException e) {
                log.error("galpon={} no se pudo devolver a DISPONIBLE: {}", galponId, e.getMessage());
            }
        }
    }
}
```

**`src/main/java/com/unimag/avicontrol/infrastructure/in/job/VaciadoSanitarioJob.java`**

```java
package com.unimag.avicontrol.infrastructure.in.job;

import com.unimag.avicontrol.application.port.in.FinalizarVaciadoSanitarioUseCase;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Adaptador de entrada programado: solo activa el caso de uso, sin reglas de negocio. Una sola instancia (una granja). */
@Component
public class VaciadoSanitarioJob {

    private final FinalizarVaciadoSanitarioUseCase useCase;

    public VaciadoSanitarioJob(FinalizarVaciadoSanitarioUseCase useCase) {
        this.useCase = useCase;
    }

    /** Por defecto cada hora; se cambia con `avicontrol.vaciado-sanitario.cron`. */
    @Scheduled(cron = "${avicontrol.vaciado-sanitario.cron:0 0 * * * *}", zone = "America/Bogota")
    public void ejecutar() {
        useCase.finalizarPeriodosCumplidos();
    }
}
```

**`src/main/java/com/unimag/avicontrol/infrastructure/out/persistence/repository/AlertaVaciadoSanitarioJpaRepository.java`**

```java
package com.unimag.avicontrol.infrastructure.out.persistence.repository;

import com.unimag.avicontrol.infrastructure.out.persistence.entity.AlertaVaciadoSanitarioEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlertaVaciadoSanitarioJpaRepository extends JpaRepository<AlertaVaciadoSanitarioEntity, UUID> {

    /** Se compara con la última alerta aceptada del galpón: un galpón puede haber pasado por varios vaciados. */
    @Query(value = """
            SELECT g.id
            FROM galpon g
            WHERE g.estado = 'VACIADO_SANITARIO'
              AND (SELECT max(a.fecha_hora_recepcion)
                   FROM alerta_vaciado_sanitario a
                   WHERE a.galpon_id = g.id) <= :limite
            """, nativeQuery = true)
    List<UUID> findGalponIdsConVaciadoCumplido(@Param("limite") Instant limite);
}
```

**`src/main/java/com/unimag/avicontrol/infrastructure/out/persistence/adapter/AlertaVaciadoPersistenceAdapter.java`**

```java
package com.unimag.avicontrol.infrastructure.out.persistence.adapter;

import com.unimag.avicontrol.domain.model.AlertaVaciadoSanitario;
import com.unimag.avicontrol.domain.port.out.AlertaVaciadoSanitarioRepository;
import com.unimag.avicontrol.infrastructure.out.persistence.mapper.AlertaPersistenceMapper;
import com.unimag.avicontrol.infrastructure.out.persistence.repository.AlertaVaciadoSanitarioJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class AlertaVaciadoPersistenceAdapter implements AlertaVaciadoSanitarioRepository {

    private final AlertaVaciadoSanitarioJpaRepository jpa;

    public AlertaVaciadoPersistenceAdapter(AlertaVaciadoSanitarioJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public AlertaVaciadoSanitario save(AlertaVaciadoSanitario alerta) {
        return AlertaPersistenceMapper.aDominio(jpa.saveAndFlush(AlertaPersistenceMapper.aEntidad(alerta)));
    }

    @Override
    public Optional<AlertaVaciadoSanitario> findById(UUID alertaId) {
        return jpa.findById(alertaId).map(AlertaPersistenceMapper::aDominio);
    }

    @Override
    public List<UUID> findGalponIdsConVaciadoCumplido(Instant limite) {
        return jpa.findGalponIdsConVaciadoCumplido(limite);
    }
}
```

## Fuera de alcance

- No escribe el estado del galpón: lo solicita a Actualizar estado.
- No modifica el resto de atributos del galpón ni los datos propios del lote.
- Las alertas rechazadas no se guardan como alertas aceptadas (FR-017).

## Coherencia entre módulos

El Módulo 1 es **consumidor**; el Módulo 2 emite la alerta al terminar la cosecha. A diferencia de la sanitaria, este spec sí guarda la alerta.

| Aspecto | Qué define este plan | A acordar con |
|---|---|---|
| Forma del mensaje | Igual que mortalidad: sobre estándar del Módulo 2 con los 4 campos en `payload`. | Módulo 2 |
| Idempotencia | `alertaId` como clave de la alerta aceptada; reentregas se ignoran, datos distintos se rechazan. | Módulo 2 |
| Precondición entre módulos | El galpón debe estar EN_COSECHA en el Módulo 1 (lo cambia el administrador). Si el Módulo 2 emite antes, el Módulo 1 rechaza el mensaje. El Módulo 2 debe emitir solo después de ver ese estado. | Módulo 2 |
| Efecto en el Módulo 2 | Al aceptar, el lote queda desvinculado: la consulta de lote actual del Módulo 1 devuelve ninguno. El Módulo 2 no debe enviar mortalidad ni alertas sanitarias de ese lote. | Módulo 2 |
| Regreso a DISPONIBLE | Lo hace el job del Módulo 1 sin enviar evento al Módulo 2; este lo verá al consultar. Si necesita enterarse en tiempo real, el Módulo 1 tendría que publicar un evento, que hoy ningún spec pide. | Módulos 1 y 2 |
| Topic y partición | `avicontrol.vaciado-sanitario`, clave `galponId`. | Módulo 2 |

Pendientes relacionados: P-02 (período de vaciado), P-05 (rechazos y reintentos) y P-06 (sobre y nombres de campo).

**Evidencia de coherencia:** `VaciadoSanitarioConsumerTest` (T095) deserializa el fixture `vaciado-sanitario-payload.json`; es el mismo JSON que se entrega al Módulo 2.

## Fixtures

En `src/test/resources/contratos/` (es un `payload`; el sobre es el de `mortalidad-evento.json`):

| Archivo | Contenido |
|---|---|
| `vaciado-sanitario-payload.json` | El mensaje de ejemplo del contrato |

## Tareas

**Goal**: El Modulo 2 publica un evento Kafka de vaciado; el galpon (En cosecha) pasa a Vaciado sanitario, el lote se desvincula y, tras el periodo configurado, vuelve a Disponible automaticamente.

**Independent Test**: mensaje Kafka para galpon En cosecha -> VACIADO_SANITARIO, lote desvinculado, alerta guardada; mensaje repetido es idempotente; periodo cumplido -> job lo deja DISPONIBLE.

### Tests

- [ ] T062 [P] [US7] Pruebas unitarias de VaciadoSanitarioService (Mockito) en test/application/: galpon En cosecha aceptado; otros estados rechazados; UUID repetido idempotente; UUID con datos distintos rechazado; registro + desvinculacion + cambio de estado en una sola operacion; `finalizarPeriodosCumplidos`: con el período sin configurar no solicita ninguna transición y deja el aviso en el log, y el fallo de un galpón no impide procesar los demás — clase `VaciadoSanitarioServiceTest` (FR-003, FR-006, FR-011 a FR-017; SC-003 a SC-005)
- [ ] T095 [P] [US7] Prueba unitaria de VaciadoSanitarioConsumer (Mockito) en test/infrastructure/kafka/: deserializa el fixture `vaciado-sanitario-payload.json` dentro del sobre y delega en RecibirVaciadoSanitarioUseCase sin lógica de negocio — clase `VaciadoSanitarioConsumerTest` (FR-001, FR-002)
- [ ] T096 [P] [US7] Prueba unitaria de VaciadoSanitarioJob (Mockito) en test/infrastructure/job/: en cada ejecución invoca una sola vez `FinalizarVaciadoSanitarioUseCase.finalizarPeriodosCumplidos` y no contiene lógica de negocio (la lógica está en `VaciadoSanitarioService` y se prueba en T062) — clase `VaciadoSanitarioJobTest` (FR-014, FR-015; SC-006)

### Implementation

- [ ] T064 [P] [US7] Crear VaciadoSanitarioMessage en main/infrastructure/in/kafka/dto/ (FR-002)
- [ ] T065 [P] [US7] Crear RecibirVaciadoSanitarioUseCase en main/application/port/in/ (FR-001)
- [ ] T066 [US7] Implementar VaciadoSanitarioService.recibir en main/application/usecase/: aceptar solo EN_COSECHA, guardar alerta con UUID, marcar desvinculado_en en el lote, solicitar EN_COSECHA -> VACIADO_SANITARIO (FR-003 a FR-013)
- [ ] T067 [US7] Implementar VaciadoSanitarioService.finalizarPeriodosCumplidos y VaciadoSanitarioJob (@Scheduled) en main/infrastructure/in/job/; habilitar @EnableScheduling (FR-014, FR-015; SC-006)
- [ ] T068 [US7] Implementar VaciadoSanitarioConsumer (@KafkaListener) en main/infrastructure/in/kafka/, leyendo `EventoEnvelope<VaciadoSanitarioMessage>`. Topic: avicontrol.vaciado-sanitario (FR-001)

**Checkpoint**: el ciclo completo del galpon se cierra y vuelve a Disponible.

## Dependencias

- Necesita un galpón EN_COSECHA creado con datos de prueba.
- Orden interno: modelo y entidad -> puerto de entrada -> caso de uso -> adaptador de entrada (REST o Kafka) -> pruebas.

## Pendientes de clarificación

- P-02: cuántos días dura el período y desde cuándo se cuenta. El plan propone contarlo desde la recepción de la alerta aceptada (`fechaHoraRecepcion`). Hasta definirlo, el job no hace nada (FR-015).
- P-05: rechazo hacia el Módulo 2 y reintentos.
- P-06: sobre de eventos y valores de `eventType`.
