# Implementation Plan: Recibir alerta sanitaria

**Fecha**: 2026-10-02
**Spec**: [recibirAlertaSanitaria.md](recibirAlertaSanitaria.md)
**Arquitectura y tecnologías**: [arquitectura.md](../arquitectura/arquitectura.md)
**Plan general**: [plan-modulo-1.md](../plan-general/plan-modulo-1.md) (historia US6, prioridad P1)

## Summary

El Módulo 2 publica un evento Kafka de alerta sanitaria; el Módulo 1 aísla el galpón PRODUCTIVO o lo reanuda desde AISLAMIENTO, sin guardar la alerta.

**Enfoque técnico:**

1. `AlertaSanitariaConsumer` (tópico `avicontrol.alerta-sanitaria`) deserializa `EventoEnvelope<AlertaSanitariaMessage>` y delega en `RecibirAlertaSanitariaUseCase` (FR-001).
2. `AlertaSanitariaService.recibir` valida la fecha del día (FR-005), que AISLAMIENTO traiga tipo de enfermedad y descripción (FR-006) y que llegue galpón o lote; resuelve el galpón directamente o por el lote activo (FR-002, FR-003, FR-009).
3. Según la acción arma la solicitud: AISLAMIENTO pide PRODUCTIVO -> AISLAMIENTO; REANUDACION pide AISLAMIENTO -> PRODUCTIVO (FR-010, FR-011). El estado esperado de la solicitud es el que corresponde a la acción.
4. Remite la solicitud a `GalponEstadoService` con origen `ALERTA_SANITARIA_MODULO_2`; si el estado vigente no es el esperado, rechaza la alerta (FR-012, FR-013).
5. No guarda la alerta ni modifica el lote (FR-004, FR-014, FR-015). Todo rechazo queda en el log.

## Contexto específico

- **Tareas previas del plan general**: T014 (`KafkaConsumerConfig`), T015-T016 (transiciones), T086 (`FixtureJson`), T087 (`EventoEnvelope`).

## Estructura del proyecto

```text
src/main/java/com/unimag/avicontrol/
├── application/
│   ├── port/in/RecibirAlertaSanitariaUseCase.java       # T059
│   └── usecase/AlertaSanitariaService.java               # T060
└── infrastructure/in/kafka/
    ├── AlertaSanitariaConsumer.java                      # T061
    └── dto/AlertaSanitariaMessage.java                   # T058

src/test/java/com/unimag/avicontrol/
├── application/AlertaSanitariaServiceTest.java           # T056
└── infrastructure/in/kafka/AlertaSanitariaConsumerTest.java   # T094
```

Además de los archivos del árbol, el código completo de este caso de uso incluye: `AccionSanitaria.java`. Están en la sección *Código de implementación*.

`EventoEnvelope` (T087), `KafkaConsumerConfig` (T014) y `GalponEstadoService` (T016) ya existen por el plan general.

## Decisiones de diseño

**Sin registro de procesadas.** El spec prohíbe guardar la alerta (FR-004), así que no hay `RegistroProcesamiento`. La idempotencia es natural por estado: una reentrega de AISLAMIENTO encuentra el galpón ya en AISLAMIENTO y se rechaza como `ACCION_INCOMPATIBLE`. Ese rechazo es esperado: se registra en nivel `INFO`, sin reintento ni dead-letter, y no se trata como falla técnica.

**Estado esperado en la solicitud.** Pasar el estado esperado a `GalponEstadoService` hace que la validación contra el estado vigente ocurra bajo el bloqueo del galpón (FR-013). Si el administrador cambió el estado entre la lectura y la solicitud, `ESTADO_ESPERADO_CAMBIADO` se traduce a `ACCION_INCOMPATIBLE`.

**Galpón o lote.** Si llegan los dos, deben coincidir (`LOTE_NO_PERTENECE_AL_GALPON` si no). Si llega solo el lote, debe ser un lote activo y de él se resuelve el galpón (FR-009). Si no llega ninguno, la alerta se rechaza.

**Datos que no se validan.** `tipoEnfermedad`, `descripcion` y `gravedad` no se validan contra un catálogo ni se guardan; el Módulo 1 solo exige que los dos primeros existan en AISLAMIENTO (FR-006, FR-007).

**Orden por galpón.** La clave de partición es `galponId` aunque la alerta nazca de un lote, para que AISLAMIENTO y REANUDACION del mismo galpón se procesen en orden.

**Clasificación de errores.** Igual que en mortalidad: los de negocio no se reintentan y los técnicos sí (P-05).

## Contrato

