# Feature Specification: Recibir alerta sanitaria

**Created**: 2026-09-03

Este caso de uso es compartido entre el módulo 2 y el módulo 1. El módulo 2 genera y remite la alerta sanitaria; el módulo 1 la recibe, valida la información necesaria y procesa la solicitud de cambio de estado. El módulo 1 no registra ni persiste la alerta sanitaria.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Procesar una alerta de aislamiento (Priority: P1)

Como módulo 1, quiero recibir una alerta sanitaria válida del módulo 2 para solicitar el aislamiento del galpón afectado.

**Why this priority**: Procesar oportunamente la alerta reduce el riesgo de propagación de enfermedades y mantiene el estado operativo del galpón alineado con la situación sanitaria informada.

**Independent Test**: Se puede enviar desde el módulo 2 una alerta válida para un galpón productivo y verificar que el módulo 1 remita a “Actualizar estado” la transición a “Aislamiento”, sin registrar la alerta como propia.

**Acceptance Scenarios**:

1. **Scenario**: Recepción de una alerta de aislamiento
	- **Given** existe un galpón en estado “Productivo”
	- **When** el módulo 1 recibe del módulo 2 una alerta con acción “Aislamiento”, galpón o lote activo afectado y la información sanitaria requerida
	- **Then** valida la estructura y los datos mínimos de la alerta
	- **And** resuelve el UUID del galpón afectado, directamente o mediante el lote activo
	- **And** remite a “Actualizar estado” una solicitud autorizada de transición a “Aislamiento”
	- **And** no registra ni persiste la alerta sanitaria

2. **Scenario**: Alerta identificada mediante un lote activo
	- **Given** existe un lote activo asociado a un galpón productivo
	- **When** el módulo 1 recibe del módulo 2 una alerta que identifica el lote
	- **Then** obtiene internamente el UUID del galpón asociado
	- **And** remite a “Actualizar estado” la solicitud de transición a “Aislamiento”
	- **And** no modifica los datos del lote

### User Story 2 - Procesar una alerta de reanudación (Priority: P1)

Como módulo 1, quiero recibir del módulo 2 una alerta sanitaria de reanudación para solicitar que un galpón aislado vuelva a producción.

**Why this priority**: La producción solo debe reanudarse cuando el módulo 2 informe que la condición sanitaria permite hacerlo.

**Independent Test**: Se puede enviar una alerta válida de reanudación para un galpón aislado y verificar que el módulo 1 remita a “Actualizar estado” la transición a “Productivo”, sin registrar la alerta ni modificar el lote.

**Acceptance Scenarios**:

1. **Scenario**: Recepción de una alerta de reanudación
	- **Given** existe un galpón en estado “Aislamiento”
	- **When** el módulo 1 recibe del módulo 2 una alerta con acción “Reanudación” y el galpón o lote afectado
	- **Then** valida la estructura y los datos mínimos de la alerta
	- **And** remite a “Actualizar estado” la solicitud autorizada de transición a “Productivo”
	- **And** no registra la alerta ni modifica los atributos del lote asociado

## Edge Cases

