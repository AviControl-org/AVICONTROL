## Feature Specification: Registrar Lote

**Created**: 2026-09-04

## User Scenarios & Testing

### User Story 1 - Registro exitoso de un lote en galpón disponible (Priority: P1)

Como administrado quiero poder registrar un nuevo lote de pollos en un galpón. 

**Why this priority**: Es el siguiente paso crítico después de registrar un galpón. Sin lotes no hay producción que gestionar, y este caso de uso permite el inicio de la crianza, activando el ciclo operativo del galpón.

**Independent Test**: Puede probarse de forma aislada teniendo al menos un galpón en estado "Disponible" previamente registrado. El administrador accede al formulario, selecciona el galpón, ingresa datos válidos y verifica que el lote aparece en el listado, que se remite la solicitud de transición a "Productivo" y que la población actual es igual a la inicial. No depende de otros módulos.

**Acceptance Scenarios**:

1. **Scenario**: Registro de lote con datos válidos
   - **Given** existe un galpón con estado "Disponible" y el administrador está en el formulario "Registrar lote"
   - **When** selecciona el galpón disponible, ingresa un nombre único (ej. "Lote A1"), fecha de ingreso igual a la actual, población inicial 1000, costo total 5000000 (COP) y envía el formulario
   - **Then** el sistema crea el lote con esos datos y asigna población actual = 1000
   - **And** remite al spec "Actualizar estado" la solicitud autorizada de transición de "Disponible" a "Productivo"
   - **And** redirige al listado de lotes mostrando el lote recién creado

2. **Scenario**: Intento de registro con nombre duplicado
   - **Given** ya existe un lote con nombre "Lote A1" en el sistema
   - **When** el administrador intenta registrar otro lote con el nombre "Lote A1" (incluso con diferente capitalización)
   - **Then** el sistema muestra un error de nombre duplicado y no crea el lote ni cambia el estado del galpón

3. **Scenario**: Intento de registro con fecha futura
   - **Given** el administrador está en el formulario "Registrar lote" con un galpón disponible seleccionado
   - **When** ingresa una fecha de ingreso posterior a la actual (ej. mañana) y envía el formulario
   - **Then** el sistema muestra un error indicando que la fecha no puede ser futura y no crea el lote

4. **Scenario**: Intento de registro con población inicial no válida
   - **Given** el administrador está en el formulario "Registrar lote"
   - **When** ingresa una población inicial que no es un entero positivo (ej. 0, -10, "cien", 10.5) y envía el formulario
   - **Then** el sistema muestra un error de población inicial inválida y no crea el lote

5. **Scenario**: Intento de registro con costo total no válido
   - **Given** el administrador está en el formulario "Registrar lote"
   - **When** ingresa un costo total que no es un entero positivo (ej. -500, 0, "mucho", 100.75) y envía el formulario
   - **Then** el sistema muestra un error de costo total inválido y no crea el lote

6. **Scenario**: Intento de seleccionar galpón no disponible
   - **Given** existe un galpón en estado "Productivo" (ocupado) y el administrador accede al formulario de registro
   - **When** el sistema muestra el listado de galpones para seleccionar
   - **Then** el galpón ocupado no aparece en el listado (solo se muestran galpones "Disponibles")

---

### Edge Cases

- ¿Qué sucede si el administrador intenta registrar un lote con un nombre que difiere solo en mayúsculas/minúsculas? Se debe validar unicidad sin distinguir mayúsculas/minúsculas (case-insensitive).
- ¿Qué sucede si se reciben espacios en blanco al inicio o final del nombre? El sistema debe recortar los espacios antes de validar unicidad y longitud.
- ¿Qué sucede si el costo total es extremadamente grande (ej. 999999999999)? Dado que se usa entero, debe soportar valores grandes (64 bits). No hay límite superior especificado.
- ¿Qué sucede si el administrador intenta manipular el formulario para enviar un galpón que no está en estado "Disponible"? El sistema debe validar en el servidor y rechazar la operación.
- ¿Cómo maneja el sistema una fecha de ingreso pasada? Se permite, siempre que no sea futura. No hay límite de cuán atrás en el tiempo puede estar, asumiendo que es por problemas de conexión o registro tardío.
- ¿El campo costo total acepta decimales? Según indicación, se decidió usar enteros (pesos colombianos sin centavos), por lo que no se aceptan decimales.
- ¿Qué pasa si el administrador intenta registrar un lote sin seleccionar galpón? El formulario debe requerir selección obligatoria.

