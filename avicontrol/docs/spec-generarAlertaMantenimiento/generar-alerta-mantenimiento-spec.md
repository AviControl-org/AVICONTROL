## Feature Specification: Generar Alerta de Mantenimiento

**Created**: 2026-09-04

## User Scenarios & Testing

### User Story 1 - Generación de alerta de mantenimiento por parte del técnico (Priority: P1)

Como técnico de infraestructura identifico una falla en un galpón y necesito notificar al administrador para que se programe una reparación. 

**Why this priority**: Es el mecanismo para informar al administrador sobre problemas de infraestructura que requieren atención. Sin esta funcionalidad, las fallas no se comunican formalmente y se pierde trazabilidad. Es una acción esencial para la operación segura de la granja.

**Independent Test**: Puede probarse de forma aislada teniendo al menos un galpón en estado "Disponible". El técnico genera una alerta con descripción y verifica que aparece en la lista de alertas pendientes. La atención posterior debe remitir al spec "Actualizar estado" la transición a "Mantenimiento".

**Acceptance Scenarios**:

1. **Scenario**: Generación exitosa de alerta
   - **Given** existe un galpón en estado "Disponible" y el técnico está en el formulario "Generar alerta de mantenimiento"
   - **When** selecciona el galpón, ingresa una descripción (ej. "Reparar sistema de ventilación"), opcionalmente selecciona severidad "Alta" y tipo "Ventilación", confirma la alerta
   - **Then** el sistema crea la alerta con fecha/hora automática, la asocia al galpón y al técnico, la marca como "Pendiente", redirige a la lista de galpones disponibles y muestra mensaje de éxito. La alerta es visible en la bandeja de alertas.

2. **Scenario**: Intento de generar alerta para galpón no disponible
   - **Given** existe un galpón en estado "Productivo" (no disponible) y un galpón "Disponible"
   - **When** el técnico accede al formulario de generación de alerta
   - **Then** el sistema solo muestra el galpón "Disponible" en el listado de selección; el galpón "Productivo" no aparece.

3. **Scenario**: Intento de generar alerta duplicada para el mismo galpón
   - **Given** ya existe una alerta activa (pendiente) para un galpón "Disponible"
   - **When** el técnico intenta generar otra alerta para ese mismo galpón
   - **Then** el sistema muestra un error indicando que ya existe una alerta activa para ese galpón y no crea una nueva.

4. **Scenario**: Intento de generar alerta con descripción vacía
   - **Given** el técnico ha seleccionado un galpón disponible
   - **When** deja el campo de descripción vacío y envía el formulario
   - **Then** el sistema muestra un mensaje de error indicando que la descripción es obligatoria y no crea la alerta.

5. **Scenario**: Cancelación de la generación antes de confirmar
   - **Given** el técnico ha completado los datos en el formulario
   - **When** en el mensaje de confirmación selecciona "Cancelar"
   - **Then** el sistema descarta los datos, no crea la alerta y permanece en el formulario o regresa sin cambios.

---

### Edge Cases

- ¿Qué sucede si el galpón seleccionado ya no existe al momento de guardar (por manipulación de URL)? El sistema debe validar en el servidor y mostrar error "galpón no encontrado".
- ¿Qué sucede si dos técnicos intentan generar una alerta para el mismo galpón simultáneamente? El sistema debe tener una restricción de unicidad en la base de datos para alertas activas por galpón, rechazando la segunda solicitud.
- ¿Qué sucede si la descripción contiene solo espacios en blanco? Se considera vacía y se muestra error.
- ¿El técnico debe poder ver sus alertas anteriores? No es parte de este caso de uso, pero el historial queda disponible para consulta del administrador.

## Requirements

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al técnico de infraestructura generar una alerta de mantenimiento para un galpón.
- **FR-002**: El sistema DEBE mostrar únicamente galpones en estado "Disponible" en el formulario de selección.
- **FR-003**: El sistema DEBE validar que el galpón seleccionado exista y esté en estado "Disponible".
- **FR-004**: El sistema DEBE requerir una descripción del problema, no vacía, con una longitud máxima de 500 caracteres.
- **FR-005**: El sistema DEBE capturar automáticamente la fecha y hora de generación de la alerta.
- **FR-006**: El sistema DEBE impedir la creación de más de una alerta activa (estado "Pendiente") por galpón.
- **FR-007**: El sistema DEBE mostrar un mensaje de confirmación antes de persistir la alerta.
- **FR-008**: Si el técnico confirma, el sistema DEBE crear la alerta con estado "Pendiente", sin cambiar el estado del galpón; la atención de la alerta DEBE remitir al spec "Actualizar estado" la transición de "Disponible" a "Mantenimiento".
- **FR-009**: Si el técnico cancela, el sistema NO DEBE crear la alerta.
- **FR-010**: El sistema DEBE almacenar las alertas en un historial accesible para el administrador.
- **FR-011**: El sistema DEBE mostrar errores claros si la descripción está vacía, el galpón no es válido o ya existe una alerta activa.
### Key Entities

- **Alerta de Mantenimiento**: Representa un aviso de falla de infraestructura generado por el técnico.
  - Atributos: ID (UUID), Galpón ID (FK), Descripción (texto), Fecha/hora (timestamp), Técnico (identificador), Estado (Pendiente, Atendida, Eliminada), Severidad (opcional), Tipo (opcional).
  - Relaciones: Pertenece a un Galpón (muchos a uno). Un galpón solo puede tener una alerta activa a la vez.

## Success Criteria

### Measurable Outcomes

- **SC-001**: El técnico puede generar una alerta en menos de 2 minutos (excluyendo tiempo de confirmación).
- **SC-002**: El 100% de las alertas creadas tienen descripción no vacía y galpón válido en estado "Disponible".
- **SC-003**: El sistema previene la creación de alertas duplicadas activas para el mismo galpón (0% de duplicados).
- **SC-004**: El 95% de las generaciones exitosas redirigen correctamente a la lista de galpones disponibles con mensaje de éxito.
- **SC-005**: Todas las alertas generadas quedan registradas en el historial con su estado inicial "Pendiente".