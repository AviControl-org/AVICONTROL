# Implementation Plan: Recibir mortalidad del galpón

**Fecha**: 2026-10-02
**Spec**: [recibirMortalidadDelGalpón.md](recibirMortalidadDelGalpón.md)
**Arquitectura y tecnologías**: [arquitectura.md](../arquitectura/arquitectura.md)
**Plan general**: [plan-modulo-1.md](../plan-general/plan-modulo-1.md) (historia US5, prioridad P1)

## Summary

El Módulo 2 publica un evento Kafka de mortalidad; el Módulo 1 descuenta los pollos del lote activo una sola vez (idempotente por UUID de alerta).

**Enfoque técnico:**

1. `MortalidadConsumer` (`@KafkaListener`, tópico `avicontrol.mortalidad`, grupo `modulo-1`) deserializa `EventoEnvelope<MortalidadMessage>` (T087), comprueba `eventType` y `eventVersion` y delega en `RecibirMortalidadUseCase`. No contiene reglas de negocio (FR-001).
2. `MortalidadService.recibir` consulta `RegistroProcesamiento` por `alertaId`: mismos datos, ignora; datos distintos, rechaza (FR-003 a FR-005).
3. Valida galpón (existe, PRODUCTIVO), lote (existe, pertenece al galpón y es el activo), cantidad entera positiva y fecha no futura del mismo día de recepción (FR-006 a FR-010, FR-016).
4. Remite el descuento a `ActualizarPoblacionUseCase` en la misma transacción (FR-011, FR-017). No modifica la población directamente ni el estado del galpón (FR-012, FR-014).
5. Un rechazo de negocio se registra en el log y no se reintenta; un error técnico se reintenta según la política del consumer (P-05).

## Contexto específico

- Las tareas T050, T051 y T055 se comparten con [plan-actualizar-poblacion-actual](../spec-actualizarPoblaciónActual/plan-actualizar-poblacion-actual.md); aquí solo se listan sus partes de mortalidad.
- **Tareas previas del plan general**: T006, T009 (`RegistroProcesamiento`), T014 (`KafkaConsumerConfig`), T086 (`FixtureJson`), T087 (`EventoEnvelope`).

## Estructura del proyecto

```text
src/main/java/com/unimag/avicontrol/
├── application/
│   ├── port/in/RecibirMortalidadUseCase.java            # T051 (parte mortalidad)
│   └── usecase/MortalidadService.java                    # T053
└── infrastructure/in/kafka/
    ├── MortalidadConsumer.java                           # T054
    └── dto/MortalidadMessage.java                        # T050 (parte mortalidad)

src/test/java/com/unimag/avicontrol/
├── application/MortalidadServiceTest.java                # T055 (parte mortalidad)
└── infrastructure/in/kafka/MortalidadConsumerTest.java   # T048
```

Además de los archivos del árbol, el código completo de este caso de uso incluye: `EventoEnvelope.java`, `LectorEventos.java`, `KafkaConsumerConfig.java`. Están en la sección *Código de implementación*.

`EventoEnvelope` (T087), `RegistroProcesamiento` (T009) y `KafkaConsumerConfig` (T014) ya existen por el plan general.

## Decisiones de diseño

**Orden de validación.** Campos obligatorios del mensaje -> idempotencia (`alertaId`) -> galpón existe -> lote existe y pertenece al galpón -> lote activo -> galpón PRODUCTIVO -> cantidad -> fecha. El primer fallo corta el proceso y deja el motivo en el log.

**Idempotencia sin doble escritor.** `MortalidadService` solo lee `RegistroProcesamiento`; quien lo escribe es `PoblacionService` (ver el plan de población). Un mensaje rechazado no deja registro (FR-020).

**Clasificación de errores en el consumer.** `ValidacionNegocioException`, `RecursoNoEncontradoException` y `ConflictoException` no se reintentan: se registran con nivel `WARN` y el offset se confirma. Los errores de acceso a datos (`DataAccessException`, choque de versión del lote) se reintentan con la política de `KafkaConsumerConfig` (P-05). El offset se confirma después de que el caso de uso termina.

**Orden por galpón.** La clave de partición es `galponId`, así que los mensajes de un mismo galpón se procesan en orden.

**Fecha.** "Del día de recepción" se evalúa en America/Bogota con el `Clock` inyectado (FR-016). Una alerta publicada a las 23:59 y entregada a las 00:01 se rechaza: es el comportamiento que pide el spec.

**Cantidad mayor que la población actual.** La rechaza el spec de población, no este; una cantidad que deja la población en cero sí se remite (FR-015).

## Contrato

