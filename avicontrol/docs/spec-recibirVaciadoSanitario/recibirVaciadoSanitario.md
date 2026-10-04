# Feature Specification: Recibir vaciado sanitario

**Created**: 2026-09-04

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Recibir alerta de vaciado sanitario (Priority: P1)

Como sistema AVICONTROL, quiero recibir una alerta emitida por el Módulo 2 para registrar el fin de la cosecha, desvincular el lote del galpón y remitir al spec “Actualizar estado” la solicitud de transición a “Vaciado sanitario”.

**Why this priority**: Esta transición inicia el periodo obligatorio de limpieza y desinfección del galpón antes de que pueda alojar un nuevo lote.

**Independent Test**: Se puede enviar una alerta válida con los UUID de alerta, galpón y lote, verificar que se registre una única vez, que el lote quede desvinculado del galpón y que se remita la solicitud de transición a “Vaciado sanitario”.

**Acceptance Scenarios**:

1. **Scenario**: Recepción exitosa de una alerta
	- **Given** existe un galpón en estado “En cosecha” y un lote asociado mediante la llave foránea del lote
	- **When** el Módulo 2 envía una alerta con UUID de alerta, UUID de galpón, UUID de lote y fecha y hora del evento
	- **Then** el sistema valida que el galpón y el lote existan y estén relacionados
	- **And** registra la alerta aceptada
	- **And** conserva en la alerta los UUID del lote y del galpón como referencia histórica
	- **And** elimina la relación activa del lote con el galpón
	- **And** remite al spec “Actualizar estado” la solicitud autorizada de transición a “Vaciado sanitario”

2. **Scenario**: Recepción repetida de la misma alerta
	- **Given** ya existe una alerta aceptada con el mismo UUID de alerta
	- **When** el Módulo 2 reenvía el mensaje
	- **Then** el sistema no crea otro registro
	- **And** no repite la desvinculación ni el cambio de estado
	- **And** conserva el resultado original de la alerta

3. **Scenario**: Finalización automática del vaciado sanitario
	- **Given** el galpón está en “Vaciado sanitario” y se cumple el periodo configurable expresado en días
	- **When** el proceso automático verifica que el periodo terminó
	- **Then** el proceso remite al spec “Actualizar estado” la solicitud autorizada de transición a “Disponible”
	- **And** mantiene la trazabilidad mediante la alerta recibida y sus UUID

### User Story 2 - Rechazar alertas inválidas (Priority: P1)

Como sistema AVICONTROL, quiero rechazar alertas incompletas o inconsistentes para evitar transiciones incorrectas del galpón y pérdida de trazabilidad.

**Why this priority**: Una alerta aplicada al galpón o lote equivocado puede liberar una instalación que aún no ha completado su ciclo sanitario.

**Independent Test**: Se pueden enviar alertas con identificadores faltantes, entidades inexistentes, relaciones incorrectas o estados incompatibles y verificar que no modifiquen la información del sistema.

**Acceptance Scenarios**:

1. **Scenario**: Datos obligatorios faltantes
	- **Given** la alerta no contiene el UUID de alerta, UUID de galpón, UUID de lote o fecha y hora del evento
	- **When** el sistema recibe el mensaje
	- **Then** rechaza la alerta
	- **And** no modifica el galpón ni el lote

2. **Scenario**: Entidad o relación inexistente
	- **Given** el UUID de galpón o de lote no existe, o el lote no referencia al galpón recibido
	- **When** el sistema recibe la alerta
	- **Then** rechaza la alerta
	- **And** no crea una alerta aceptada ni cambia el estado del galpón

3. **Scenario**: Estado incompatible del galpón
	- **Given** el galpón existe, pero su estado no es “En cosecha”
	- **When** el sistema recibe la alerta de vaciado sanitario
	- **Then** rechaza la alerta
	- **And** conserva el estado actual del galpón y la relación del lote

## Edge Cases

