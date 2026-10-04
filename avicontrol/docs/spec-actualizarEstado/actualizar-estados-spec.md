## Feature Specification: Actualizar Estado del Galpón

**Created**: 2026-09-04

## User Scenarios & Testing

### User Story 1 - Actualización manual de estado siguiendo transiciones permitidas (Priority: P1)

Como administrador, necesito solicitar el cambio de estado de un galpón para reflejar su fase operativa actual. Este spec es el único responsable de validar y persistir cualquier cambio de estado.

**Why this priority**: Es el mecanismo central para controlar el ciclo de vida del galpón y mantener la bioseguridad. Sin esta funcionalidad, no se puede reflejar mantenimiento, cosecha, aislamiento o limpieza, afectando la operación completa.

**Independent Test**: Puede probarse de forma aislada seleccionando un galpón en un estado conocido, verificando que solo se aceptan las transiciones válidas, procesando una solicitud y comprobando que el estado cambia correctamente y el historial registra el cambio.

### Autoridad única para cambios de estado

Todo cambio de estado del galpón DEBE ser remitido a este spec, sin importar si se origina en una acción del administrador, el registro de un lote, una alerta sanitaria, una alerta de mantenimiento, una alerta de vaciado sanitario o un proceso automático. El spec de origen puede validar y registrar su evento de origen, pero no debe actualizar directamente el estado del galpón. Para una alerta sanitaria, el módulo 2 registra la alerta y el módulo 1 la recibe y remite la solicitud; este spec registra únicamente el cambio de estado y su origen.

**Acceptance Scenarios**:

1. **Scenario**: Transición de Disponible a Mantenimiento (al atender una alerta)
   - **Given** un galpón en estado "Disponible" con una alerta de mantenimiento pendiente
   - **When** el administrador atiende la alerta de mantenimiento desde la bandeja de alertas
   - **Then** el sistema remite a este spec la solicitud de transición a "Mantenimiento", el galpón pasa a "Mantenimiento", se registra en historial y se muestra mensaje de éxito

2. **Scenario**: Solicitud sanitaria de Productivo a Aislamiento
   - **Given** un galpón en estado "Productivo" con un lote activo
   - **When** el módulo 1 remite una solicitud válida, originada por una alerta sanitaria del módulo 2, para cambiarlo a "Aislamiento"
   - **Then** este spec valida y persiste el cambio a "Aislamiento"
   - **And** el historial refleja el cambio y su origen "alerta sanitaria / módulo 2"

3. **Scenario**: Transición de Productivo a En cosecha
   - **Given** un galpón en estado "Productivo"
   - **When** el administrador actualiza el estado a "En cosecha" y confirma
   - **Then** el galpón pasa a "En cosecha"

4. **Scenario**: Solicitud de vaciado de En cosecha a Vaciado sanitario
   - **Given** un galpón en estado "En cosecha"
   - **When** el spec de vaciado sanitario remite una solicitud válida para cambiarlo a "Vaciado sanitario"
   - **Then** este spec valida y persiste el cambio a "Vaciado sanitario"

5. **Scenario**: Solicitud sanitaria de Aislamiento a Productivo (recuperación de enfermedad)
   - **Given** un galpón en estado "Aislamiento"
   - **When** el módulo 1 remite una solicitud válida, originada por una alerta sanitaria del módulo 2, para volver a "Productivo"
   - **Then** este spec valida y persiste el cambio a "Productivo"

6. **Scenario**: Retoma de cosecha desde Aislamiento
   - **Given** un galpón en estado "Aislamiento" cuyos pollos estaban en fase de cosecha antes del evento sanitario
   - **When** el administrador solicita el cambio a "En cosecha" para retomar la cosecha una vez resuelto el aislamiento
   - **Then** este spec valida y persiste el cambio a "En cosecha"

7. **Scenario**: Solicitud automática de Vaciado sanitario a Disponible
   - **Given** un galpón en estado "Vaciado sanitario"
   - **When** el proceso de vaciado sanitario remite una solicitud válida para cambiarlo a "Disponible"
   - **Then** este spec valida y persiste el cambio a "Disponible"
   - **And** el galpón queda listo para un nuevo lote

8. **Scenario**: Transición de Mantenimiento a Disponible
   - **Given** un galpón en estado "Mantenimiento"
   - **When** el administrador actualiza a "Disponible" y confirma
   - **Then** el galpón pasa a "Disponible"

9. **Scenario**: Cancelación de la actualización
   - **Given** el administrador ha seleccionado un nuevo estado en el formulario
   - **When** en el mensaje de confirmación elige "Cancelar"
   - **Then** el galpón conserva su estado original y no se registra cambio

---

### Edge Cases

- ¿Qué sucede si el administrador intenta forzar una transición no permitida manipulando la petición? El sistema debe validar en el servidor y rechazar con error.
- ¿Qué sucede si el galpón no existe (URL manipulada)? Se muestra error "galpón no encontrado".
- ¿Qué sucede si se intenta cambiar a "Vaciado sanitario"? La solicitud debe provenir del spec "Recibir vaciado sanitario" con el galpón en estado "En cosecha" (ya sea por la ruta Productivo a En cosecha o por la ruta Aislamiento a En cosecha). El spec de vaciado sanitario desvincula el lote del galpón; "Actualizar estado" no debe exigir que la población actual sea cero.
- ¿Qué sucede si llegan solicitudes simultáneas para el mismo galpón? Cada solicitud debe validar el estado vigente dentro de la transacción; si el estado esperado ya cambió, la solicitud se rechaza y no sobrescribe la actualización confirmada anteriormente.
- ¿Qué sucede si el estado actual es "Productivo" y el administrador quiere pasar a "Mantenimiento"? No se permite; solo desde Disponible.