> Los nombres de campo son propuestos (P-06) y deben acordarse con el Módulo 2 (ver *Coherencia entre módulos*). El JSON de ejemplo es el `payload` del [sobre de eventos](../arquitectura/arquitectura.md#sobre-de-eventos).

### Mensaje Kafka — Alerta sanitaria

| Elemento | Definición |
|---|---|
| Topic | `avicontrol.alerta-sanitaria` |
| Productor / consumidor | Módulo 2 / Módulo 1 (grupo `modulo-1`) |
| Clave de partición | `galponId` (si la alerta viene por lote, la clave sigue siendo el galpón) |
| Mensaje (request) | `AlertaSanitariaMessage` (JSON) |
| Respuesta | Ninguna. Éxito: el mensaje se confirma. Rechazo: se registra en el log y sigue la política de errores del consumer (P-05). |

**Campos del mensaje**

| Campo | Tipo | Obligatorio | Regla |
|---|---|---|---|
| alertaId | UUID | sí | Identificador generado por el Módulo 2; el Módulo 1 no la guarda |
| accion | string | sí | `AISLAMIENTO` o `REANUDACION` |
| galponId | UUID | uno de los dos | Galpón afectado |
| loteId | UUID | uno de los dos | Debe ser lote activo; se resuelve el galpón por el lote. Si vienen ambos, deben coincidir |
| fechaHora | datetime | sí | Debe ser del día actual; la hora puede variar |
| tipoEnfermedad | string | solo con AISLAMIENTO | Tipo de enfermedad o sospecha |
| descripcion | string | solo con AISLAMIENTO | Descripción de la novedad |
| gravedad | string | no | Si no viene se conserva vacía |

**Resultados**

| Caso (spec) | Resultado |
|---|---|
| AISLAMIENTO con galpón PRODUCTIVO | Remite PRODUCTIVO -> AISLAMIENTO a `GalponEstadoService`, origen `ALERTA_SANITARIA_MODULO_2` |
| REANUDACION con galpón en AISLAMIENTO | Remite AISLAMIENTO -> PRODUCTIVO, origen `ALERTA_SANITARIA_MODULO_2` |
| Galpón o lote inexistente; lote histórico o no activo | Rechazado |
| Acción que no corresponde al estado del galpón | Rechazado |
| Sin galpón ni lote; fecha de otro día; falta tipo o descripción en AISLAMIENTO | Rechazado |

Un rechazo no cambia el estado del galpón ni deja registros propios (FR-015). El Módulo 1 no modifica población, fecha, costo ni demás datos del lote.

Ejemplo de mensaje (`payload`):

```json
{
  "alertaId": "5b1c7d52-3a08-4f0e-9c1d-77a1d4e0b6aa",
  "accion": "AISLAMIENTO",
  "galponId": "550e8400-e29b-41d4-a716-446655440000",
  "fechaHora": "2026-10-05T09:15:00-05:00",
  "tipoEnfermedad": "Sospecha de Newcastle",
  "descripcion": "Mortalidad atípica y signos respiratorios"
}
```

### Códigos de rechazo

| Código | Mensaje (log y, si se adopta, respuesta al Módulo 2) |
|---|---|
| `ALERTA_INCOMPLETA` | Falta un campo obligatorio de la alerta. |
| `VERSION_NO_SOPORTADA` | La versión del evento no es soportada. |
| `ACCION_INVALIDA` | La acción debe ser AISLAMIENTO o REANUDACION. |
| `GALPON_O_LOTE_REQUERIDO` | La alerta debe identificar un galpón o un lote. |
| `DATOS_AISLAMIENTO_INCOMPLETOS` | Una alerta de aislamiento requiere tipo de enfermedad y descripción. |
| `FECHA_INVALIDA` | La fecha de la alerta no corresponde al día actual. |
| `GALPON_NO_ENCONTRADO` | El galpón de la alerta no existe. |
| `LOTE_NO_ENCONTRADO` | El lote de la alerta no existe. |
| `LOTE_NO_ACTIVO` | El lote no está activo. |
| `LOTE_NO_PERTENECE_AL_GALPON` | El lote no corresponde al galpón de la alerta. |
| `ACCION_INCOMPATIBLE` | La acción no corresponde al estado actual del galpón. |
| `MENSAJE_ILEGIBLE` | El mensaje no es un JSON válido o tiene campos con tipos incorrectos. |
| `TIPO_EVENTO_NO_SOPORTADO` | El tipo de evento no corresponde a este tópico. |

Línea de log de un rechazo esperado (reentrega):

```text
INFO AlertaSanitariaConsumer rechazada alertaId=5b1c7d52-3a08-4f0e-9c1d-77a1d4e0b6aa galponId=550e8400-e29b-41d4-a716-446655440000 loteId=null codigo=ACCION_INCOMPATIBLE mensaje="La acción no corresponde al estado actual del galpón."
```

## Peticiones y respuestas por escenario

Es un mensaje Kafka: no hay path ni query params ni respuesta HTTP. Cada escenario muestra el mensaje completo (sobre + `payload`) y su resultado. Los números son los de este documento; los escenarios del spec son los marcados.

**1. Escenario 1: AISLAMIENTO con el galpón PRODUCTIVO**

- **Topic**: `avicontrol.alerta-sanitaria`
- **Clave de partición**: `550e8400-e29b-41d4-a716-446655440000`
- **Cabecera/sobre**: `eventType=ALERTA_SANITARIA_EMITIDA`, `eventVersion=1`

Valor del mensaje (sobre del Módulo 2 con el `payload` de este escenario):

```json
{
  "eventId": "1a4d7e90-3c2b-4f68-8d05-9b7e6c2a1f34",
  "eventType": "ALERTA_SANITARIA_EMITIDA",
  "eventVersion": 1,
  "occurredAt": "2026-10-05T14:30:00-05:00",
  "publishedAt": "2026-10-05T14:30:02-05:00",
  "producer": "modulo-2",
  "aggregateType": "Galpon",
  "aggregateId": "550e8400-e29b-41d4-a716-446655440000",
  "payload": {
    "alertaId": "5b1c7d52-3a08-4f0e-9c1d-77a1d4e0b6aa",
    "accion": "AISLAMIENTO",
    "galponId": "550e8400-e29b-41d4-a716-446655440000",
    "fechaHora": "2026-10-05T09:15:00-05:00",
    "tipoEnfermedad": "Sospecha de Newcastle",
    "descripcion": "Mortalidad atípica y signos respiratorios"
  }
}
```

**Resultado** (el Módulo 1 no responde al productor):

- Se solicita `PRODUCTIVO -> AISLAMIENTO` (origen `ALERTA_SANITARIA_MODULO_2`) a `GalponEstadoService`: queda una fila en el historial de estados.
- No se guarda la alerta ni se tocan los datos del lote.

**2. Escenario 2: REANUDACION con el galpón en AISLAMIENTO**

- **Topic**: `avicontrol.alerta-sanitaria`
- **Clave de partición**: `550e8400-e29b-41d4-a716-446655440000`
- **Cabecera/sobre**: `eventType=ALERTA_SANITARIA_EMITIDA`, `eventVersion=1`
- **Nota**: `tipoEnfermedad` y `descripcion` no se exigen en la reanudación.

Valor del mensaje (sobre del Módulo 2 con el `payload` de este escenario):

```json
{
  "eventId": "1a4d7e90-3c2b-4f68-8d05-9b7e6c2a1f34",
  "eventType": "ALERTA_SANITARIA_EMITIDA",
  "eventVersion": 1,
  "occurredAt": "2026-10-05T14:30:00-05:00",
  "publishedAt": "2026-10-05T14:30:02-05:00",
  "producer": "modulo-2",
  "aggregateType": "Galpon",
  "aggregateId": "550e8400-e29b-41d4-a716-446655440000",
  "payload": {
    "alertaId": "8e2f6a13-7b4c-4d90-a1e5-2c9d0b3f5a67",
    "accion": "REANUDACION",
    "galponId": "550e8400-e29b-41d4-a716-446655440000",
    "fechaHora": "2026-10-05T15:00:00-05:00"
  }
}
```

**Resultado** (el Módulo 1 no responde al productor):

- Se solicita `AISLAMIENTO -> PRODUCTIVO` (origen `ALERTA_SANITARIA_MODULO_2`).

**3. Alerta identificada por lote (sin `galponId`)**

- **Topic**: `avicontrol.alerta-sanitaria`
- **Clave de partición**: `550e8400-e29b-41d4-a716-446655440000`
- **Cabecera/sobre**: `eventType=ALERTA_SANITARIA_EMITIDA`, `eventVersion=1`
- **Nota**: La clave de partición sigue siendo el `galponId`. Si llegan galpón y lote, deben coincidir.

Valor del mensaje (sobre del Módulo 2 con el `payload` de este escenario):

```json
{
  "eventId": "1a4d7e90-3c2b-4f68-8d05-9b7e6c2a1f34",
  "eventType": "ALERTA_SANITARIA_EMITIDA",
  "eventVersion": 1,
  "occurredAt": "2026-10-05T14:30:00-05:00",
  "publishedAt": "2026-10-05T14:30:02-05:00",
  "producer": "modulo-2",
  "aggregateType": "Galpon",
  "aggregateId": "550e8400-e29b-41d4-a716-446655440000",
  "payload": {
    "alertaId": "5b1c7d52-3a08-4f0e-9c1d-77a1d4e0b6aa",
    "accion": "AISLAMIENTO",
    "fechaHora": "2026-10-05T09:15:00-05:00",
    "tipoEnfermedad": "Sospecha de Newcastle",
    "descripcion": "Mortalidad atípica y signos respiratorios",
    "loteId": "2fc03a21-84c4-4c83-bb15-6607bb75cb90"
  }
}
```

**Resultado** (el Módulo 1 no responde al productor):

- El galpón se resuelve a partir del lote activo y se solicita `PRODUCTIVO -> AISLAMIENTO`.

**4. Escenario 3: acción que no corresponde al estado (reentrega de AISLAMIENTO con el galpón ya aislado)**

- **Topic**: `avicontrol.alerta-sanitaria`
- **Clave de partición**: `550e8400-e29b-41d4-a716-446655440000`
- **Cabecera/sobre**: `eventType=ALERTA_SANITARIA_EMITIDA`, `eventVersion=1`
- **Nota**: Es el rechazo esperado de una reentrega: nivel `INFO`, sin reintento.

Valor del mensaje (sobre del Módulo 2 con el `payload` de este escenario):

```json
{
  "eventId": "1a4d7e90-3c2b-4f68-8d05-9b7e6c2a1f34",
  "eventType": "ALERTA_SANITARIA_EMITIDA",
  "eventVersion": 1,
  "occurredAt": "2026-10-05T14:30:00-05:00",
  "publishedAt": "2026-10-05T14:30:02-05:00",
  "producer": "modulo-2",
  "aggregateType": "Galpon",
  "aggregateId": "550e8400-e29b-41d4-a716-446655440000",
  "payload": {
    "alertaId": "5b1c7d52-3a08-4f0e-9c1d-77a1d4e0b6aa",
    "accion": "AISLAMIENTO",
    "galponId": "550e8400-e29b-41d4-a716-446655440000",
    "fechaHora": "2026-10-05T09:15:00-05:00",
    "tipoEnfermedad": "Sospecha de Newcastle",
    "descripcion": "Mortalidad atípica y signos respiratorios"
  }
}
```

**Resultado** (el Módulo 1 no responde al productor):

- Rechazo de negocio: el estado del galpón no cambia y no queda ningún registro. **No se reintenta**.
- **Log** (`INFO`): `INFO AlertaSanitariaConsumer rechazada alertaId=5b1c7d52-3a08-4f0e-9c1d-77a1d4e0b6aa galponId=550e8400-e29b-41d4-a716-446655440000 loteId=null codigo=ACCION_INCOMPATIBLE mensaje="La acción no corresponde al estado actual del galpón."`

**5. Acción distinta de AISLAMIENTO o REANUDACION**

- **Topic**: `avicontrol.alerta-sanitaria`
- **Clave de partición**: `550e8400-e29b-41d4-a716-446655440000`
- **Cabecera/sobre**: `eventType=ALERTA_SANITARIA_EMITIDA`, `eventVersion=1`

Valor del mensaje (sobre del Módulo 2 con el `payload` de este escenario):

```json
{
  "eventId": "1a4d7e90-3c2b-4f68-8d05-9b7e6c2a1f34",
  "eventType": "ALERTA_SANITARIA_EMITIDA",
  "eventVersion": 1,
  "occurredAt": "2026-10-05T14:30:00-05:00",
  "publishedAt": "2026-10-05T14:30:02-05:00",
  "producer": "modulo-2",
  "aggregateType": "Galpon",
  "aggregateId": "550e8400-e29b-41d4-a716-446655440000",
  "payload": {
    "alertaId": "5b1c7d52-3a08-4f0e-9c1d-77a1d4e0b6aa",
    "accion": "CUARENTENA",
    "galponId": "550e8400-e29b-41d4-a716-446655440000",
    "fechaHora": "2026-10-05T09:15:00-05:00",
    "tipoEnfermedad": "Sospecha de Newcastle",
    "descripcion": "Mortalidad atípica y signos respiratorios"
  }
}
```

**Resultado** (el Módulo 1 no responde al productor):

- Rechazo de negocio: el estado del galpón no cambia y no queda ningún registro. **No se reintenta**.
- **Log** (`WARN`): `WARN AlertaSanitariaConsumer rechazada alertaId=5b1c7d52-3a08-4f0e-9c1d-77a1d4e0b6aa galponId=550e8400-e29b-41d4-a716-446655440000 loteId=null codigo=ACCION_INVALIDA mensaje="La acción debe ser AISLAMIENTO o REANUDACION."`

**6. Sin galpón ni lote**

- **Topic**: `avicontrol.alerta-sanitaria`
- **Clave de partición**: `550e8400-e29b-41d4-a716-446655440000`
- **Cabecera/sobre**: `eventType=ALERTA_SANITARIA_EMITIDA`, `eventVersion=1`

Valor del mensaje (sobre del Módulo 2 con el `payload` de este escenario):

```json
{
  "eventId": "1a4d7e90-3c2b-4f68-8d05-9b7e6c2a1f34",
  "eventType": "ALERTA_SANITARIA_EMITIDA",
  "eventVersion": 1,
  "occurredAt": "2026-10-05T14:30:00-05:00",
  "publishedAt": "2026-10-05T14:30:02-05:00",
  "producer": "modulo-2",
  "aggregateType": "Galpon",
  "aggregateId": "550e8400-e29b-41d4-a716-446655440000",
  "payload": {
    "alertaId": "5b1c7d52-3a08-4f0e-9c1d-77a1d4e0b6aa",
    "accion": "AISLAMIENTO",
    "fechaHora": "2026-10-05T09:15:00-05:00",
    "tipoEnfermedad": "Sospecha de Newcastle",
    "descripcion": "Mortalidad atípica y signos respiratorios"
  }
}
```

**Resultado** (el Módulo 1 no responde al productor):

- Rechazo de negocio: el estado del galpón no cambia y no queda ningún registro. **No se reintenta**.
- **Log** (`WARN`): `WARN AlertaSanitariaConsumer rechazada alertaId=5b1c7d52-3a08-4f0e-9c1d-77a1d4e0b6aa galponId=None loteId=null codigo=GALPON_O_LOTE_REQUERIDO mensaje="La alerta debe identificar un galpón o un lote."`

**7. AISLAMIENTO sin `tipoEnfermedad` ni `descripcion`**

- **Topic**: `avicontrol.alerta-sanitaria`
- **Clave de partición**: `550e8400-e29b-41d4-a716-446655440000`
- **Cabecera/sobre**: `eventType=ALERTA_SANITARIA_EMITIDA`, `eventVersion=1`

Valor del mensaje (sobre del Módulo 2 con el `payload` de este escenario):

```json
{
  "eventId": "1a4d7e90-3c2b-4f68-8d05-9b7e6c2a1f34",
  "eventType": "ALERTA_SANITARIA_EMITIDA",
  "eventVersion": 1,
  "occurredAt": "2026-10-05T14:30:00-05:00",
  "publishedAt": "2026-10-05T14:30:02-05:00",
  "producer": "modulo-2",
  "aggregateType": "Galpon",
  "aggregateId": "550e8400-e29b-41d4-a716-446655440000",
  "payload": {
    "alertaId": "5b1c7d52-3a08-4f0e-9c1d-77a1d4e0b6aa",
    "accion": "AISLAMIENTO",
    "galponId": "550e8400-e29b-41d4-a716-446655440000",
    "fechaHora": "2026-10-05T09:15:00-05:00"
  }
}
```

**Resultado** (el Módulo 1 no responde al productor):

- Rechazo de negocio: el estado del galpón no cambia y no queda ningún registro. **No se reintenta**.
- **Log** (`WARN`): `WARN AlertaSanitariaConsumer rechazada alertaId=5b1c7d52-3a08-4f0e-9c1d-77a1d4e0b6aa galponId=550e8400-e29b-41d4-a716-446655440000 loteId=null codigo=DATOS_AISLAMIENTO_INCOMPLETOS mensaje="Una alerta de aislamiento requiere tipo de enfermedad y descripción."`

**8. Fecha de otro día**

- **Topic**: `avicontrol.alerta-sanitaria`
- **Clave de partición**: `550e8400-e29b-41d4-a716-446655440000`
- **Cabecera/sobre**: `eventType=ALERTA_SANITARIA_EMITIDA`, `eventVersion=1`

Valor del mensaje (sobre del Módulo 2 con el `payload` de este escenario):

```json
{
  "eventId": "1a4d7e90-3c2b-4f68-8d05-9b7e6c2a1f34",
  "eventType": "ALERTA_SANITARIA_EMITIDA",
  "eventVersion": 1,
  "occurredAt": "2026-10-05T14:30:00-05:00",
  "publishedAt": "2026-10-05T14:30:02-05:00",
  "producer": "modulo-2",
  "aggregateType": "Galpon",
  "aggregateId": "550e8400-e29b-41d4-a716-446655440000",
  "payload": {
    "alertaId": "5b1c7d52-3a08-4f0e-9c1d-77a1d4e0b6aa",
    "accion": "AISLAMIENTO",
    "galponId": "550e8400-e29b-41d4-a716-446655440000",
    "fechaHora": "2026-10-04T09:15:00-05:00",
    "tipoEnfermedad": "Sospecha de Newcastle",
    "descripcion": "Mortalidad atípica y signos respiratorios"
  }
}
```

**Resultado** (el Módulo 1 no responde al productor):

- Rechazo de negocio: el estado del galpón no cambia y no queda ningún registro. **No se reintenta**.
- **Log** (`WARN`): `WARN AlertaSanitariaConsumer rechazada alertaId=5b1c7d52-3a08-4f0e-9c1d-77a1d4e0b6aa galponId=550e8400-e29b-41d4-a716-446655440000 loteId=null codigo=FECHA_INVALIDA mensaje="La fecha de la alerta no corresponde al día actual."`

**9. Falta un campo obligatorio (sin `alertaId`)**

- **Topic**: `avicontrol.alerta-sanitaria`
- **Clave de partición**: `550e8400-e29b-41d4-a716-446655440000`
- **Cabecera/sobre**: `eventType=ALERTA_SANITARIA_EMITIDA`, `eventVersion=1`

Valor del mensaje (sobre del Módulo 2 con el `payload` de este escenario):

```json
{
  "eventId": "1a4d7e90-3c2b-4f68-8d05-9b7e6c2a1f34",
  "eventType": "ALERTA_SANITARIA_EMITIDA",
  "eventVersion": 1,
  "occurredAt": "2026-10-05T14:30:00-05:00",
  "publishedAt": "2026-10-05T14:30:02-05:00",
  "producer": "modulo-2",
  "aggregateType": "Galpon",
  "aggregateId": "550e8400-e29b-41d4-a716-446655440000",
  "payload": {
    "accion": "AISLAMIENTO",
    "galponId": "550e8400-e29b-41d4-a716-446655440000",
    "fechaHora": "2026-10-05T09:15:00-05:00",
    "tipoEnfermedad": "Sospecha de Newcastle",
    "descripcion": "Mortalidad atípica y signos respiratorios"
  }
}
```

**Resultado** (el Módulo 1 no responde al productor):

- Rechazo de negocio: el estado del galpón no cambia y no queda ningún registro. **No se reintenta**.
- **Log** (`WARN`): `WARN AlertaSanitariaConsumer rechazada alertaId=None galponId=550e8400-e29b-41d4-a716-446655440000 loteId=null codigo=ALERTA_INCOMPLETA mensaje="Falta un campo obligatorio de la alerta."`

**10. Galpón inexistente**

- **Topic**: `avicontrol.alerta-sanitaria`
- **Clave de partición**: `550e8400-e29b-41d4-a716-446655440000`
- **Cabecera/sobre**: `eventType=ALERTA_SANITARIA_EMITIDA`, `eventVersion=1`

Valor del mensaje (sobre del Módulo 2 con el `payload` de este escenario):

```json
{
  "eventId": "1a4d7e90-3c2b-4f68-8d05-9b7e6c2a1f34",
  "eventType": "ALERTA_SANITARIA_EMITIDA",
  "eventVersion": 1,
  "occurredAt": "2026-10-05T14:30:00-05:00",
  "publishedAt": "2026-10-05T14:30:02-05:00",
  "producer": "modulo-2",
  "aggregateType": "Galpon",
  "aggregateId": "550e8400-e29b-41d4-a716-446655440000",
  "payload": {
    "alertaId": "5b1c7d52-3a08-4f0e-9c1d-77a1d4e0b6aa",
    "accion": "AISLAMIENTO",
    "galponId": "d9b2d63d-a233-4123-847a-3f2c0c1e6b55",
    "fechaHora": "2026-10-05T09:15:00-05:00",
    "tipoEnfermedad": "Sospecha de Newcastle",
    "descripcion": "Mortalidad atípica y signos respiratorios"
  }
}
```

**Resultado** (el Módulo 1 no responde al productor):

- Rechazo de negocio: el estado del galpón no cambia y no queda ningún registro. **No se reintenta**.
- **Log** (`WARN`): `WARN AlertaSanitariaConsumer rechazada alertaId=5b1c7d52-3a08-4f0e-9c1d-77a1d4e0b6aa galponId=d9b2d63d-a233-4123-847a-3f2c0c1e6b55 loteId=null codigo=GALPON_NO_ENCONTRADO mensaje="El galpón de la alerta no existe."`

**11. Lote inexistente**

- **Topic**: `avicontrol.alerta-sanitaria`
- **Clave de partición**: `550e8400-e29b-41d4-a716-446655440000`
- **Cabecera/sobre**: `eventType=ALERTA_SANITARIA_EMITIDA`, `eventVersion=1`

Valor del mensaje (sobre del Módulo 2 con el `payload` de este escenario):

```json
{
  "eventId": "1a4d7e90-3c2b-4f68-8d05-9b7e6c2a1f34",
  "eventType": "ALERTA_SANITARIA_EMITIDA",
  "eventVersion": 1,
  "occurredAt": "2026-10-05T14:30:00-05:00",
  "publishedAt": "2026-10-05T14:30:02-05:00",
  "producer": "modulo-2",
  "aggregateType": "Galpon",
  "aggregateId": "550e8400-e29b-41d4-a716-446655440000",
  "payload": {
    "alertaId": "5b1c7d52-3a08-4f0e-9c1d-77a1d4e0b6aa",
    "accion": "AISLAMIENTO",
    "fechaHora": "2026-10-05T09:15:00-05:00",
    "tipoEnfermedad": "Sospecha de Newcastle",
    "descripcion": "Mortalidad atípica y signos respiratorios",
    "loteId": "d9b2d63d-a233-4123-847a-3f2c0c1e6b55"
  }
}
```

**Resultado** (el Módulo 1 no responde al productor):

- Rechazo de negocio: el estado del galpón no cambia y no queda ningún registro. **No se reintenta**.
- **Log** (`WARN`): `WARN AlertaSanitariaConsumer rechazada alertaId=5b1c7d52-3a08-4f0e-9c1d-77a1d4e0b6aa galponId=None loteId=d9b2d63d-a233-4123-847a-3f2c0c1e6b55 codigo=LOTE_NO_ENCONTRADO mensaje="El lote de la alerta no existe."`

**12. Lote histórico (ya no es el activo del galpón)**

- **Topic**: `avicontrol.alerta-sanitaria`
- **Clave de partición**: `550e8400-e29b-41d4-a716-446655440000`
- **Cabecera/sobre**: `eventType=ALERTA_SANITARIA_EMITIDA`, `eventVersion=1`

Valor del mensaje (sobre del Módulo 2 con el `payload` de este escenario):

```json
{
  "eventId": "1a4d7e90-3c2b-4f68-8d05-9b7e6c2a1f34",
  "eventType": "ALERTA_SANITARIA_EMITIDA",
  "eventVersion": 1,
  "occurredAt": "2026-10-05T14:30:00-05:00",
  "publishedAt": "2026-10-05T14:30:02-05:00",
  "producer": "modulo-2",
  "aggregateType": "Galpon",
  "aggregateId": "550e8400-e29b-41d4-a716-446655440000",
  "payload": {
    "alertaId": "5b1c7d52-3a08-4f0e-9c1d-77a1d4e0b6aa",
    "accion": "AISLAMIENTO",
    "fechaHora": "2026-10-05T09:15:00-05:00",
    "tipoEnfermedad": "Sospecha de Newcastle",
    "descripcion": "Mortalidad atípica y signos respiratorios",
    "loteId": "9a1f4c70-2d3b-4a55-8e61-0b7f3c9d2e18"
  }
}
```

**Resultado** (el Módulo 1 no responde al productor):

- Rechazo de negocio: el estado del galpón no cambia y no queda ningún registro. **No se reintenta**.
- **Log** (`WARN`): `WARN AlertaSanitariaConsumer rechazada alertaId=5b1c7d52-3a08-4f0e-9c1d-77a1d4e0b6aa galponId=None loteId=9a1f4c70-2d3b-4a55-8e61-0b7f3c9d2e18 codigo=LOTE_NO_ACTIVO mensaje="El lote no está activo."`

**13. Galpón y lote que no se corresponden**

- **Topic**: `avicontrol.alerta-sanitaria`
- **Clave de partición**: `550e8400-e29b-41d4-a716-446655440000`
- **Cabecera/sobre**: `eventType=ALERTA_SANITARIA_EMITIDA`, `eventVersion=1`

Valor del mensaje (sobre del Módulo 2 con el `payload` de este escenario):

```json
{
  "eventId": "1a4d7e90-3c2b-4f68-8d05-9b7e6c2a1f34",
  "eventType": "ALERTA_SANITARIA_EMITIDA",
  "eventVersion": 1,
  "occurredAt": "2026-10-05T14:30:00-05:00",
  "publishedAt": "2026-10-05T14:30:02-05:00",
  "producer": "modulo-2",
  "aggregateType": "Galpon",
  "aggregateId": "550e8400-e29b-41d4-a716-446655440000",
  "payload": {
    "alertaId": "5b1c7d52-3a08-4f0e-9c1d-77a1d4e0b6aa",
    "accion": "AISLAMIENTO",
    "galponId": "6ba7b810-9dad-41d1-80b4-00c04fd430c8",
    "fechaHora": "2026-10-05T09:15:00-05:00",
    "tipoEnfermedad": "Sospecha de Newcastle",
    "descripcion": "Mortalidad atípica y signos respiratorios",
    "loteId": "2fc03a21-84c4-4c83-bb15-6607bb75cb90"
  }
}
```

**Resultado** (el Módulo 1 no responde al productor):

- Rechazo de negocio: el estado del galpón no cambia y no queda ningún registro. **No se reintenta**.
- **Log** (`WARN`): `WARN AlertaSanitariaConsumer rechazada alertaId=5b1c7d52-3a08-4f0e-9c1d-77a1d4e0b6aa galponId=6ba7b810-9dad-41d1-80b4-00c04fd430c8 loteId=2fc03a21-84c4-4c83-bb15-6607bb75cb90 codigo=LOTE_NO_PERTENECE_AL_GALPON mensaje="El lote no corresponde al galpón de la alerta."`

**14. Valor que no es un JSON válido**

- **Topic**: `avicontrol.alerta-sanitaria`
- **Clave de partición**: `550e8400-e29b-41d4-a716-446655440000`
- **Cabecera/sobre**: `eventType=ALERTA_SANITARIA_EMITIDA`, `eventVersion=1`

Valor del mensaje (texto crudo):

```
{"eventId": "1a4d7e90", "payload": 
```

**Resultado** (el Módulo 1 no responde al productor):

- Rechazo sin reintento.
- **Log** (`WARN`): `WARN AlertaSanitariaConsumer rechazada alertaId=null galponId=null loteId=null codigo=MENSAJE_ILEGIBLE mensaje="El mensaje no es un JSON válido o tiene campos con tipos incorrectos."`

**15. Versión del evento no soportada**

- **Topic**: `avicontrol.alerta-sanitaria`
- **Clave de partición**: `550e8400-e29b-41d4-a716-446655440000`
- **Cabecera/sobre**: `eventType=ALERTA_SANITARIA_EMITIDA`, `eventVersion=1`

Valor del mensaje (sobre del Módulo 2 con el `payload` de este escenario):

```json
{
  "eventId": "6f2d9c1a-0b7e-4e4a-9d51-3c8a7b1e2f90",
  "eventType": "ALERTA_SANITARIA_EMITIDA",
  "eventVersion": 2,
  "occurredAt": "2026-10-05T14:30:00-05:00",
  "publishedAt": "2026-10-05T14:30:02-05:00",
  "producer": "modulo-2",
  "aggregateType": "Galpon",
  "aggregateId": "550e8400-e29b-41d4-a716-446655440000",
  "payload": {
    "alertaId": "5b1c7d52-3a08-4f0e-9c1d-77a1d4e0b6aa",
    "accion": "AISLAMIENTO",
    "galponId": "550e8400-e29b-41d4-a716-446655440000",
    "fechaHora": "2026-10-05T09:15:00-05:00",
    "tipoEnfermedad": "Sospecha de Newcastle",
    "descripcion": "Mortalidad atípica y signos respiratorios"
  }
}
```

**Resultado** (el Módulo 1 no responde al productor):

- Rechazo sin reintento.
- **Log** (`WARN`): `WARN AlertaSanitariaConsumer rechazada alertaId=null galponId=null loteId=null codigo=VERSION_NO_SOPORTADA mensaje="La versión del evento no es soportada."`

**16. `eventType` que no corresponde al tópico**

- **Topic**: `avicontrol.alerta-sanitaria`
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
    "alertaId": "5b1c7d52-3a08-4f0e-9c1d-77a1d4e0b6aa",
    "accion": "AISLAMIENTO",
    "galponId": "550e8400-e29b-41d4-a716-446655440000",
    "fechaHora": "2026-10-05T09:15:00-05:00",
    "tipoEnfermedad": "Sospecha de Newcastle",
    "descripcion": "Mortalidad atípica y signos respiratorios"
  }
}
```

**Resultado** (el Módulo 1 no responde al productor):

- Rechazo sin reintento.
- **Log** (`WARN`): `WARN AlertaSanitariaConsumer rechazada alertaId=null galponId=null loteId=null codigo=TIPO_EVENTO_NO_SOPORTADO mensaje="El tipo de evento no corresponde a este tópico."`

**17. Error técnico (base de datos no disponible)**

- **Topic**: `avicontrol.alerta-sanitaria`
- **Clave de partición**: `550e8400-e29b-41d4-a716-446655440000`
- **Cabecera/sobre**: `eventType=ALERTA_SANITARIA_EMITIDA`, `eventVersion=1`

Valor del mensaje (sobre del Módulo 2 con el `payload` de este escenario):

```json
{
  "eventId": "6f2d9c1a-0b7e-4e4a-9d51-3c8a7b1e2f90",
  "eventType": "ALERTA_SANITARIA_EMITIDA",
  "eventVersion": 1,
  "occurredAt": "2026-10-05T14:30:00-05:00",
  "publishedAt": "2026-10-05T14:30:02-05:00",
  "producer": "modulo-2",
  "aggregateType": "Galpon",
  "aggregateId": "550e8400-e29b-41d4-a716-446655440000",
  "payload": {
    "alertaId": "5b1c7d52-3a08-4f0e-9c1d-77a1d4e0b6aa",
    "accion": "AISLAMIENTO",
    "galponId": "550e8400-e29b-41d4-a716-446655440000",
    "fechaHora": "2026-10-05T09:15:00-05:00",
    "tipoEnfermedad": "Sospecha de Newcastle",
    "descripcion": "Mortalidad atípica y signos respiratorios"
  }
}
```

**Resultado** (el Módulo 1 no responde al productor):

- No es un rechazo de negocio: se revierte, no se confirma el offset y se **reintenta** (P-05).

## Código de implementación

Código completo de este caso de uso, tal como compila en el proyecto (paquete base `com.unimag.avicontrol`). Las clases compartidas por varios casos de uso (`GalponService`, `GalponController`, los mappers y los adaptadores) muestran aquí solo los métodos de este plan; el resto está en el plan de cada caso de uso.

**`src/main/java/com/unimag/avicontrol/application/port/in/RecibirAlertaSanitariaUseCase.java`**

```java
package com.unimag.avicontrol.application.port.in;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface RecibirAlertaSanitariaUseCase {

    void recibir(Comando comando);

    record Comando(UUID alertaId, String accion, UUID galponId, UUID loteId, OffsetDateTime fechaHora,
                   String tipoEnfermedad, String descripcion, String gravedad) {
    }
}
```

**`src/main/java/com/unimag/avicontrol/domain/model/AccionSanitaria.java`**

```java
package com.unimag.avicontrol.domain.model;

