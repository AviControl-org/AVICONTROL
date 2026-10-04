# Implementation Plan: Recibir vaciado sanitario

**Fecha**: 2026-10-02
**Spec**: [recibirVaciadoSanitario.md](recibirVaciadoSanitario.md)
**Arquitectura y tecnologías**: [arquitectura.md](../arquitectura/arquitectura.md)
**Plan general**: [plan-modulo-1.md](../plan-general/plan-modulo-1.md) (historia US7, prioridad P1)

## Summary

El Módulo 2 publica un evento Kafka de vaciado; el galpón EN_COSECHA pasa a VACIADO_SANITARIO, el lote se desvincula y, cumplido el período configurado, un job devuelve el galpón a DISPONIBLE.

## Contexto específico

- **Tareas previas del plan general**: T006, T009, T014, T016.

## Clases y adaptadores propios

- `VaciadoSanitarioMessage` (record Kafka)
- `RecibirVaciadoSanitarioUseCase`
- `VaciadoSanitarioService.recibir` y `finalizarPeriodosCumplidos`
- `VaciadoSanitarioConsumer`
- `VaciadoSanitarioJob`

## Contrato

> Mensaje Kafka del Módulo 2. Topic `avicontrol.vaciado-sanitario`, formato JSON, clave de partición `galponId`. Los nombres de campo son propuestos y deben acordarse con el Módulo 2.

```json
{
  "alertaId": "0c8e2a11-5d4f-4b7a-a3e9-1f6b2c9d8e47",
  "galponId": "550e8400-e29b-41d4-a716-446655440000",
  "loteId": "2fc03a21-84c4-4c83-bb15-6607bb75cb90",
  "fechaHoraEvento": "2026-10-02T16:00:00-05:00"
}
```

Todos los campos son obligatorios. Si es válido, en **una sola transacción**: se registra la alerta aceptada (`alertaId`, `galponId`, `loteId`, fecha del evento, fecha de recepción, resultado), se marca la desvinculación del lote (`desvinculado_en`, conservando los UUID en la alerta) y se solicita EN_COSECHA -> VACIADO_SANITARIO con origen `ALERTA_VACIADO_SANITARIO`.

| Caso (spec) | Resultado |
|---|---|
| Falta un campo obligatorio | Rechazado |
| Galpón o lote inexistente, o el lote no referencia al galpón | Rechazado |
| El galpón no está EN_COSECHA (incluye VACIADO_SANITARIO y DISPONIBLE) | Rechazado |
| Fecha futura o de otro día | Rechazado |
| Mismo `alertaId` con datos distintos | Rechazado, el registro original no se sobrescribe |
| Mismo `alertaId` y mismos datos | Ignorado: sin otro registro, desvinculación ni cambio de estado |
| Dos alertas distintas simultáneas para el mismo galpón | Solo una aplica; la otra falla al validar el estado vigente |

**Finalización automática (`VaciadoSanitarioJob`):** el job revisa los galpones en VACIADO_SANITARIO cuyo período configurado (`avicontrol.vaciado-sanitario.dias`) ya se cumplió y solicita VACIADO_SANITARIO -> DISPONIBLE con origen `PROCESO_AUTOMATICO`. Si el período no está configurado, no ejecuta la transición y reporta la configuración faltante en el log.

## Fuera de alcance

- No escribe el estado del galpón: lo solicita a Actualizar estado.
- No modifica el resto de atributos del galpón ni los datos propios del lote.
- Las alertas rechazadas no se guardan como alertas aceptadas (FR-017).

## Tareas

**Goal**: El Modulo 2 publica un evento Kafka de vaciado; el galpon (En cosecha) pasa a Vaciado sanitario, el lote se desvincula y, tras el periodo configurado, vuelve a Disponible automaticamente.

**Independent Test**: mensaje Kafka para galpon En cosecha -> VACIADO_SANITARIO, lote desvinculado, alerta guardada; mensaje repetido es idempotente; periodo cumplido -> job lo deja DISPONIBLE.

### Tests

- [ ] T062 [P] [US7] Pruebas unitarias de VaciadoSanitarioService (Mockito) en test/application/: galpon En cosecha aceptado; otros estados rechazados; UUID repetido idempotente; UUID con datos distintos rechazado; registro + desvinculacion + cambio de estado en una sola operacion; periodo sin configurar -> el job no ejecuta la transicion — clase `VaciadoSanitarioServiceTest`

### Implementation

- [ ] T064 [P] [US7] Crear VaciadoSanitarioMessage en main/infrastructure/in/kafka/dto/
- [ ] T065 [P] [US7] Crear RecibirVaciadoSanitarioUseCase en main/application/port/in/
- [ ] T066 [US7] Implementar VaciadoSanitarioService.recibir en main/application/usecase/: aceptar solo EN_COSECHA, guardar alerta con UUID, marcar desvinculado_en en el lote, solicitar EN_COSECHA -> VACIADO_SANITARIO
- [ ] T067 [US7] Implementar VaciadoSanitarioService.finalizarPeriodosCumplidos y VaciadoSanitarioJob (@Scheduled) en main/infrastructure/in/job/; habilitar @EnableScheduling
- [ ] T068 [US7] Implementar VaciadoSanitarioConsumer (@KafkaListener) en main/infrastructure/in/kafka/. Topic: avicontrol.vaciado-sanitario

**Checkpoint**: el ciclo completo del galpon se cierra y vuelve a Disponible.

## Dependencias

- Necesita un galpón EN_COSECHA creado con datos de prueba.
- Orden interno: modelo y entidad -> puerto de entrada -> caso de uso -> adaptador de entrada (REST o Kafka) -> pruebas.

## Decisiones pendientes

- Pregunta abierta 2: cuántos días dura el período de vaciado.
- Preguntas abiertas 5 y 6 (Kafka).
- Desde cuándo se cuenta el período: el spec no lo dice. Propuesta: desde la recepción de la alerta aceptada (`fechaHoraRecepcion`). Confirmar.
- Cuántos días dura el período (pregunta abierta 2 del plan general). Hasta definirlo, el job no hace nada.
- Rechazo hacia el Módulo 2 y reintentos: ver pregunta abierta 5.