## Requirements

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al administrador actualizar el estado de un galpón existente.
- **FR-002**: El sistema DEBE aceptar únicamente las siguientes transiciones, indicando el origen de la solicitud:
   - Desde **Disponible**: **Productivo** (spec de registro de lote, tras crear el lote exitosamente) o **Mantenimiento** (administrador, al atender una alerta de mantenimiento)
   - Desde **Productivo**: **En cosecha** (administrador) o **Aislamiento** (módulo 1, por alerta sanitaria del módulo 2)
   - Desde **En cosecha**: **Vaciado sanitario** (alerta de vaciado)
   - Desde **Vaciado sanitario**: **Disponible** (proceso automático al finalizar el periodo)
   - Desde **Mantenimiento**: **Disponible** (administrador, al finalizar mantenimiento)
   - Desde **Aislamiento**: **Productivo** (módulo 1, por alerta sanitaria de reanudación del módulo 2) o **En cosecha** (administrador, al resolver el aislamiento y retomar la cosecha)
- **FR-003**: El sistema DEBE validar que la transición solicitada esté permitida y que el origen tenga autorización para ella antes de persistir el cambio.
- **FR-004**: El sistema DEBE rechazar solicitudes manuales de **Disponible** a **Productivo**; esa transición solo puede ser remitida por el spec de registro de lote después de crear correctamente el lote.
- **FR-005**: El sistema DEBE impedir la transición directa de **Aislamiento** a **Vaciado sanitario**; el camino correcto es Aislamiento → En cosecha (administrador) y luego En cosecha → Vaciado sanitario.
- **FR-006**: Antes de guardar un cambio solicitado directamente por el administrador, el sistema DEBE mostrar un mensaje de confirmación ("¿Estás seguro de actualizar el estado?"). Las solicitudes provenientes de otros specs DEBEN incluir la autorización definida por su flujo de origen; las solicitudes del módulo 1 por alertas sanitarias se consideran autorizadas después de la validación de la alerta recibida del módulo 2 y no requieren una confirmación manual adicional.
- **FR-007**: Si la solicitud es válida y, cuando corresponda, está confirmada, este spec DEBE actualizar el estado del galpón.
- **FR-008**: El sistema DEBE registrar en el historial de cambios: identificador del galpón, estado anterior, estado nuevo, timestamp y origen de la solicitud. Para alertas sanitarias, el origen debe ser "alerta sanitaria / módulo 2". *(Usuario pendiente, se omite por autenticación no especificada)*
- **FR-009**: Tras una actualización manual exitosa, el sistema DEBE redirigir al listado de galpones y mostrar mensaje de éxito. Para solicitudes provenientes de otros specs, debe devolver el resultado del procesamiento al spec solicitante.
- **FR-010**: Si la transición es inválida o el galpón no existe, el sistema DEBE mostrar un mensaje de error y no realizar cambios.
- **FR-011**: El sistema NO DEBE usar la población actual como condición para permitir la transición a **Vaciado sanitario**. Para la transición desde **En cosecha**, DEBE validar que la solicitud provenga del spec "Recibir vaciado sanitario" después de la cosecha y que el lote haya sido retirado o desvinculado según ese flujo. Un galpón en **Aislamiento** no puede ir directamente a **Vaciado sanitario**; debe pasar primero a **En cosecha** (administrador) y luego al flujo de vaciado sanitario.
- **FR-012**: Ningún otro spec DEBE persistir directamente un cambio de estado del galpón; debe remitirlo a este spec con el estado esperado, el estado destino y el origen de la solicitud. En el caso sanitario, el módulo 1 debe remitir la solicitud con origen "alerta sanitaria / módulo 2".
- **FR-013**: Las solicitudes concurrentes DEBEN validarse contra el estado vigente dentro de la misma transacción que persiste el cambio.

### Key Entities

- **Galpón**: Entidad existente con atributo **Estado actual** que cambia según las reglas.
- **Historial de Cambios de Estado**: Registro de transiciones de estado.
   - Atributos: ID, Galpón ID (FK), Estado anterior, Estado nuevo, Timestamp, Origen de la solicitud.

## Success Criteria

### Measurable Outcomes

- **SC-001**: El administrador puede completar una actualización de estado válida en menos de 1 minuto (sin contar confirmación).
- **SC-002**: El 100% de las transiciones realizadas son válidas según la matriz definida (0% de transiciones inválidas exitosas).
- **SC-003**: El sistema previene el cambio manual de Disponible a Productivo y la transición directa de Aislamiento a Vaciado sanitario en el 100% de los intentos.
- **SC-004**: El 100% de las actualizaciones confirmadas generan registro en el historial.
- **SC-005**: El 95% de las actualizaciones exitosas redirigen correctamente al listado con mensaje de éxito.