import com.unimag.avicontrol.domain.exception.ValidacionNegocioException;

public enum AccionSanitaria {
    AISLAMIENTO, REANUDACION;

    public static AccionSanitaria de(String texto) {
        try {
            return AccionSanitaria.valueOf(texto);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ValidacionNegocioException("ACCION_INVALIDA", "La acción debe ser AISLAMIENTO o REANUDACION.");
        }
    }
}
```

**`src/main/java/com/unimag/avicontrol/infrastructure/in/kafka/dto/AlertaSanitariaMessage.java`**

```java
package com.unimag.avicontrol.infrastructure.in.kafka.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/** `payload` del evento de alerta sanitaria. `gravedad` es opcional y no se valida ni se guarda. */
public record AlertaSanitariaMessage(UUID alertaId, String accion, UUID galponId, UUID loteId,
                                     OffsetDateTime fechaHora, String tipoEnfermedad, String descripcion,
                                     String gravedad) {
}
```

**`src/main/java/com/unimag/avicontrol/infrastructure/in/kafka/AlertaSanitariaConsumer.java`**

```java
package com.unimag.avicontrol.infrastructure.in.kafka;

import com.unimag.avicontrol.application.port.in.RecibirAlertaSanitariaUseCase;
import com.unimag.avicontrol.domain.exception.NegocioException;
import com.unimag.avicontrol.infrastructure.in.kafka.dto.AlertaSanitariaMessage;
import com.unimag.avicontrol.infrastructure.in.kafka.dto.EventoEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;