## Requirements

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al administrador registrar un lote proporcionando nombre, fecha de ingreso, población inicial, costo total y seleccionando un galpón.
- **FR-002**: El sistema DEBE mostrar únicamente galpones en estado "Disponible" en el listado de selección al momento de registrar un lote.
- **FR-003**: El sistema DEBE validar que el galpón seleccionado esté en estado "Disponible" antes de crear el lote.
- **FR-004**: El sistema DEBE validar que el nombre del lote sea único en el sistema, sin distinguir mayúsculas/minúsculas.
- **FR-005**: El sistema DEBE validar que el nombre no esté vacío, tenga una longitud máxima de 100 caracteres y solo contenga letras, números, espacios, guiones y guiones bajos.
- **FR-006**: El sistema DEBE validar que la población inicial sea un número entero positivo (mayor que cero).
- **FR-007**: El sistema DEBE validar que el costo total sea un número entero positivo (mayor que cero) en pesos colombianos.
- **FR-008**: El sistema DEBE validar que la fecha de ingreso no sea una fecha futura.
- **FR-009**: El sistema DEBE asignar automáticamente la población actual igual a la población inicial al crear el lote.
- **FR-010**: Al registrar exitosamente el lote, el sistema DEBE remitir al spec "Actualizar estado" la solicitud de transición de "Disponible" a "Productivo"; no debe actualizar directamente el estado del galpón.
- **FR-011**: El sistema DEBE asignar un UUID único al lote al crearlo.
- **FR-012**: El sistema DEBE persistir el lote y remitir la solicitud de cambio de estado de forma atómica; la actualización del estado DEBE ser ejecutada por el spec "Actualizar estado".
- **FR-013**: Tras el registro exitoso, el sistema DEBE redirigir al administrador al listado de lotes, mostrando el lote recién creado.
- **FR-014**: El sistema DEBE mostrar mensajes de error claros si el nombre está duplicado, la fecha es futura, la población inicial o el costo total no son enteros positivos, o el galpón no está disponible.

### Key Entities

- **Lote**: Representa un grupo de pollos de engorde alojados en un galpón.
  
   | Atributo | Tipo | Restricciones |
   |---|---|---|
   | UUID único | UUID | Se asigna automáticamente al crear el lote. |
   | Nombre | Texto | Único, obligatorio, máximo 100 caracteres. |
   | Población inicial | Entero positivo | Mayor que cero. |
   | Población actual | Entero positivo | Se inicializa con el valor de Población inicial. |
   | Fecha de ingreso | Fecha | No puede ser futura. |
   | Costo total | Entero positivo | En pesos colombianos, sin decimales. |
   | Llave foránea del galpón (`galpon_id`) | UUID | Obligatoria; referencia al galpón para el cual fue registrado. |

  - Relaciones: Pertenece a un Galpón (muchos a uno, pero un galpón solo puede tener un lote activo a la vez).

- **Galpón**: Entidad existente que cambia de estado al registrar un lote.

   | Atributo | Tipo | Restricciones |
   |---|---|---|
   | ID | UUID | Único; se asigna al crear el galpón. |
   | Nombre | Texto | Único y obligatorio. |
   | capacidad | Entero positivo | Mayor que cero. |
   | Estado | Enum | Disponible, Productivo, En cosecha, Vaciado sanitario, Mantenimiento o Aislamiento. |

## Success Criteria

### Measurable Outcomes

- **SC-001**: El administrador puede completar el registro de un lote en menos de 2 minutos (excluyendo tiempo de carga).
- **SC-002**: El 100% de los lotes registrados cumplen con las validaciones de nombre único, fecha no futura, población inicial y costo total entero positivo.
- **SC-003**: El 100% de los registros exitosos remite al spec "Actualizar estado" una solicitud válida de transición a "Productivo" y asigna población actual igual a la inicial.
- **SC-004**: El sistema impide la selección de galpones no disponibles en el formulario de registro (0% de intentos exitosos con galpón no disponible).
- **SC-005**: El 95% de los registros exitosos redirigen correctamente al listado de lotes sin errores.