> Los nombres de campo son propuestos (P-06) y deben acordarse con el Módulo 2 (ver *Coherencia entre módulos*). El JSON de ejemplo es el `payload` del [sobre de eventos](../arquitectura/arquitectura.md#sobre-de-eventos).

### Mensaje Kafka — Mortalidad del galpón

| Elemento | Definición |
|---|---|
| Topic | `avicontrol.mortalidad` |
| Productor / consumidor | Módulo 2 / Módulo 1 (grupo `modulo-1`) |
| Clave de partición | `galponId` |
| Mensaje (request) | `MortalidadMessage` (JSON) |
| Respuesta | Ninguna. Éxito: el mensaje se confirma. Rechazo: se registra en el log y sigue la política de errores del consumer (P-05). |

**Campos del mensaje**

| Campo | Tipo | Obligatorio | Regla |
|---|---|---|---|
| alertaId | UUID | sí | Clave de idempotencia; no puede reutilizarse con datos distintos |
| galponId | UUID | sí | El galpón debe existir y estar PRODUCTIVO |
| loteId | UUID | sí | Debe ser el lote activo del galpón |
| fechaHoraEvento | datetime | sí | No futura y del mismo día de recepción (America/Bogota) |
| cantidadMuertos | integer | sí | Entero mayor que 0; sin decimales |

**Resultados**

| Caso (spec) | Resultado |
|---|---|
| Falta un campo obligatorio | Rechazado |
| Galpón o lote inexistente, o el lote no referencia al galpón | Rechazado |
| El lote no es el activo del galpón | Rechazado |
| El galpón no está PRODUCTIVO | Rechazado |
| Cantidad 0, negativa, decimal o no numérica | Rechazado |
| Fecha futura o de otro día | Rechazado |
| Mismo `alertaId` con galpón, lote, cantidad o fecha distintos | Rechazado por inconsistencia |
| Mismo `alertaId` y mismos datos | Ignorado: no se remite otra vez; se conserva el resultado original |
| Cantidad mayor que la población actual | Lo rechaza el spec de población |

Un mensaje rechazado no se remite a población ni se guarda en `RegistroProcesamiento` como procesado. La validación y la remisión a `ActualizarPoblacionUseCase` son atómicas. No hay consulta de alertas de mortalidad para los usuarios.

Ejemplo de mensaje (`payload`):

```json
{
  "alertaId": "9d3c0b2e-6a58-4c42-8b0c-2a1f3f0f7a11",
  "galponId": "550e8400-e29b-41d4-a716-446655440000",
  "loteId": "2fc03a21-84c4-4c83-bb15-6607bb75cb90",
  "fechaHoraEvento": "2026-10-05T14:30:00-05:00",
  "cantidadMuertos": 25
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
| `LOTE_NO_ACTIVO` | El lote no es el lote activo del galpón. |
| `GALPON_NO_PRODUCTIVO` | El galpón no está en estado PRODUCTIVO. |
| `CANTIDAD_INVALIDA` | La cantidad de muertos debe ser un entero positivo. |
| `FECHA_INVALIDA` | La fecha del evento es futura o corresponde a otro día. |
| `ALERTA_INCONSISTENTE` | El alertaId ya fue procesado con datos distintos. |
| `MENSAJE_ILEGIBLE` | El mensaje no es un JSON válido o tiene campos con tipos incorrectos. |
| `TIPO_EVENTO_NO_SOPORTADO` | El tipo de evento no corresponde a este tópico. |
| `CANTIDAD_EXCEDE_POBLACION` | La cantidad de muertos supera la población actual del lote. |

Línea de log de un rechazo:

```text
WARN MortalidadConsumer rechazada alertaId=9d3c0b2e-6a58-4c42-8b0c-2a1f3f0f7a11 galponId=550e8400-e29b-41d4-a716-446655440000 codigo=GALPON_NO_PRODUCTIVO mensaje="El galpón no está en estado PRODUCTIVO."
```

Mensaje completo con el sobre del Módulo 2 (el JSON de ejemplo anterior es su `payload`):

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
    "alertaId": "9d3c0b2e-6a58-4c42-8b0c-2a1f3f0f7a11",
    "galponId": "550e8400-e29b-41d4-a716-446655440000",
    "loteId": "2fc03a21-84c4-4c83-bb15-6607bb75cb90",
    "fechaHoraEvento": "2026-10-05T14:30:00-05:00",
    "cantidadMuertos": 25
  }
}
```

El valor de `eventType` es un ejemplo: se acuerda con el Módulo 2 (P-06).

## Peticiones y respuestas por escenario

Es un mensaje Kafka: no hay path ni query params ni respuesta HTTP. Cada escenario muestra el mensaje completo que publica el Módulo 2 (sobre + `payload`) y su resultado. El Módulo 1 no responde al productor. Los valores del `eventType` son propuestos (P-06).

**1. Mortalidad válida (galpón PRODUCTIVO, lote activo, fecha de hoy)**

- **Topic**: `avicontrol.mortalidad`
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
    "alertaId": "9d3c0b2e-6a58-4c42-8b0c-2a1f3f0f7a11",
    "galponId": "550e8400-e29b-41d4-a716-446655440000",
    "loteId": "2fc03a21-84c4-4c83-bb15-6607bb75cb90",
    "fechaHoraEvento": "2026-10-05T14:30:00-05:00",
    "cantidadMuertos": 25
  }
}
```

**Resultado** (el Módulo 1 no responde al productor):

- Se remite a `ActualizarPoblacionUseCase`: el lote baja de 500 a 475 pollos y queda el registro de procesamiento con el `alertaId`.
- El estado del galpón no cambia. El offset se confirma.

**2. Reentrega: mismo `alertaId` con los mismos datos**

- **Topic**: `avicontrol.mortalidad`
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
    "alertaId": "9d3c0b2e-6a58-4c42-8b0c-2a1f3f0f7a11",
    "galponId": "550e8400-e29b-41d4-a716-446655440000",
    "loteId": "2fc03a21-84c4-4c83-bb15-6607bb75cb90",
    "fechaHoraEvento": "2026-10-05T14:30:00-05:00",
    "cantidadMuertos": 25
  }
}
```

**Resultado** (el Módulo 1 no responde al productor):

- Se ignora: no se descuenta de nuevo y se conserva el resultado original. El offset se confirma.
- **Log** (`INFO`): `INFO MortalidadService ignorada alertaId=9d3c0b2e-6a58-4c42-8b0c-2a1f3f0f7a11 motivo=YA_PROCESADA`

**3. Falta un campo obligatorio (sin `cantidadMuertos`)**