- Si el galpón o lote informado no existe, el sistema debe rechazar la alerta y devolver un mensaje específico al módulo 2.
- Si se informa un lote histórico o no activo, el sistema debe rechazar la alerta; solo se permiten lotes activos.
- Si el galpón seleccionado no está en estado “Productivo” al solicitar aislamiento, el sistema debe rechazar la alerta.
- Si el galpón seleccionado no está en estado “Aislamiento” al solicitar reanudación, el sistema debe rechazar la alerta.
- Si la alerta no identifica un galpón ni un lote activo, el sistema debe rechazarla.
- Si la fecha de la alerta no corresponde al día actual, el sistema debe rechazarla. La hora puede variar dentro del día actual.
- Si falta el tipo de enfermedad o sospecha o la descripción en una alerta de aislamiento, el sistema debe rechazarla y señalar el campo correspondiente.
- La gravedad es opcional; si no se informa, el módulo 1 debe conservar el valor vacío al procesar la solicitud.
- El módulo 2 puede informar identificadores funcionales o UUID; el módulo 1 debe resolver y validar internamente los UUID del galpón y del lote cuando corresponda.
- Si llegan dos alertas simultáneas para el mismo galpón, cada solicitud debe remitirse a “Actualizar estado”, que debe procesarla dentro de una transacción atómica y respetar el estado vigente al momento de confirmar.
- Si la alerta de aislamiento y la de reanudación llegan simultáneamente, “Actualizar estado” debe determinar el estado final según el orden de las transacciones confirmadas.
- Si falla la validación o el envío de la solicitud, el sistema no debe cambiar el estado del galpón ni registrar una alerta incompleta.
- Si ocurre un error durante el procesamiento, el sistema debe informar el error al módulo 2 para permitir un nuevo envío.
- El sistema no debe modificar la población, fecha, costo ni demás atributos del lote al cambiar el estado del galpón.
- Un nombre ambiguo no debe poder seleccionarse; los nombres de galpones y lotes deben ser únicos.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE recibir alertas sanitarias emitidas por el módulo 2.
- **FR-002**: El sistema DEBE aceptar alertas que identifiquen el galpón directamente o mediante un lote activo.
- **FR-003**: El sistema DEBE resolver y validar internamente el UUID del galpón y del lote cuando corresponda.
- **FR-004**: El sistema NO DEBE generar el UUID ni registrar o persistir la alerta sanitaria; esa responsabilidad corresponde al módulo 2.
- **FR-005**: El sistema DEBE validar la fecha y hora recibidas y aceptar únicamente fechas del día actual.
- **FR-006**: Una alerta con acción “Aislamiento” DEBE incluir tipo de enfermedad o sospecha y descripción.
- **FR-007**: La gravedad de la alerta DEBE ser opcional.
- **FR-008**: El sistema DEBE validar que el galpón exista antes de remitir una solicitud de cambio de estado.
- **FR-009**: El sistema DEBE validar que el lote informado exista, esté activo y esté asociado al galpón afectado.
- **FR-010**: El sistema DEBE remitir a “Actualizar estado” la transición de “Productivo” a “Aislamiento” únicamente después de validar una alerta de aislamiento recibida del módulo 2.
- **FR-011**: El sistema DEBE remitir a “Actualizar estado” la transición de “Aislamiento” a “Productivo” únicamente después de validar una alerta de reanudación recibida del módulo 2.
- **FR-012**: El sistema DEBE rechazar una alerta cuya acción no corresponda al estado actual del galpón.
- **FR-013**: La solicitud remitida a “Actualizar estado” DEBE procesarse dentro de la transacción que valida y persiste el cambio de estado; este spec no debe persistir directamente el estado.
- **FR-014**: El sistema NO DEBE modificar la población, fecha, costo ni demás atributos del lote al procesar la alerta.
- **FR-015**: Si falla la validación o el envío de la solicitud, el sistema NO DEBE cambiar el estado del galpón ni crear registros propios de alerta.
- **FR-016**: El sistema DEBE devolver al módulo 2 mensajes claros para alertas inválidas, galpones inexistentes, lotes no activos y errores de procesamiento.

### Key Entities *(include if feature involves data)*

- **Alerta sanitaria recibida**: Representa la novedad sanitaria emitida por el módulo 2 y procesada por el módulo 1. El módulo 1 la usa como entrada, pero no es responsable de persistirla.
	- Identificador y datos suministrados por el módulo 2
  - Fecha y hora
  - Acción: Aislamiento o Reanudación
  - Tipo de enfermedad o sospecha, obligatorio para aislamiento
  - Descripción, obligatoria para aislamiento
  - Gravedad opcional
  - UUID del galpón afectado
  - UUID del lote activo cuando la alerta se origine desde un lote
	- Origen: módulo 2
	- Estado o transición solicitada

- **Galpón**: Unidad física cuyo estado cambia como consecuencia de la alerta.
  - UUID único
  - Nombre único
  - Estado: Disponible, vaciado sanitario, productivo, en cosecha, mantenimiento o aislamiento

- **Lote**: Grupo de pollos que puede utilizarse para identificar el galpón afectado.
    - UUID único
    - Nombre
    - Población inicial
    - Población actual
    - Fecha de ingreso
    - Costo total
    - Llave foránea del galpón para el cual fue registrado

- **Módulo 2**: Emite la alerta sanitaria y es responsable de su registro de origen.
- **Módulo 1**: Recibe, valida y procesa la alerta para solicitar el cambio de estado.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100 % de las alertas de aislamiento válidas recibidas del módulo 2 debe remitir a “Actualizar estado” una solicitud de transición a “Aislamiento”.
- **SC-002**: El 100 % de las alertas de reanudación válidas recibidas del módulo 2 debe remitir a “Actualizar estado” una solicitud de transición a “Productivo”.
- **SC-003**: El 100 % de las alertas inválidas o incompatibles con el estado actual debe rechazarse con un mensaje específico al módulo 2.
- **SC-004**: Ninguna alerta fallida debe modificar el estado del galpón ni crear registros propios de alerta.
- **SC-005**: El 100 % de los cambios solicitados debe conservar la referencia al origen “módulo 2” y ejecutarse respetando la transacción de “Actualizar estado”.
- **SC-006**: El 100 % de los datos del lote debe conservarse sin cambios al aislar o reactivar su galpón.