@Component
public class AlertaSanitariaConsumer {

    static final String TOPIC = "avicontrol.alerta-sanitaria";
    static final String EVENT_TYPE = "ALERTA_SANITARIA_EMITIDA";
    private static final TypeReference<EventoEnvelope<AlertaSanitariaMessage>> TIPO = new TypeReference<>() {
    };
    private static final String RECHAZO = "rechazada alertaId={} galponId={} loteId={} codigo={} mensaje=\"{}\"";
    private static final Logger log = LoggerFactory.getLogger(AlertaSanitariaConsumer.class);

    private final LectorEventos lector;
    private final RecibirAlertaSanitariaUseCase useCase;

    public AlertaSanitariaConsumer(LectorEventos lector, RecibirAlertaSanitariaUseCase useCase) {
        this.lector = lector;
        this.useCase = useCase;
    }

    @KafkaListener(topics = TOPIC)
    public void consumir(String json) {
        AlertaSanitariaMessage m = null;
        try {
            m = lector.leer(json, TIPO, EVENT_TYPE).payload();
            useCase.recibir(new RecibirAlertaSanitariaUseCase.Comando(m.alertaId(), m.accion(), m.galponId(),
                    m.loteId(), m.fechaHora(), m.tipoEnfermedad(), m.descripcion(), m.gravedad()));
        } catch (NegocioException e) {
            registrarRechazo(m, e);
        }
    }