- **Topic**: `avicontrol.mortalidad`
- **Clave de partición**: `550e8400-e29b-41d4-a716-446655440000`
- **Cabecera/sobre**: `eventType=MORTALIDAD_REGISTRADA`, `eventVersion=1`
- **Nota**: Igual si falta `alertaId`, `galponId`, `loteId` o `fechaHoraEvento`.

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
    "alertaId": "9d3c0b2e-6a58-4c42-8b0c-2a1f3f0f7a11",
    "galponId": "550e8400-e29b-41d4-a716-446655440000",
    "loteId": "2fc03a21-84c4-4c83-bb15-6607bb75cb90",
    "fechaHoraEvento": "2026-10-05T14:30:00-05:00"
  }
}
```

**Resultado** (el Módulo 1 no responde al productor):

- Rechazo de negocio: no se descuenta nada, no se guarda registro de procesamiento y **no se reintenta**. El offset se confirma.
- **Log** (`WARN`): `WARN MortalidadConsumer rechazada alertaId=9d3c0b2e-6a58-4c42-8b0c-2a1f3f0f7a11 galponId=550e8400-e29b-41d4-a716-446655440000 codigo=ALERTA_INCOMPLETA mensaje="Falta un campo obligatorio de la alerta."`

**4. Galpón inexistente**

- **Topic**: `avicontrol.mortalidad`
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
    "alertaId": "9d3c0b2e-6a58-4c42-8b0c-2a1f3f0f7a11",
    "galponId": "d9b2d63d-a233-4123-847a-3f2c0c1e6b55",
    "loteId": "2fc03a21-84c4-4c83-bb15-6607bb75cb90",
    "fechaHoraEvento": "2026-10-05T14:30:00-05:00",
    "cantidadMuertos": 25
  }
}
```

**Resultado** (el Módulo 1 no responde al productor):

- Rechazo de negocio: no se descuenta nada, no se guarda registro de procesamiento y **no se reintenta**. El offset se confirma.
- **Log** (`WARN`): `WARN MortalidadConsumer rechazada alertaId=9d3c0b2e-6a58-4c42-8b0c-2a1f3f0f7a11 galponId=d9b2d63d-a233-4123-847a-3f2c0c1e6b55 codigo=GALPON_NO_ENCONTRADO mensaje="El galpón de la alerta no existe."`

**5. Lote inexistente**

- **Topic**: `avicontrol.mortalidad`
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
    "alertaId": "9d3c0b2e-6a58-4c42-8b0c-2a1f3f0f7a11",
    "galponId": "550e8400-e29b-41d4-a716-446655440000",
    "loteId": "d9b2d63d-a233-4123-847a-3f2c0c1e6b55",
    "fechaHoraEvento": "2026-10-05T14:30:00-05:00",
    "cantidadMuertos": 25
  }
}
```

**Resultado** (el Módulo 1 no responde al productor):

- Rechazo de negocio: no se descuenta nada, no se guarda registro de procesamiento y **no se reintenta**. El offset se confirma.
- **Log** (`WARN`): `WARN MortalidadConsumer rechazada alertaId=9d3c0b2e-6a58-4c42-8b0c-2a1f3f0f7a11 galponId=550e8400-e29b-41d4-a716-446655440000 codigo=LOTE_NO_ENCONTRADO mensaje="El lote de la alerta no existe."`

**6. El lote no corresponde al galpón**

- **Topic**: `avicontrol.mortalidad`
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
    "alertaId": "9d3c0b2e-6a58-4c42-8b0c-2a1f3f0f7a11",
    "galponId": "6ba7b810-9dad-41d1-80b4-00c04fd430c8",
    "loteId": "2fc03a21-84c4-4c83-bb15-6607bb75cb90",
    "fechaHoraEvento": "2026-10-05T14:30:00-05:00",
    "cantidadMuertos": 25
  }
}
```

**Resultado** (el Módulo 1 no responde al productor):

- Rechazo de negocio: no se descuenta nada, no se guarda registro de procesamiento y **no se reintenta**. El offset se confirma.
- **Log** (`WARN`): `WARN MortalidadConsumer rechazada alertaId=9d3c0b2e-6a58-4c42-8b0c-2a1f3f0f7a11 galponId=6ba7b810-9dad-41d1-80b4-00c04fd430c8 codigo=LOTE_NO_PERTENECE_AL_GALPON mensaje="El lote no corresponde al galpón de la alerta."`

**7. El lote existe pero no es el activo del galpón**

- **Topic**: `avicontrol.mortalidad`
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
    "alertaId": "9d3c0b2e-6a58-4c42-8b0c-2a1f3f0f7a11",
    "galponId": "550e8400-e29b-41d4-a716-446655440000",
    "loteId": "9a1f4c70-2d3b-4a55-8e61-0b7f3c9d2e18",
    "fechaHoraEvento": "2026-10-05T14:30:00-05:00",
    "cantidadMuertos": 25
  }
}
```

**Resultado** (el Módulo 1 no responde al productor):

- Rechazo de negocio: no se descuenta nada, no se guarda registro de procesamiento y **no se reintenta**. El offset se confirma.
- **Log** (`WARN`): `WARN MortalidadConsumer rechazada alertaId=9d3c0b2e-6a58-4c42-8b0c-2a1f3f0f7a11 galponId=550e8400-e29b-41d4-a716-446655440000 codigo=LOTE_NO_ACTIVO mensaje="El lote no es el lote activo del galpón."`

**8. El galpón no está PRODUCTIVO (por ejemplo EN_COSECHA o AISLAMIENTO)**

- **Topic**: `avicontrol.mortalidad`
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
    "alertaId": "9d3c0b2e-6a58-4c42-8b0c-2a1f3f0f7a11",
    "galponId": "550e8400-e29b-41d4-a716-446655440000",
    "loteId": "2fc03a21-84c4-4c83-bb15-6607bb75cb90",
    "fechaHoraEvento": "2026-10-05T14:30:00-05:00",
    "cantidadMuertos": 25
  }
}
```

