## Feature Specification: Editar Galpón

**Created**: 2026-09-04

## User Scenarios & Testing

### User Story 1 - Edición del nombre del galpón (Priority: P3)

Como administrador necesito cambiar el nombre de un galpón existente.

**Why this priority**: Es una operación frecuente para mantener organizada la información de la granja; puede ser necesaria por cambios en la nomenclatura o para corregir errores. No afecta la capacidad ni el estado operativo, por lo que es de menor riesgo y alta utilidad.

**Independent Test**: Puede probarse de forma aislada seleccionando un galpón existente, cambiando su nombre a uno válido y verificando que el cambio se refleja en el listado, el historial registra la modificación y no se altera la capacidad ni el estado.

**Acceptance Scenarios**:

1. **Scenario**: Edición exitosa del nombre
   - **Given** existe un galpón con nombre "Galpón A" y estado "Disponible"
   - **When** el administrador selecciona "Editar", cambia el nombre a "Galpón Norte", confirma la acción en el mensaje de confirmación
   - **Then** el sistema guarda el nuevo nombre, registra en historial (nombre anterior y nuevo), redirige al listado de galpones y muestra mensaje de éxito. El galpón aparece con "Galpón Norte".

2. **Scenario**: Intento de editar nombre a uno duplicado
   - **Given** existen dos galpones: "Galpón A" y "Galpón B"
   - **When** el administrador edita "Galpón A" y lo cambia a "Galpón B" y confirma
   - **Then** el sistema muestra un error de nombre duplicado y no guarda los cambios.

3. **Scenario**: Intento de editar nombre con formato inválido
   - **Given** el administrador está editando un galpón
   - **When** deja el nombre vacío, ingresa solo espacios, o supera la longitud máxima permitida
   - **Then** el sistema muestra un error indicando que el nombre es inválido y no guarda.

4. **Scenario**: Cancelación de la edición antes de confirmar
   - **Given** el administrador ha modificado el nombre en el formulario
   - **When** en el mensaje de confirmación selecciona "Cancelar"
   - **Then** el sistema descarta los cambios, permanece en el formulario o regresa sin guardar, y no se modifica el galpón.

---

### User Story 2 - Edición de la capacidad (Priority: P3)

Como administrador necesito ajustar la capacidad de un galpón.

**Why this priority**: Es una operación menos frecuente y restringida a un estado específico, pero crítica cuando hay cambios estructurales en el galpón. Es de prioridad menor porque no es necesaria en la operación diaria.

**Independent Test**: Puede probarse de forma aislada seleccionando un galpón en estado "Mantenimiento", editando su capacidad a un valor entero positivo, confirmando y verificando que se actualiza correctamente. También se puede probar que en otros estados la capacidad no es editable.

**Acceptance Scenarios**:

1. **Scenario**: Edición exitosa de capacidad en Mantenimiento
   - **Given** existe un galpón en estado "Mantenimiento" con capacidad actual 1000
   - **When** el administrador edita la capacidad a 1500 y confirma
   - **Then** el sistema guarda la nueva capacidad, registra en historial, redirige al listado y muestra mensaje de éxito. El galpón aparece con capacidad 1500.

2. **Scenario**: Intento de editar capacidad en un estado distinto a Mantenimiento
   - **Given** un galpón en estado "Disponible" (o Productivo, etc.)
   - **When** el administrador intenta modificar la capacidad en el formulario
   - **Then** el sistema no permite la edición del campo capacidad (deshabilitado o rechaza el cambio), y si se fuerza mediante manipulación, muestra error y no guarda.

3. **Scenario**: Intento de ingresar capacidad no válida
   - **Given** el administrador está editando un galpón en estado "Mantenimiento"
   - **When** ingresa una capacidad que no es un entero positivo (ej. 0, -5, decimal, texto)
   - **Then** el sistema muestra un error indicando que debe ser entero positivo y no guarda.

---

### Edge Cases

