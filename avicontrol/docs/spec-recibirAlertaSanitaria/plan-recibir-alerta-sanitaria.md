# Implementation Plan: Recibir alerta sanitaria

**Fecha**: 2026-10-02
**Spec**: [recibirAlertaSanitaria.md](recibirAlertaSanitaria.md)
**Arquitectura y tecnologías**: [arquitectura.md](../arquitectura/arquitectura.md)
**Plan general**: [plan-modulo-1.md](../plan-general/plan-modulo-1.md) (historia US6, prioridad P1)

## Summary

El Módulo 2 publica un evento Kafka de alerta sanitaria; el Módulo 1 aísla el galpón PRODUCTIVO o lo reanuda desde AISLAMIENTO, sin guardar la alerta.

## Contexto específico

- **Tareas previas del plan general**: T014 (`KafkaConsumerConfig`), T015-T016 (transiciones).

## Clases y adaptadores propios

- `AlertaSanitariaMessage` (record Kafka)
- `RecibirAlertaSanitariaUseCase`
- `AlertaSanitariaService.recibir`
- `AlertaSanitariaConsumer`

## Contrato

> Mensaje Kafka del Módulo 2. Topic `avicontrol.alerta-sanitaria`, formato JSON. Los nombres de campo son propuestos y deben acordarse con el Módulo 2.

```json
{
  "alertaId": "5b1c7d52-3a08-4f0e-9c1d-77a1d4e0b6aa",
  "accion": "AISLAMIENTO",
  "galponId": "550e8400-e29b-41d4-a716-446655440000",
  "loteId": null,
  "fechaHora": "2026-10-02T09:15:00-05:00",
  "tipoEnfermedad": "Sospecha de Newcastle",
  "descripcion": "Mortalidad atípica y signos respiratorios",
  "gravedad": null
}
```

| Campo | Regla |
|---|---|
| `accion` | `AISLAMIENTO` o `REANUDACION`. |
| `galponId` / `loteId` | Al menos uno. Si solo viene `loteId`, se resuelve el galpón por el lote activo; un lote histórico o no activo se rechaza. |
| `fechaHora` | Debe ser del día actual (la hora puede variar). |
| `tipoEnfermedad`, `descripcion` | Obligatorios solo con `AISLAMIENTO`. |
| `gravedad` | Opcional; si no viene se conserva vacía. |

| Alerta | Estado requerido | Solicitud remitida a `GalponEstadoService` |
|---|---|---|
| `AISLAMIENTO` | PRODUCTIVO | PRODUCTIVO -> AISLAMIENTO, origen `ALERTA_SANITARIA_MODULO_2` |
| `REANUDACION` | AISLAMIENTO | AISLAMIENTO -> PRODUCTIVO, origen `ALERTA_SANITARIA_MODULO_2` |

Se rechaza si el galpón o el lote no existen, si la acción no corresponde al estado vigente, si falta la identificación, si la fecha no es de hoy o si falta un campo obligatorio del aislamiento. Un rechazo no cambia el estado del galpón ni deja registros.

## Fuera de alcance

- No genera ni guarda la alerta sanitaria: la responsabilidad es del Módulo 2 (FR-004).
- No escribe el estado del galpón: lo remite a Actualizar estado.
- No modifica población, fecha, costo ni demás datos del lote.

## Tareas

**Goal**: El Modulo 2 publica un evento Kafka de alerta sanitaria; el Modulo 1 aisla el galpon Productivo o lo reanuda desde Aislamiento sin guardar la alerta.

**Independent Test**: alerta de aislamiento para galpon Productivo -> queda en AISLAMIENTO con origen ALERTA_SANITARIA_MODULO_2; alerta de reanudacion -> vuelve a PRODUCTIVO; lote historico -> rechazado.

### Tests

- [ ] T056 [P] [US6] Pruebas unitarias de AlertaSanitariaService (Mockito) en test/application/: aislamiento sin tipo de enfermedad rechazado; fecha de otro dia rechazada; accion incompatible con el estado rechazada; alerta identificada por lote resuelve el galpon; datos del lote no se modifican — clase `AlertaSanitariaServiceTest`

### Implementation

- [ ] T058 [P] [US6] Crear AlertaSanitariaMessage en main/infrastructure/in/kafka/dto/
- [ ] T059 [P] [US6] Crear RecibirAlertaSanitariaUseCase en main/application/port/in/
- [ ] T060 [US6] Implementar AlertaSanitariaService.recibir en main/application/usecase/: resolver galpon directamente o por lote activo, solicitar cambio a GalponEstadoService
- [ ] T061 [US6] Implementar AlertaSanitariaConsumer (@KafkaListener) en main/infrastructure/in/kafka/. Topic: avicontrol.alerta-sanitaria

**Checkpoint**: el galpon refleja la situacion sanitaria recibida por Kafka.

## Dependencias

- Necesita un lote activo y un galpón PRODUCTIVO creados con datos de prueba.
- Orden interno: modelo y entidad -> puerto de entrada -> caso de uso -> adaptador de entrada (REST o Kafka) -> pruebas.

## Decisiones pendientes

- Pregunta abierta 5 (reintentos / dead-letter) y 6 (formato del mensaje) del plan general.
- Respuesta al Módulo 2 (FR-016): el spec pide devolver mensajes claros, pero Kafka es asíncrono. Opciones: registrar el rechazo en log y dead-letter topic, o un topic de respuesta. Decidir junto con la pregunta abierta 5.
- Identificador funcional: el spec dice que el Módulo 2 puede enviar identificadores funcionales o UUID. Este plan asume UUID; si llegan nombres, hay que resolverlos por nombre único.