**Resultado** (el Módulo 1 no responde al productor):

- Rechazo de negocio: no se descuenta nada, no se guarda registro de procesamiento y **no se reintenta**. El offset se confirma.
- **Log** (`WARN`): `WARN MortalidadConsumer rechazada alertaId=9d3c0b2e-6a58-4c42-8b0c-2a1f3f0f7a11 galponId=550e8400-e29b-41d4-a716-446655440000 codigo=GALPON_NO_PRODUCTIVO mensaje="El galpón no está en estado PRODUCTIVO."`

**9. Cantidad cero**

- **Topic**: `avicontrol.mortalidad`
- **Clave de partición**: `550e8400-e29b-41d4-a716-446655440000`
- **Cabecera/sobre**: `eventType=MORTALIDAD_REGISTRADA`, `eventVersion=1`
- **Nota**: Igual con una cantidad negativa (`-5`).

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
    "alertaId": "9d3c0b2e-6a58-4c42-8b0c-2a1f3f0f7a11",
    "galponId": "550e8400-e29b-41d4-a716-446655440000",
    "loteId": "2fc03a21-84c4-4c83-bb15-6607bb75cb90",
    "fechaHoraEvento": "2026-10-05T14:30:00-05:00",
    "cantidadMuertos": 0
  }
}
```

**Resultado** (el Módulo 1 no responde al productor):

- Rechazo de negocio: no se descuenta nada, no se guarda registro de procesamiento y **no se reintenta**. El offset se confirma.
- **Log** (`WARN`): `WARN MortalidadConsumer rechazada alertaId=9d3c0b2e-6a58-4c42-8b0c-2a1f3f0f7a11 galponId=550e8400-e29b-41d4-a716-446655440000 codigo=CANTIDAD_INVALIDA mensaje="La cantidad de muertos debe ser un entero positivo."`

**10. Fecha futura**

- **Topic**: `avicontrol.mortalidad`
- **Clave de partición**: `550e8400-e29b-41d4-a716-446655440000`
- **Cabecera/sobre**: `eventType=MORTALIDAD_REGISTRADA`, `eventVersion=1`
- **Nota**: Con la hora de recepción 14:32 del 5 de octubre de 2026. Igual con una fecha de otro día (`2026-10-04T23:50:00-05:00`).

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
    "alertaId": "9d3c0b2e-6a58-4c42-8b0c-2a1f3f0f7a11",
    "galponId": "550e8400-e29b-41d4-a716-446655440000",
    "loteId": "2fc03a21-84c4-4c83-bb15-6607bb75cb90",
    "fechaHoraEvento": "2026-10-05T23:30:00-05:00",
    "cantidadMuertos": 25
  }
}
```

**Resultado** (el Módulo 1 no responde al productor):

- Rechazo de negocio: no se descuenta nada, no se guarda registro de procesamiento y **no se reintenta**. El offset se confirma.
- **Log** (`WARN`): `WARN MortalidadConsumer rechazada alertaId=9d3c0b2e-6a58-4c42-8b0c-2a1f3f0f7a11 galponId=550e8400-e29b-41d4-a716-446655440000 codigo=FECHA_INVALIDA mensaje="La fecha del evento es futura o corresponde a otro día."`

**11. Mismo `alertaId` con datos distintos**

- **Topic**: `avicontrol.mortalidad`
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
    "alertaId": "9d3c0b2e-6a58-4c42-8b0c-2a1f3f0f7a11",
    "galponId": "550e8400-e29b-41d4-a716-446655440000",
    "loteId": "2fc03a21-84c4-4c83-bb15-6607bb75cb90",
    "fechaHoraEvento": "2026-10-05T14:30:00-05:00",
    "cantidadMuertos": 40
  }
}
```

**Resultado** (el Módulo 1 no responde al productor):

- Rechazo de negocio: no se descuenta nada, no se guarda registro de procesamiento y **no se reintenta**. El offset se confirma.
- **Log** (`WARN`): `WARN MortalidadConsumer rechazada alertaId=9d3c0b2e-6a58-4c42-8b0c-2a1f3f0f7a11 galponId=550e8400-e29b-41d4-a716-446655440000 codigo=ALERTA_INCONSISTENTE mensaje="El alertaId ya fue procesado con datos distintos."`

**12. Cantidad mayor que la población actual (la rechaza el spec de población)**

- **Topic**: `avicontrol.mortalidad`
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
    "alertaId": "9d3c0b2e-6a58-4c42-8b0c-2a1f3f0f7a11",
    "galponId": "550e8400-e29b-41d4-a716-446655440000",
    "loteId": "2fc03a21-84c4-4c83-bb15-6607bb75cb90",
    "fechaHoraEvento": "2026-10-05T14:30:00-05:00",
    "cantidadMuertos": 900
  }
}
```

**Resultado** (el Módulo 1 no responde al productor):

- Rechazo de negocio: no se descuenta nada, no se guarda registro de procesamiento y **no se reintenta**. El offset se confirma.
- **Log** (`WARN`): `WARN MortalidadConsumer rechazada alertaId=9d3c0b2e-6a58-4c42-8b0c-2a1f3f0f7a11 galponId=550e8400-e29b-41d4-a716-446655440000 codigo=CANTIDAD_EXCEDE_POBLACION mensaje="La cantidad de muertos supera la población actual del lote."`

**13. Cantidad decimal: `12.5` (no se acepta redondear)**

