# Implementation Plan: Recibir mortalidad del galpón

**Fecha**: 2026-10-02
**Spec**: [recibirMortalidadDelGalpón.md](recibirMortalidadDelGalpón.md)
**Arquitectura y tecnologías**: [arquitectura.md](../arquitectura/arquitectura.md)
**Plan general**: [plan-modulo-1.md](../plan-general/plan-modulo-1.md) (historia US5, prioridad P1)

## Summary

El Módulo 2 publica un evento Kafka de mortalidad; el Módulo 1 descuenta los pollos del lote activo una sola vez (idempotente por UUID de alerta).

## Contexto específico

- Las tareas T050, T051 y T055 se comparten con [plan-actualizar-poblacion-actual](../spec-actualizarPoblaciónActual/plan-actualizar-poblacion-actual.md); aquí solo se listan sus partes de mortalidad.
- **Tareas previas del plan general**: T006, T009 (`RegistroProcesamiento`), T014 (`KafkaConsumerConfig`).

## Clases y adaptadores propios

- `MortalidadMessage` (record Kafka, T050)
- `RecibirMortalidadUseCase` (T051)
- `MortalidadService.recibir` (T053)
- `MortalidadConsumer` (T054)

## Contrato

> Mensaje Kafka del Módulo 2. Topic `avicontrol.mortalidad`, formato JSON, clave de partición `galponId`. Los nombres de campo son propuestos y deben acordarse con el Módulo 2 (pregunta abierta 6 del plan general).

```json
{
  "alertaId": "9d3c0b2e-6a58-4c42-8b0c-2a1f3f0f7a11",
  "galponId": "550e8400-e29b-41d4-a716-446655440000",
  "loteId": "2fc03a21-84c4-4c83-bb15-6607bb75cb90",
  "fechaHoraEvento": "2026-10-02T14:30:00-05:00",
  "cantidadMuertos": 25
}
```

Todos los campos son obligatorios. Si el mensaje es válido, se remite a `ActualizarPoblacionUseCase` sin modificar nada directamente.

| Caso (spec) | Resultado |
|---|---|
| Falta algún campo obligatorio | Rechazado |
| Galpón o lote inexistente, o el lote no referencia al galpón | Rechazado |
| El lote no es el activo del galpón | Rechazado |
| El galpón no está PRODUCTIVO | Rechazado |
| Cantidad 0, negativa, decimal o no numérica | Rechazado |
| Fecha y hora futura o de un día distinto al de recepción (zona America/Bogota) | Rechazado |
| Mismo `alertaId` con galpón, lote, cantidad o fecha distintos | Rechazado por inconsistencia |
| Mismo `alertaId` y mismos datos | Ignorado: no se remite otra vez, se conserva el resultado original |
| Cantidad mayor que la población actual | Lo rechaza el spec de población |

Un mensaje rechazado no se remite a población ni se guarda en `RegistroProcesamiento` como procesado. La validación y la remisión son atómicas. El consumer solo deserializa y delega (`MortalidadConsumer`); no hay consulta de alertas de mortalidad para los usuarios.

## Fuera de alcance

- No descuenta pollos: eso lo hace [plan-actualizar-poblacion-actual](../spec-actualizarPoblaciónActual/plan-actualizar-poblacion-actual.md).
- No cambia el estado del galpón ni genera la orden de vaciado sanitario (FR-014, FR-021).
- No crea una entidad de negocio de alertas de mortalidad; solo el registro técnico de idempotencia (FR-019).

## Tareas

**Goal**: El Modulo 2 publica un evento Kafka de mortalidad; el Modulo 1 descuenta los pollos del lote activo una sola vez.

**Independent Test**: lote de 1000 pollos, mensaje Kafka de 25 muertos -> poblacion queda en 975; reenviar el mismo UUID no descuenta de nuevo; UUID con datos distintos es rechazado.

### Tests

- [ ] T048 [P] [US5] Prueba unitaria de MortalidadConsumer (Mockito) en test/infrastructure/kafka/: deserializa MortalidadMessage y delega en RecibirMortalidadUseCase sin logica de negocio — clase `MortalidadConsumerTest`

### Implementation

- [ ] T050 [P] [US5] Crear MortalidadMessage (record Kafka) en main/infrastructure/in/kafka/dto/ (parte mortalidad de T050)
- [ ] T051 [P] [US5] Crear RecibirMortalidadUseCase en main/application/port/in/ (parte mortalidad de T051)
- [ ] T053 [US5] Implementar MortalidadService.recibir en main/application/usecase/: galpon Productivo, fecha del dia, idempotencia con RegistroProcesamientoRepository, delegar descuento a PoblacionService en la misma transaccion
- [ ] T054 [US5] Implementar MortalidadConsumer (@KafkaListener) en main/infrastructure/in/kafka/: deserializar MortalidadMessage, invocar RecibirMortalidadUseCase. Topic: avicontrol.mortalidad
- [ ] T055 [US5] Pruebas unitarias de MortalidadService en test/application/ (parte mortalidad de T055): mensaje valido descuenta; UUID duplicado no descuenta; UUID con datos distintos rechazado; galpon no Productivo rechazado — clase `MortalidadServiceTest`

**Checkpoint**: la poblacion del lote refleja la mortalidad recibida por Kafka.

## Dependencias

- Requiere el spec de actualizar población actual (T052) y un lote activo.
- Orden interno: modelo y entidad -> puerto de entrada -> caso de uso -> adaptador de entrada (REST o Kafka) -> pruebas.

## Decisiones pendientes

- Pregunta abierta 5: política de reintentos y dead-letter topic; definirla en `KafkaConsumerConfig` antes de implementar.
- Pregunta abierta 6: confirmar con el Módulo 2 el esquema JSON del mensaje.
- Rechazo hacia el Módulo 2: con Kafka no hay respuesta síncrona. Los errores de validación no deben reintentarse; los técnicos sí. Definir en `KafkaConsumerConfig` el manejo de errores y el dead-letter topic (pregunta abierta 5).