    /** Una reentrega de AISLAMIENTO sobre un galpón ya aislado es ACCION_INCOMPATIBLE: esperado, nivel INFO. */
    private void registrarRechazo(AlertaSanitariaMessage m, NegocioException e) {
        Object alertaId = m == null ? null : m.alertaId();
        Object galponId = m == null ? null : m.galponId();
        Object loteId = m == null ? null : m.loteId();
        if ("ACCION_INCOMPATIBLE".equals(e.codigo())) {
            log.info(RECHAZO, alertaId, galponId, loteId, e.codigo(), e.getMessage());
        } else {
            log.warn(RECHAZO, alertaId, galponId, loteId, e.codigo(), e.getMessage());
        }
    }
}
```

**`src/main/java/com/unimag/avicontrol/application/usecase/AlertaSanitariaService.java`**

```java
package com.unimag.avicontrol.application.usecase;

import com.unimag.avicontrol.application.port.in.RecibirAlertaSanitariaUseCase;
import com.unimag.avicontrol.domain.exception.ConflictoException;
import com.unimag.avicontrol.domain.exception.RecursoNoEncontradoException;
import com.unimag.avicontrol.domain.exception.ValidacionNegocioException;
import com.unimag.avicontrol.domain.model.AccionSanitaria;
import com.unimag.avicontrol.domain.model.EstadoGalpon;
import com.unimag.avicontrol.domain.model.Galpon;
import com.unimag.avicontrol.domain.model.Lote;
import com.unimag.avicontrol.domain.model.OrigenCambioEstado;
import com.unimag.avicontrol.domain.policy.ReglasFecha;
import com.unimag.avicontrol.domain.port.out.GalponRepository;
import com.unimag.avicontrol.domain.port.out.LoteRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** No guarda la alerta (FR-004) ni toca el lote; solo pide el cambio de estado del galpón. */
@Service
public class AlertaSanitariaService implements RecibirAlertaSanitariaUseCase {