- **Topic**: `avicontrol.mortalidad`
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
    "alertaId": "9d3c0b2e-6a58-4c42-8b0c-2a1f3f0f7a11",
    "galponId": "550e8400-e29b-41d4-a716-446655440000",
    "loteId": "2fc03a21-84c4-4c83-bb15-6607bb75cb90",
    "fechaHoraEvento": "2026-10-05T14:30:00-05:00",
    "cantidadMuertos": 12.5
  }
}
```

**Resultado** (el Módulo 1 no responde al productor):

- Jackson rechaza el decimal en un campo entero (`ACCEPT_FLOAT_AS_INT = false`): rechazo sin reintento. Igual con una cantidad no numérica (`"abc"`).
- **Log** (`WARN`): `WARN MortalidadConsumer rechazada alertaId=null galponId=null codigo=MENSAJE_ILEGIBLE mensaje="El mensaje no es un JSON válido o tiene campos con tipos incorrectos."`

**14. Valor que no es un JSON válido**

- **Topic**: `avicontrol.mortalidad`
- **Clave de partición**: `550e8400-e29b-41d4-a716-446655440000`
- **Cabecera/sobre**: `eventType=MORTALIDAD_REGISTRADA`, `eventVersion=1`

Valor del mensaje (texto crudo):

```
{"eventId": "6f2d9c1a", "payload": {
```

**Resultado** (el Módulo 1 no responde al productor):

- Rechazo sin reintento; el offset se confirma.
- **Log** (`WARN`): `WARN MortalidadConsumer rechazada alertaId=null galponId=null codigo=MENSAJE_ILEGIBLE mensaje="El mensaje no es un JSON válido o tiene campos con tipos incorrectos."`

**15. Versión del evento no soportada**

- **Topic**: `avicontrol.mortalidad`
- **Clave de partición**: `550e8400-e29b-41d4-a716-446655440000`
- **Cabecera/sobre**: `eventType=MORTALIDAD_REGISTRADA`, `eventVersion=1`

Valor del mensaje (sobre del Módulo 2 con el `payload` de este escenario):

```json
{
  "eventId": "6f2d9c1a-0b7e-4e4a-9d51-3c8a7b1e2f90",
  "eventType": "MORTALIDAD_REGISTRADA",
  "eventVersion": 2,
  "occurredAt": "2026-10-05T14:30:00-05:00",
  "publishedAt": "2026-10-05T14:30:02-05:00",
  "producer": "modulo-2",
  "aggregateType": "Galpon",
  "aggregateId": "550e8400-e29b-41d4-a716-446655440000",
  "payload": {
    "alertaId": "9d3c0b2e-6a58-4c42-8b0c-2a1f3f0f7a11",
    "galponId": "550e8400-e29b-41d4-a716-446655440000",
    "loteId": "2fc03a21-84c4-4c83-bb15-6607bb75cb90",
    "fechaHoraEvento": "2026-10-05T14:30:00-05:00",
    "cantidadMuertos": 25
  }
}
```

**Resultado** (el Módulo 1 no responde al productor):

- Rechazo sin reintento.
- **Log** (`WARN`): `WARN MortalidadConsumer rechazada alertaId=null galponId=null codigo=VERSION_NO_SOPORTADA mensaje="La versión del evento no es soportada."`

**16. `eventType` que no corresponde al tópico**

- **Topic**: `avicontrol.mortalidad`
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
    "alertaId": "9d3c0b2e-6a58-4c42-8b0c-2a1f3f0f7a11",
    "galponId": "550e8400-e29b-41d4-a716-446655440000",
    "loteId": "2fc03a21-84c4-4c83-bb15-6607bb75cb90",
    "fechaHoraEvento": "2026-10-05T14:30:00-05:00",
    "cantidadMuertos": 25
  }
}
```

**Resultado** (el Módulo 1 no responde al productor):

- Rechazo sin reintento.
- **Log** (`WARN`): `WARN MortalidadConsumer rechazada alertaId=null galponId=null codigo=TIPO_EVENTO_NO_SOPORTADO mensaje="El tipo de evento no corresponde a este tópico."`

**17. Error técnico (base de datos no disponible o choque de versión del lote)**

- **Topic**: `avicontrol.mortalidad`
- **Clave de partición**: `550e8400-e29b-41d4-a716-446655440000`
- **Cabecera/sobre**: `eventType=MORTALIDAD_REGISTRADA`, `eventVersion=1`
- **Nota**: En el reintento se recalcula sobre la población vigente.

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
    "alertaId": "9d3c0b2e-6a58-4c42-8b0c-2a1f3f0f7a11",
    "galponId": "550e8400-e29b-41d4-a716-446655440000",
    "loteId": "2fc03a21-84c4-4c83-bb15-6607bb75cb90",
    "fechaHoraEvento": "2026-10-05T14:30:00-05:00",
    "cantidadMuertos": 25
  }
}
```

**Resultado** (el Módulo 1 no responde al productor):

- No es un rechazo de negocio: la transacción se revierte, no se confirma el offset y el mensaje se **reintenta** con la política de `KafkaConsumerConfig` (P-05).

## Código de implementación

Código completo de este caso de uso, tal como compila en el proyecto (paquete base `com.unimag.avicontrol`). Las clases compartidas por varios casos de uso (`GalponService`, `GalponController`, los mappers y los adaptadores) muestran aquí solo los métodos de este plan; el resto está en el plan de cada caso de uso.

**`src/main/java/com/unimag/avicontrol/application/port/in/RecibirMortalidadUseCase.java`**

```java
package com.unimag.avicontrol.application.port.in;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface RecibirMortalidadUseCase {

    void recibir(Comando comando);

    /** Campos sin validar tal como llegan del mensaje; el caso de uso valida y rechaza con su código. */
    record Comando(UUID alertaId, UUID galponId, UUID loteId, OffsetDateTime fechaHoraEvento, Long cantidadMuertos) {
    }
}
```

**`src/main/java/com/unimag/avicontrol/infrastructure/in/kafka/dto/EventoEnvelope.java`**

```java
package com.unimag.avicontrol.infrastructure.in.kafka.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Sobre estándar de los eventos del Módulo 2. El Módulo 1 solo usa `eventType`, `eventVersion` y `payload`;
 * el resto de campos es informativo y los campos que no estén aquí se ignoran (P-06).
 */