- Si el UUID de alerta ya fue procesado, el mensaje debe tratarse de forma idempotente.
- Si el UUID de alerta coincide con un registro existente pero contiene datos diferentes, el sistema debe rechazar la inconsistencia y no sobrescribir el registro original.
- Si el galpón ya está en “Vaciado sanitario” o “Disponible”, la alerta debe rechazarse por estado incompatible.
- Si llegan dos alertas diferentes para el mismo galpón simultáneamente, solo una puede aplicar la transición; la otra debe rechazarse al validar el estado vigente.
- Si falla el registro de la alerta, la desvinculación o el cambio de estado, el sistema debe revertir toda la operación.
- Si el periodo configurable no está definido, el sistema no debe ejecutar automáticamente la transición a “Disponible” y debe reportar la configuración faltante.
- La fecha y hora del evento debe corresponder al día en que se recibe la alerta; una fecha futura o de otro día debe rechazarse.
- La desvinculación activa no debe eliminar los UUID del lote y del galpón registrados en la alerta.
- Las alertas rechazadas no deben persistirse como alertas aceptadas.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE recibir alertas de vaciado sanitario emitidas por el Módulo 2.
- **FR-002**: La alerta DEBE contener obligatoriamente el UUID de alerta, UUID de galpón, UUID de lote y fecha y hora del evento.
- **FR-003**: El sistema DEBE validar que el UUID de alerta no haya sido procesado previamente.
- **FR-004**: El sistema DEBE validar que exista el galpón identificado en la alerta.
- **FR-005**: El sistema DEBE validar que exista el lote identificado y que su llave foránea corresponda al galpón recibido.
- **FR-006**: El sistema DEBE aceptar la alerta únicamente cuando el galpón esté en estado “En cosecha”.
- **FR-007**: Al aceptar la alerta, el sistema DEBE remitir al spec “Actualizar estado” la transición de “En cosecha” a “Vaciado sanitario”.
- **FR-008**: Al aceptar la alerta, el sistema DEBE eliminar la relación activa del lote con el galpón.
- **FR-009**: El sistema DEBE conservar en la alerta aceptada los UUID del lote y del galpón asociados durante la cosecha.
- **FR-010**: El sistema DEBE registrar la alerta aceptada con sus identificadores, fecha y hora del evento y fecha y hora de recepción.
- **FR-011**: El registro de la alerta, la desvinculación del lote y la solicitud al spec “Actualizar estado” DEBEN ejecutarse dentro de una única transacción atómica; este spec no debe persistir directamente el estado.
- **FR-012**: Si una operación de la transacción falla, el sistema NO DEBE conservar cambios parciales.
- **FR-013**: El sistema DEBE procesar de forma idempotente una alerta recibida más de una vez.
- **FR-014**: Después de un periodo configurable expresado en días, el proceso DEBE remitir al spec “Actualizar estado” la transición de “Vaciado sanitario” a “Disponible”.
- **FR-015**: El periodo configurable DEBE quedar definido antes de habilitar la transición automática a “Disponible”.
- **FR-016**: El sistema DEBE rechazar alertas con datos faltantes, entidades inexistentes, relación lote-galpón inválida, fecha inválida o estado incompatible.
- **FR-017**: Las alertas rechazadas NO DEBEN modificar el galpón, el lote ni crear un registro de alerta aceptada.
- **FR-018**: El sistema NO DEBE modificar los demás atributos del galpón ni los datos propios del lote como consecuencia de esta alerta.

### Key Entities *(include if feature involves data)*

- **Alerta de vaciado sanitario**: Evento enviado por el Módulo 2.
  - UUID único de la alerta
  - UUID del galpón
  - UUID del lote
  - Fecha y hora del evento
  - Fecha y hora de recepción
  - Resultado del procesamiento

- **Galpón**: Unidad física cuyo estado se actualiza.
  - UUID
  - Nombre
  - Estado
  - capacidad

- **Lote**: Grupo de pollos registrado para un galpón.
	- UUID único
	- Nombre
	- Población inicial
	- Población actual
	- Fecha de ingreso
	- Costo total
	- Llave foránea del galpón para el cual fue registrado

- **Módulo 2**: Actor externo que emite la alerta al finalizar la cosecha.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100 % de las alertas válidas debe remitir al spec “Actualizar estado” una solicitud de transición a “Vaciado sanitario”.
- **SC-002**: El 100 % de las alertas válidas debe desvincular la relación activa del lote y conservar los UUID del lote y del galpón en la alerta.
- **SC-003**: El 100 % de las alertas repetidas debe procesarse sin registros ni transiciones duplicadas.
- **SC-004**: El 100 % de las alertas inválidas debe dejar sin cambios el galpón y el lote.
- **SC-005**: El 100 % de las operaciones aceptadas debe ser atómica, sin registros de alerta aceptada asociados a transiciones incompletas.
- **SC-006**: El 100 % de los galpones con periodo de vaciado cumplido debe remitir al spec “Actualizar estado” una solicitud de transición a “Disponible”.
- **SC-007**: El 100 % de las alertas aceptadas debe conservar los UUID de alerta, galpón y lote para trazabilidad.