    private static final String ACCION_INCOMPATIBLE = "La acción no corresponde al estado actual del galpón.";

    private final GalponRepository galpones;
    private final LoteRepository lotes;
    private final GalponEstadoService estados;
    private final Clock clock;

    public AlertaSanitariaService(GalponRepository galpones, LoteRepository lotes, GalponEstadoService estados,
                                  Clock clock) {
        this.galpones = galpones;
        this.lotes = lotes;
        this.estados = estados;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void recibir(Comando comando) {
        if (comando.alertaId() == null || comando.accion() == null || comando.fechaHora() == null) {
            throw new ValidacionNegocioException("ALERTA_INCOMPLETA", "Falta un campo obligatorio de la alerta.");
        }
        AccionSanitaria accion = AccionSanitaria.de(comando.accion());
        if (comando.galponId() == null && comando.loteId() == null) {
            throw new ValidacionNegocioException("GALPON_O_LOTE_REQUERIDO",
                    "La alerta debe identificar un galpón o un lote.");
        }
        if (!ReglasFecha.esDeHoy(comando.fechaHora(), clock)) {
            throw new ValidacionNegocioException("FECHA_INVALIDA",
                    "La fecha de la alerta no corresponde al día actual.");
        }
        if (accion == AccionSanitaria.AISLAMIENTO && (esVacio(comando.tipoEnfermedad()) || esVacio(comando.descripcion()))) {
            throw new ValidacionNegocioException("DATOS_AISLAMIENTO_INCOMPLETOS",
                    "Una alerta de aislamiento requiere tipo de enfermedad y descripción.");
        }

        Galpon galpon = resolverGalpon(comando.galponId(), comando.loteId());

        // AISLAMIENTO: PRODUCTIVO -> AISLAMIENTO. REANUDACION: AISLAMIENTO -> PRODUCTIVO.
        EstadoGalpon esperado = accion == AccionSanitaria.AISLAMIENTO ? EstadoGalpon.PRODUCTIVO : EstadoGalpon.AISLAMIENTO;
        EstadoGalpon destino = accion == AccionSanitaria.AISLAMIENTO ? EstadoGalpon.AISLAMIENTO : EstadoGalpon.PRODUCTIVO;
        if (galpon.estado() != esperado) {
            throw new ConflictoException("ACCION_INCOMPATIBLE", ACCION_INCOMPATIBLE);
        }
        try {
            estados.solicitarCambio(galpon.id(), esperado, destino, OrigenCambioEstado.ALERTA_SANITARIA_MODULO_2);
        } catch (ConflictoException e) {
            if ("ESTADO_ESPERADO_CAMBIADO".equals(e.codigo())) {
                throw new ConflictoException("ACCION_INCOMPATIBLE", ACCION_INCOMPATIBLE);
            }
            throw e;
        }
    }

    /** Resuelve el galpón directamente o por el lote activo; si llegan los dos, deben coincidir. */
    private Galpon resolverGalpon(UUID galponId, UUID loteId) {
        UUID idGalpon = galponId;
        if (loteId != null) {
            Lote lote = lotes.findById(loteId).orElseThrow(() ->
                    new RecursoNoEncontradoException("LOTE_NO_ENCONTRADO", "El lote de la alerta no existe."));
            if (galponId != null && !galponId.equals(lote.galponId())) {
                throw new ValidacionNegocioException("LOTE_NO_PERTENECE_AL_GALPON",
                        "El lote no corresponde al galpón de la alerta.");
            }
            if (!lote.activo()) {
                throw new ValidacionNegocioException("LOTE_NO_ACTIVO", "El lote no está activo.");
            }
            idGalpon = lote.galponId();
        }
        return galpones.findById(idGalpon).orElseThrow(() ->
                new RecursoNoEncontradoException("GALPON_NO_ENCONTRADO", "El galpón de la alerta no existe."));
    }

    private static boolean esVacio(String texto) {
        return texto == null || texto.isBlank();
    }
}
```

## Fuera de alcance

- No genera ni guarda la alerta sanitaria: la responsabilidad es del Módulo 2 (FR-004).
- No escribe el estado del galpón: lo remite a Actualizar estado.
- No modifica población, fecha, costo ni demás datos del lote.

## Coherencia entre módulos

El Módulo 1 es **consumidor**; el Módulo 2 genera y guarda la alerta sanitaria.

| Aspecto | Qué define este plan | A acordar con |
|---|---|---|
| Forma del mensaje | Igual que mortalidad: sobre estándar del Módulo 2 con los campos de arriba en `payload`; ignorar campos desconocidos. | Módulo 2 |
| Catálogos | `accion` = `AISLAMIENTO` / `REANUDACION`. Tipo de enfermedad y gravedad: el Módulo 1 no los valida ni los guarda, así que no necesita el catálogo. | Módulo 2 |
| Orden | Aislamiento y reanudación del mismo galpón deben procesarse en orden. El Módulo 2 debe publicar siempre con clave `galponId`, aunque la alerta nazca de un lote. | Módulo 2 |
| Reentrega | El spec prohíbe guardar la alerta, así que no hay registro de procesadas. Una reentrega de AISLAMIENTO sobre un galpón que ya está en AISLAMIENTO se rechaza por estado incompatible; el consumer debe tratarlo como esperado (log, sin reintento) y no como falla técnica. | Módulo 1 (interno) |
| Identificadores | UUID de galpón o lote. Si el Módulo 2 usa nombres, hay que acordarlo. | Módulo 2 |
| Trazabilidad | El historial de estados guarda el origen `ALERTA_SANITARIA_MODULO_2`, como pide el spec de actualizar estado. | — |

Pendientes relacionados: P-05 (rechazos y reintentos), P-06 (sobre y nombres de campo) y P-13 (identificador funcional o UUID).

**Evidencia de coherencia:** `AlertaSanitariaConsumerTest` (T094) deserializa los fixtures `alerta-sanitaria-*-payload.json`; son los mismos JSON que se entregan al Módulo 2.

## Fixtures

En `src/test/resources/contratos/` (todos son `payload`; el sobre es el de `mortalidad-evento.json`):

| Archivo | Contenido |
|---|---|
| `alerta-sanitaria-aislamiento-payload.json` | AISLAMIENTO por galpón (el ejemplo del contrato) |
| `alerta-sanitaria-reanudacion-payload.json` | REANUDACION por galpón |
| `alerta-sanitaria-por-lote-payload.json` | AISLAMIENTO identificado solo por `loteId` |

## Tareas

**Goal**: El Modulo 2 publica un evento Kafka de alerta sanitaria; el Modulo 1 aisla el galpon Productivo o lo reanuda desde Aislamiento sin guardar la alerta.

**Independent Test**: alerta de aislamiento para galpon Productivo -> queda en AISLAMIENTO con origen ALERTA_SANITARIA_MODULO_2; alerta de reanudacion -> vuelve a PRODUCTIVO; lote historico -> rechazado.

### Tests

- [ ] T056 [P] [US6] Pruebas unitarias de AlertaSanitariaService (Mockito) en test/application/: aislamiento sin tipo de enfermedad rechazado; fecha de otro dia rechazada; accion incompatible con el estado rechazada; alerta identificada por lote resuelve el galpon; datos del lote no se modifican; reentrega de AISLAMIENTO sobre un galpón ya aislado rechazada como `ACCION_INCOMPATIBLE` — clase `AlertaSanitariaServiceTest` (FR-002, FR-005, FR-006, FR-009, FR-012, FR-014, FR-015; SC-003, SC-004, SC-006)
- [ ] T094 [P] [US6] Prueba unitaria de AlertaSanitariaConsumer (Mockito) en test/infrastructure/kafka/: deserializa los fixtures `alerta-sanitaria-*-payload.json` dentro del sobre y delega en RecibirAlertaSanitariaUseCase sin lógica de negocio — clase `AlertaSanitariaConsumerTest` (FR-001, FR-002)

### Implementation

- [ ] T058 [P] [US6] Crear AlertaSanitariaMessage en main/infrastructure/in/kafka/dto/ (FR-002, FR-006, FR-007)
- [ ] T059 [P] [US6] Crear RecibirAlertaSanitariaUseCase en main/application/port/in/ (FR-001)
- [ ] T060 [US6] Implementar AlertaSanitariaService.recibir en main/application/usecase/: resolver galpon directamente o por lote activo, solicitar cambio a GalponEstadoService (FR-002 a FR-013)
- [ ] T061 [US6] Implementar AlertaSanitariaConsumer (@KafkaListener) en main/infrastructure/in/kafka/, leyendo `EventoEnvelope<AlertaSanitariaMessage>`. Topic: avicontrol.alerta-sanitaria (FR-001)

**Checkpoint**: el galpon refleja la situacion sanitaria recibida por Kafka.

## Dependencias

- Necesita un lote activo y un galpón PRODUCTIVO creados con datos de prueba.
- Orden interno: modelo y entidad -> puerto de entrada -> caso de uso -> adaptador de entrada (REST o Kafka) -> pruebas.

## Pendientes de clarificación

- P-05: FR-016 pide devolver mensajes claros al Módulo 2, pero Kafka es asíncrono. Opciones: log más dead-letter topic, o un tópico de respuesta.
- P-13: el spec dice que el Módulo 2 puede enviar identificadores funcionales o UUID. Este plan asume UUID; si llegan nombres, se resuelven por nombre único.
- P-06: sobre de eventos y valores de `eventType`.