public record EventoEnvelope<T>(UUID eventId, String eventType, Integer eventVersion, OffsetDateTime occurredAt,
                                OffsetDateTime publishedAt, String producer, String correlationId,
                                String causationId, String aggregateType, String aggregateId, T payload) {
}
```

**`src/main/java/com/unimag/avicontrol/infrastructure/in/kafka/dto/MortalidadMessage.java`**

```java
package com.unimag.avicontrol.infrastructure.in.kafka.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * `payload` del evento de mortalidad. `cantidadMuertos` llega como Number para distinguir un entero (25) de un
 * decimal (25.0 o 25.5), que se rechaza como CANTIDAD_INVALIDA (FR-008).
 */
public record MortalidadMessage(UUID alertaId, UUID galponId, UUID loteId, OffsetDateTime fechaHoraEvento,
                                Number cantidadMuertos) {
}
```

**`src/main/java/com/unimag/avicontrol/infrastructure/in/kafka/LectorEventos.java`**

```java
package com.unimag.avicontrol.infrastructure.in.kafka;

import com.unimag.avicontrol.domain.exception.ValidacionNegocioException;
import com.unimag.avicontrol.infrastructure.in.kafka.dto.EventoEnvelope;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lee el sobre del Módulo 2 desde el JSON crudo. Un mensaje ilegible, de versión distinta de 1 o de otro
 * `eventType` se rechaza con su código; los campos desconocidos se ignoran.
 */
@Component
public class LectorEventos {

    private static final int VERSION_SOPORTADA = 1;

    private final JsonMapper mapper;

    public LectorEventos(JsonMapper mapper) {
        this.mapper = mapper;
    }

    public <T> EventoEnvelope<T> leer(String json, TypeReference<EventoEnvelope<T>> tipo, String eventTypeEsperado) {
        EventoEnvelope<T> evento;
        try {
            evento = mapper.readValue(json, tipo);
        } catch (JacksonException e) {
            throw new ValidacionNegocioException("MENSAJE_ILEGIBLE",
                    "El mensaje no es un JSON válido o tiene campos con tipos incorrectos.");
        }
        if (evento == null || evento.payload() == null) {
            throw new ValidacionNegocioException("ALERTA_INCOMPLETA", "Falta un campo obligatorio de la alerta.");
        }
        if (evento.eventVersion() == null || evento.eventVersion() != VERSION_SOPORTADA) {
            throw new ValidacionNegocioException("VERSION_NO_SOPORTADA", "La versión del evento no es soportada.");
        }
        if (!eventTypeEsperado.equals(evento.eventType())) {
            throw new ValidacionNegocioException("TIPO_EVENTO_NO_SOPORTADO",
                    "El tipo de evento no corresponde a este tópico.");
        }
        return evento;
    }
}
```

**`src/main/java/com/unimag/avicontrol/infrastructure/in/kafka/MortalidadConsumer.java`**

```java
package com.unimag.avicontrol.infrastructure.in.kafka;

import com.unimag.avicontrol.application.port.in.RecibirMortalidadUseCase;
import com.unimag.avicontrol.domain.exception.NegocioException;
import com.unimag.avicontrol.domain.exception.ValidacionNegocioException;
import com.unimag.avicontrol.infrastructure.in.kafka.dto.EventoEnvelope;
import com.unimag.avicontrol.infrastructure.in.kafka.dto.MortalidadMessage;
import java.math.BigInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;

/**
 * Adaptador de entrada Kafka: lee el sobre, arma el comando y delega. No contiene reglas de negocio.
 * Un rechazo de negocio se registra y se confirma sin reintento; un error técnico se propaga para que el
 * manejador de errores del contenedor lo reintente (P-05).
 */
@Component
public class MortalidadConsumer {

    static final String TOPIC = "avicontrol.mortalidad";
    static final String EVENT_TYPE = "MORTALIDAD_REGISTRADA";
    private static final TypeReference<EventoEnvelope<MortalidadMessage>> TIPO = new TypeReference<>() {
    };
    private static final Logger log = LoggerFactory.getLogger(MortalidadConsumer.class);

    private final LectorEventos lector;
    private final RecibirMortalidadUseCase useCase;

    public MortalidadConsumer(LectorEventos lector, RecibirMortalidadUseCase useCase) {
        this.lector = lector;
        this.useCase = useCase;
    }

    @KafkaListener(topics = TOPIC)
    public void consumir(String json) {
        MortalidadMessage mensaje = null;
        try {
            mensaje = lector.leer(json, TIPO, EVENT_TYPE).payload();
            useCase.recibir(new RecibirMortalidadUseCase.Comando(mensaje.alertaId(), mensaje.galponId(),
                    mensaje.loteId(), mensaje.fechaHoraEvento(), comoEntero(mensaje.cantidadMuertos())));
        } catch (NegocioException e) {
            log.warn("rechazada alertaId={} galponId={} codigo={} mensaje=\"{}\"",
                    mensaje == null ? null : mensaje.alertaId(), mensaje == null ? null : mensaje.galponId(),
                    e.codigo(), e.getMessage());
        }
    }

