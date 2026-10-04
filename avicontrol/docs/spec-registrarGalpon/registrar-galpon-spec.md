# Feature Specification: Registrar galpón

**Created**: 2026-08-24

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Registro de un galpón (Priority: P1)

Como administrador de granja, quiero registrar un galpón indicando su nombre y capacidad para incorporarlo al control operativo de la granja.

**Why this priority**: Es la funcionalidad base para la operación del módulo 1; sin galpones registrados no se pueden crear lotes ni realizar seguimiento. Es una acción indispensable para el alta de activos físicos de la granja.

**Independent Test**: Se puede probar accediendo al formulario de registro, ingresando datos válidos y verificando que el galpón aparece en el listado con estado "Disponible". No depende de otros módulos ni entidades.

**Acceptance Scenarios**:

1. **Scenario**: Registro exitoso
   - **Given** el administrador se encuentra en el formulario "Registrar galpón"
   - **When** ingresa un nombre único (ej. "Galpón Norte"), una capacidad entera positiva (ej. 1500) y envía el formulario
   - **Then** el sistema crea el galpón con esos datos le asigna un UUID, estado inicial "Disponible"
   - **And** redirige al listado de galpones donde se muestra el nuevo galpón

2. **Scenario**: Registro con nombre duplicado
   - **Given** existe un galpón registrado con el mismo nombre
   - **When** el administrador intenta registrar otro galpón con ese nombre
   - **Then** el sistema muestra un mensaje de error indicando que el nombre ya está en uso 
   - **Ans**no crea el galpón

3. **Scenario**: Registro con aforo inválido
   - **Given**  el administrador está en el formulario "Registrar galpón"
   - **When** ingresa una capacidad que no es un entero positivo (ej. -5, 0, "abc", 10.5
   - **Then** el sistema muestra un mensaje de error indicando que la capacidad debe ser un número entero positivo y no crea el galpón

4. **Scenario**: registro con nombre vacío
   - **Given** el administrador está en el formulario "Registrar galpón"
   - **When** deja el campo nombre vacío y envía el formulario
   - **Then** el sistema rechaza el registro
   - **And** el sistema muestra un mensaje de error indicando que el nombre es obligatorio y no crea el galpón

## Edge Cases

- ¿Qué sucede si el administrador intenta registrar un galpón con un nombre que difiere solo en mayúsculas/minúsculas de uno existente? (ej. "galpón norte" vs "Galpón Norte") El sistema debe considerar el nombre como único sin distinguir mayúsculas/minúsculas para evitar duplicados confusos.

- ¿Cómo maneja el sistema un nombre con espacios al inicio o al final? Se deben recortar los espacios antes de validar unicidad y longitud.

- ¿Qué pasa si la capacidad es extremadamente grande (ej. 999999999999)? Al ser entero positivo sin rango definido, el sistema debería aceptarlo, pero se debe asegurar que el tipo de dato en base de datos soporte valores grandes (ej. usar entero de 64 bits).

- Si el administrador cierra el navegador antes de enviar el formulario, no se guarda nada (no hay persistencia parcial).

- ¿El sistema debe impedir el registro si hay un error de conexión a la base de datos? Debe mostrar un mensaje de error genérico y no crear el galpón.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al administrador registrar un galpón proporcionando nombre y capacidad.

- **FR-002**: El sistema DEBE validar que el nombre no esté vacío y que tenga una longitud máxima de 100 caracteres, permitiendo solo letras, números, espacios, guiones y guiones bajos.
- **FR-003**: El sistema DEBE validar que el nombre sea único en el sistema (sin distinguir mayúsculas/minúsculas).
- **FR-004**: El sistema DEBE validar que la capacidad sea un número entero positivo (mayor que cero).
- **FR-005**: El sistema DEBE asignar automáticamente un UUID único al galpón al crearlo.
- **FR-006**: El sistema DEBE establecer el estado inicial del galpón como "Disponible".
- **FR-007**: El sistema DEBE persistir el galpón en la base de datos.
- **FR-008**: Tras el registro exitoso, el sistema DEBE redirigir al administrador al listado de galpones.
- **FR-009**: El listado de galpones DEBE mostrar el galpón recién creado inmediatamente después del registro.
- **FR-010**: El sistema DEBE mostrar mensajes de error claros si el nombre está duplicado, está vacío, excede la longitud permitida o si la capacidad no es un entero positivo.

### Key Entities 

- **Galpón**: Representa un galpón físico de la granja.
	- Atributos: ID (UUID), Nombre (texto único), Capacidad (entero positivo), Estado actual (enum: Disponible, Productivo, En cosecha, Vaciado sanitario, Mantenimiento, Aislamiento).
    
    - Relaciones: Puede tener asociado un Lote (uno a uno en el tiempo), pero no es obligatorio en el momento del registro.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de los galpones nuevos nacen estrictamente con estado "Disponible".
- **SC-002**: El 100% de los intentos de registros con nombres duplicados o valores nulos son rechazados antes de llegar a la base de datos.
- **SC-003**: El 100 % de los galpones registrados correctamente debe aparecer inmediatamente en la lista de galpones.
- **SC-004**: El 100 % de los galpones creados debe recibir un UUID único.
- **SC-005**: El 100 % de los errores de validación debe mostrar un mensaje claro y específico para que el administrador pueda corregir el campo correspondiente.
- **SC-006**: El 100 % de los errores durante el guardado debe conservar los datos ingresados en el formulario para permitir un nuevo intento sin reescribir todo.