- ¿Qué sucede si el administrador intenta editar un galpón que ya no existe (por manipulación de URL o borrado externo)? El sistema debe mostrar un error de "galpón no encontrado" y no permitir la edición.
- ¿Qué sucede si se intenta cambiar la capacidad a un valor menor que la población actual de un lote activo? Dado que la capacidad solo se edita en Mantenimiento (estado sin lote activo), esta situación no debería ocurrir; sin embargo, se puede validar que si existiera un lote asociado (aunque no debería) se impida.
- ¿El nombre es sensible a mayúsculas/minúsculas? Se valida unicidad sin distinguir mayúsculas/minúsculas; además, se recortan espacios al inicio y final.
- ¿Se permite guardar el mismo nombre sin cambios? Sí, no debe generar error de duplicado porque se excluye el propio galpón.
- ¿Qué sucede si el administrador abre dos pestañas de edición del mismo galpón y guarda en una y luego en otra? La última confirmación sobreescribe; no se maneja bloqueo optimista en esta especificación.

## Requirements

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al administrador acceder a un formulario de edición para un galpón existente.
- **FR-002**: El sistema DEBE cargar los datos actuales del galpón (nombre, capacidad, estado) en el formulario.
- **FR-003**: El campo "nombre" DEBE ser editable en cualquier estado del galpón.
- **FR-004**: El campo "capacidad" DEBE ser editable únicamente cuando el estado del galpón es "Mantenimiento".
- **FR-005**: El sistema DEBE validar que el nuevo nombre no esté vacío, no exceda 100 caracteres y solo contenga letras, números, espacios, guiones y guiones bajos.
- **FR-006**: El sistema DEBE validar que el nuevo nombre sea único en el sistema, excluyendo el galpón en edición (sin distinguir mayúsculas/minúsculas).
- **FR-007**: El sistema DEBE validar que la nueva capacidad sea un número entero positivo (mayor que cero).
- **FR-008**: Si el galpón no está en estado "Mantenimiento" y se intenta modificar la capacidad (incluso mediante manipulación de solicitud), el sistema DEBE rechazar el cambio y mostrar un mensaje de error.
- **FR-009**: Antes de persistir los cambios, el sistema DEBE mostrar un mensaje de confirmación ("¿Estás seguro de guardar los cambios?") con opciones de confirmar/cancelar.
- **FR-010**: Si el administrador confirma, el sistema DEBE guardar los cambios en la base de datos y registrar en el historial: identificador del galpón, campo modificado, valor anterior, valor nuevo y timestamp. 
- **FR-011**: Si el administrador cancela la confirmación, el sistema NO DEBE guardar los cambios.
- **FR-012**: Tras guardar exitosamente, el sistema DEBE redirigir al listado de galpones y mostrar un mensaje de éxito.
- **FR-013**: El sistema DEBE mostrar un mensaje de error si el galpón no existe o si el nombre está duplicado, la capacidad es inválida o se intenta cambiar capacidad en estado no permitido.

### Key Entities

- **Galpón**: Entidad existente con atributos: ID, Nombre, Capacidad, Estado actual.
- **Historial de Cambios** (nueva entidad): Registro de modificaciones sobre el galpón.
  - Atributos: ID (UUID), `galpon_id` (FK), Campo modificado (ej. "nombre", "capacidad"), Valor anterior, Valor nuevo, Timestamp.

## Success Criteria

### Measurable Outcomes

- **SC-001**: El administrador puede completar la edición del nombre en menos de 1 minuto (sin contar confirmación).
- **SC-002**: El 100% de los cambios de nombre cumplen con validación de unicidad y formato.
- **SC-003**: El sistema impide la edición de capacidad en estados distintos a "Mantenimiento" (0% de cambios exitosos en estados no permitidos).
- **SC-004**: El 100% de las ediciones confirmadas generan registro en el historial de cambios.
- **SC-005**: El 95% de las ediciones exitosas redirigen correctamente al listado de galpones con mensaje de éxito.