    /** Solo un entero JSON es válido: 25 sí; 25.0, 25.5 o texto, no (FR-008). */
    private static Long comoEntero(Number numero) {
        if (numero == null) {
            return null;
        }
        if (numero instanceof Integer || numero instanceof Long || numero instanceof Short || numero instanceof Byte) {
            return numero.longValue();
        }
        if (numero instanceof BigInteger grande && grande.bitLength() < Long.SIZE) {
            return grande.longValue();
        }
        throw new ValidacionNegocioException("CANTIDAD_INVALIDA", "La cantidad de muertos debe ser un entero positivo.");
    }
}
```

**`src/main/java/com/unimag/avicontrol/application/usecase/MortalidadService.java`**

```java
package com.unimag.avicontrol.application.usecase;

import com.unimag.avicontrol.application.port.in.ActualizarPoblacionCommand;
import com.unimag.avicontrol.application.port.in.ActualizarPoblacionUseCase;
import com.unimag.avicontrol.application.port.in.RecibirMortalidadUseCase;
import com.unimag.avicontrol.domain.exception.ConflictoException;
import com.unimag.avicontrol.domain.exception.RecursoNoEncontradoException;
import com.unimag.avicontrol.domain.exception.ValidacionNegocioException;
import com.unimag.avicontrol.domain.model.EstadoGalpon;
import com.unimag.avicontrol.domain.model.Galpon;
import com.unimag.avicontrol.domain.model.Lote;
import com.unimag.avicontrol.domain.policy.ReglasFecha;
import com.unimag.avicontrol.domain.port.out.GalponRepository;
import com.unimag.avicontrol.domain.port.out.LoteRepository;
import com.unimag.avicontrol.domain.port.out.RegistroProcesamientoRepository;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MortalidadService implements RecibirMortalidadUseCase {

    private static final Logger log = LoggerFactory.getLogger(MortalidadService.class);

    private final GalponRepository galpones;
    private final LoteRepository lotes;
    private final RegistroProcesamientoRepository registros;
    private final ActualizarPoblacionUseCase poblacion;
    private final Clock clock;

    public MortalidadService(GalponRepository galpones, LoteRepository lotes, RegistroProcesamientoRepository registros,
                             ActualizarPoblacionUseCase poblacion, Clock clock) {
        this.galpones = galpones;
        this.lotes = lotes;
        this.registros = registros;
        this.poblacion = poblacion;
        this.clock = clock;
    }

    /**
     * Orden de validación: campos obligatorios -> idempotencia -> galpón -> lote y su relación con el galpón ->
     * lote activo -> galpón PRODUCTIVO -> cantidad -> fecha. El primer fallo corta el proceso.
     */
    @Override
    @Transactional
    public void recibir(Comando comando) {
        if (comando.alertaId() == null || comando.galponId() == null || comando.loteId() == null
                || comando.fechaHoraEvento() == null || comando.cantidadMuertos() == null) {
            throw new ValidacionNegocioException("ALERTA_INCOMPLETA", "Falta un campo obligatorio de la alerta.");
        }
        Instant momento = comando.fechaHoraEvento().toInstant();

        var previo = registros.findById(comando.alertaId());
        if (previo.isPresent()) {
            if (previo.get().mismosDatos(comando.galponId(), comando.loteId(), comando.cantidadMuertos(), momento)) {
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
        if (!lote.activo()) {
            throw new ValidacionNegocioException("LOTE_NO_ACTIVO", "El lote no es el lote activo del galpón.");
        }
        if (galpon.estado() != EstadoGalpon.PRODUCTIVO) {
            throw new ValidacionNegocioException("GALPON_NO_PRODUCTIVO", "El galpón no está en estado PRODUCTIVO.");
        }
        if (comando.cantidadMuertos() <= 0) {
            throw new ValidacionNegocioException("CANTIDAD_INVALIDA",
                    "La cantidad de muertos debe ser un entero positivo.");
        }
        if (ReglasFecha.esFutura(comando.fechaHoraEvento(), clock)
                || !ReglasFecha.esDeHoy(comando.fechaHoraEvento(), clock)) {
            throw new ValidacionNegocioException("FECHA_INVALIDA",
                    "La fecha del evento es futura o corresponde a otro día.");
        }

        // Misma transacción: el descuento y el registro de idempotencia se confirman juntos (FR-011, FR-017).
        poblacion.actualizar(new ActualizarPoblacionCommand(comando.alertaId(), comando.loteId(),
                comando.galponId(), comando.cantidadMuertos(), momento));
    }
}
```

**`src/main/java/com/unimag/avicontrol/infrastructure/config/KafkaConsumerConfig.java`**

```java
package com.unimag.avicontrol.infrastructure.config;

import com.unimag.avicontrol.domain.exception.NegocioException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Los consumers capturan los rechazos de negocio y los registran; lo que llega aquí es un error técnico
 * (base de datos, choque de versión del lote) y se reintenta con espera fija. Agotados los reintentos el registro
 * se descarta con un log de error; el dead-letter topic queda abierto en P-05.
 */
@Configuration
public class KafkaConsumerConfig {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(AvicontrolProperties propiedades) {
        DefaultErrorHandler manejador = new DefaultErrorHandler(
                new FixedBackOff(propiedades.reintentoIntervaloMs(), propiedades.reintentosMaximos()));
        manejador.addNotRetryableExceptions(NegocioException.class);
        return manejador;
    }
}
```

## Fuera de alcance

- No descuenta pollos: eso lo hace [plan-actualizar-poblacion-actual](../spec-actualizarPoblaciónActual/plan-actualizar-poblacion-actual.md).
- No cambia el estado del galpón ni genera la orden de vaciado sanitario (FR-014, FR-021).
- No crea una entidad de negocio de alertas de mortalidad; solo el registro técnico de idempotencia (FR-019).

## Coherencia entre módulos

El Módulo 1 es **consumidor**; el Módulo 2 es el productor de la alerta.

| Aspecto | Qué define este plan | A acordar con |
|---|---|---|
| Forma del mensaje | JSON plano de 5 campos. El General del Módulo 2 define un sobre estándar (`eventId`, `eventType`, `eventVersion`, `occurredAt`, `publishedAt`, `producer`, `correlationId`, `causationId`, `aggregateType`, `aggregateId`, `payload`). Propuesta: el consumer lee el sobre, toma los 5 campos de `payload` e ignora campos desconocidos. | Módulo 2 |
| Idempotencia | Clave de negocio: `alertaId` (lo exige el spec). `eventId` del sobre solo identifica la entrega. El Módulo 2 publica con reintentos, así que los duplicados son esperados. | Módulo 2 |
| Topic y partición | `avicontrol.mortalidad`, grupo `modulo-1`, clave `galponId`. | Módulo 2 |
| Fecha | `fechaHoraEvento` con zona; la regla "del día de recepción" se evalúa en America/Bogota. | Módulo 2 |
| Tipos | `cantidadMuertos` debe viajar como entero JSON (25, no 25.0). | Módulo 2 |
| Versión | Se acepta `eventVersion` 1; otra versión se rechaza. | Módulo 2 |
| Precondición | Galpón PRODUCTIVO y lote activo. El Módulo 2 debe tomar galpón y lote vigentes del Módulo 1 al emitir; después de un vaciado ya no debe enviar mortalidad de ese lote. | Módulo 2 |
| Dueño de la población | Solo el Módulo 1 modifica `poblacionActual` (spec de población). El plan del Módulo 2 menciona un "inventario vivo": confirmar que no mantiene un segundo contador. | Módulo 2 |

El Módulo 1 no publica ningún evento de respuesta (el spec no lo pide).

Pendientes relacionados: P-05 (rechazos y reintentos), P-06 (sobre y nombres de campo), P-10 (topología) y P-11 (dueño de la población).

**Evidencia de coherencia:** `MortalidadConsumerTest` (T048) deserializa los fixtures `mortalidad-payload.json` y `mortalidad-evento.json`; son los mismos JSON que se entregan al Módulo 2.

## Fixtures

En `src/test/resources/contratos/`:

| Archivo | Contenido |
|---|---|
| `mortalidad-payload.json` | El mensaje de ejemplo del contrato |
| `mortalidad-evento.json` | El mismo mensaje dentro del sobre del Módulo 2 |
| `mortalidad-payload-cantidad-decimal.json` | `cantidadMuertos: 25.5`, que debe rechazarse |

`MortalidadConsumerTest` deserializa `mortalidad-evento.json` y verifica que el consumer ignora los campos del sobre que no usa.

## Tareas

**Goal**: El Modulo 2 publica un evento Kafka de mortalidad; el Modulo 1 descuenta los pollos del lote activo una sola vez.

**Independent Test**: lote de 1000 pollos, mensaje Kafka de 25 muertos -> poblacion queda en 975; reenviar el mismo UUID no descuenta de nuevo; UUID con datos distintos es rechazado.

### Tests

- [ ] T048 [P] [US5] Prueba unitaria de MortalidadConsumer (Mockito) en test/infrastructure/kafka/: deserializa el fixture `mortalidad-evento.json` (sobre con `payload`) y delega en RecibirMortalidadUseCase sin logica de negocio — clase `MortalidadConsumerTest` (FR-001, FR-002, FR-011)

### Implementation

- [ ] T050 [P] [US5] Crear MortalidadMessage (record Kafka) en main/infrastructure/in/kafka/dto/ (parte mortalidad de T050) (FR-002)
- [ ] T051 [P] [US5] Crear RecibirMortalidadUseCase en main/application/port/in/ (parte mortalidad de T051) (FR-011)
- [ ] T053 [US5] Implementar MortalidadService.recibir en main/application/usecase/: galpon Productivo, fecha del dia, idempotencia con RegistroProcesamientoRepository, delegar descuento a PoblacionService en la misma transaccion (FR-003 a FR-011, FR-016, FR-017)
- [ ] T054 [US5] Implementar MortalidadConsumer (@KafkaListener) en main/infrastructure/in/kafka/: deserializar `EventoEnvelope<MortalidadMessage>`, invocar RecibirMortalidadUseCase. Topic: avicontrol.mortalidad (FR-001)
- [ ] T055 [US5] Pruebas unitarias de MortalidadService en test/application/ (parte mortalidad de T055): mensaje valido descuenta; UUID duplicado no descuenta; UUID con datos distintos rechazado; galpon no Productivo rechazado — clase `MortalidadServiceTest` (FR-004, FR-005, FR-008, FR-020; SC-001 a SC-003)

**Checkpoint**: la poblacion del lote refleja la mortalidad recibida por Kafka.

## Dependencias

- Requiere el spec de actualizar población actual (T052) y un lote activo.
- Orden interno: modelo y entidad -> puerto de entrada -> caso de uso -> adaptador de entrada (REST o Kafka) -> pruebas.

## Pendientes de clarificación

- P-05: Kafka no responde al productor. Los errores de validación no deben reintentarse y los técnicos sí; definir en `KafkaConsumerConfig` el manejo de errores y el dead-letter topic, y si el Módulo 2 necesita recibir el rechazo.
- P-06: sobre de eventos y valores de `eventType`.
- P-11: confirmar que el Módulo 2 no mantiene un segundo contador de población.
