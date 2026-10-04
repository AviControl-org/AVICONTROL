 # Feature Specification: Consultar galpón-lote

 **Created**: 2026-09-03

 ## User Scenarios & Testing *(mandatory)*

 ### User Story 1 - Consultar el listado de galpones (Priority: P1)

 Como administrador u operario de granja, quiero consultar un listado de galpones para conocer rápidamente su nombre, capacidad y estado actual.

 **Why this priority**: El listado permite ubicar y supervisar los galpones disponibles antes de consultar la información detallada de sus lotes.

 **Independent Test**: Se puede probar accediendo a la consulta y verificando que los galpones registrados aparezcan paginados, ordenados por nombre y con sus atributos principales.

 **Acceptance Scenarios**:

 1. **Scenario**: Listado inicial de galpones
	 - **Given** existen galpones registrados
	 - **When** el usuario ingresa a la consulta sin indicar un nombre
	 - **Then** el sistema muestra todos los galpones aplicando una paginación de 10 registros
	 - **And** los ordena por nombre ascendente
	 - **And** muestra el nombre, capacidad y estado de cada galpón

 2. **Scenario**: Búsqueda parcial por nombre
	 - **Given** existen galpones registrados
	 - **When** el usuario ingresa una parte del nombre
	 - **Then** el sistema muestra los galpones cuyo nombre contiene el texto buscado

 3. **Scenario**: Filtrado y ordenamiento del listado
	 - **Given** el listado de galpones está visible
	 - **When** el usuario aplica un estado o cambia el criterio de ordenamiento
	 - **Then** el sistema actualiza el listado de acuerdo con el filtro o criterio seleccionado
	 - **And** conserva la paginación de 10 registros

 ### User Story 2 - Consultar el detalle del galpón y su lote (Priority: P1)

 Como administrador u operario de granja, quiero consultar el detalle de un galpón y la información de su lote para conocer su situación operativa.

 **Why this priority**: La relación entre el galpón y el lote permite consultar la población y los datos económicos necesarios para el control de la producción.

 **Independent Test**: Se puede probar seleccionando un galpón del listado y verificando que el sistema muestre sus datos y los del lote más reciente cuya llave foránea referencia a ese galpón.

 **Acceptance Scenarios**:

 1. **Scenario**: Galpón referenciado por un lote
	 - **Given** existe uno o más lotes cuya llave foránea referencia al galpón
	 - **When** el usuario selecciona el galpón
	 - **Then** el sistema muestra el nombre, capacidad y estado del galpón
	 - **And** muestra el nombre del lote, población inicial, población actual, fecha de ingreso, edad en días y costo total
	- **And** considera como lote activo el lote referenciado más recientemente por fecha de ingreso

 2. **Scenario**: Galpón sin lotes que lo referencien
	 - **Given** existe un galpón que no es referenciado por ningún lote
	 - **When** el usuario consulta su detalle
	 - **Then** el sistema muestra los datos del galpón
	- **And** informa que no existen lotes registrados para ese galpón

 3. **Scenario**: Consulta del historial de lotes
	 - **Given** existen un lote activo y lotes anteriores cuya llave foránea referencia al galpón
	 - **When** el usuario consulta el historial
	- **Then** el sistema muestra el lote activo y los lotes anteriores que referencian al galpón

 ### User Story 3 - Gestionar resultados y errores de consulta (Priority: P2)

 Como administrador u operario de granja, quiero recibir información clara cuando una consulta no tiene resultados o presenta un error para poder continuar trabajando.

 **Why this priority**: Los mensajes claros evitan interpretaciones incorrectas y permiten reintentar la consulta sin perder los filtros aplicados.

 **Independent Test**: Se puede probar usando un nombre sin coincidencias y simulando un error de consulta para verificar los mensajes y la conservación de los criterios.

 **Acceptance Scenarios**:

 1. **Scenario**: Búsqueda sin coincidencias
	 - **Given** no existe un galpón cuyo nombre coincida con el texto ingresado
	 - **When** el usuario ejecuta la búsqueda
	 - **Then** el sistema muestra un mensaje indicando que no encontró coincidencias
	 - **And** muestra nuevamente todos los galpones

 2. **Scenario**: Campo del lote no disponible
	 - **Given** el lote tiene un campo vacío o inválido
	 - **When** el usuario consulta el detalle
	 - **Then** el sistema muestra “No disponible” para ese campo
	 - **And** continúa mostrando el resto de la información válida

 3. **Scenario**: Error durante la consulta
	 - **Given** ocurre un error al recuperar los datos
	 - **When** el sistema intenta completar la consulta
	 - **Then** muestra un mensaje específico del error
	 - **And** conserva la búsqueda, los filtros y el criterio de ordenamiento
	 - **And** permite reintentar la consulta

 ## Edge Cases

 - Si el usuario realiza una búsqueda con el nombre vacío, el sistema debe mostrar todos los galpones aplicando la paginación y el ordenamiento predeterminados.
 - Si el nombre contiene espacios al inicio o al final, el sistema debe eliminarlos antes de ejecutar la búsqueda.
 - Si el usuario ingresa una coincidencia parcial, el sistema debe devolver todos los galpones cuyos nombres contengan el texto buscado.
 - Si no existen coincidencias, el sistema debe mostrar un mensaje informativo y recuperar todos los galpones.
 - Si existen más de 10 galpones, el sistema debe dividir los resultados en páginas de 10 y permitir navegar entre ellas sin duplicar ni omitir registros.
 - Si varios galpones cumplen los criterios de búsqueda, el sistema debe mostrar cada uno con sus propios datos y permitir ordenarlos según el criterio seleccionado.
 - Si un lote es registrado, el sistema debe guardar en la entidad lote la llave foránea del galpón correspondiente.
 - Si se intenta registrar un lote sin llave foránea de galpón o con una llave que no existe, el sistema debe rechazar el registro y mostrar un mensaje específico.
 - Si se intenta registrar un lote con un UUID ya utilizado, el sistema debe rechazar el registro e informar que el UUID debe ser único.
 - Si un lote no tiene un UUID válido, el sistema no debe mostrarlo como un lote consultable y debe informar una inconsistencia de datos.
 - La entidad galpón no debe recibir ni almacenar directamente un lote; la consulta debe obtener los lotes mediante la llave foránea almacenada en lote.
 - Si un galpón es referenciado por varios lotes históricos, el sistema debe mostrar el lote con fecha de ingreso más reciente como activo y los demás en el historial.
 - Si ningún lote referencia al galpón, el sistema debe mostrar los datos del galpón y un mensaje indicando que no existen lotes registrados para él.
 - Si un campo del lote está vacío o es inválido, el sistema debe mostrar “No disponible” en ese campo y continuar mostrando los datos válidos.
 - Si el galpón consultado no existe al abrir el detalle, el sistema debe mostrar un mensaje específico, no presentar datos de otro galpón y permitir volver al listado.
 - Si ocurre un error técnico durante la consulta, el sistema debe mostrar un mensaje específico, conservar los criterios introducidos y permitir reintentar.

 ## Requirements *(mandatory)*

 ### Functional Requirements

 - **FR-001**: El sistema DEBE permitir a administradores y operarios consultar los galpones registrados.
 - **FR-002**: El sistema DEBE mostrar en el listado el nombre, capacidad y estado de cada galpón.
 - **FR-003**: El sistema DEBE permitir buscar galpones mediante coincidencias parciales por nombre.
 - **FR-004**: El sistema DEBE mostrar todos los galpones cuando el usuario no ingrese un nombre de búsqueda.
 - **FR-005**: El sistema DEBE mostrar un mensaje cuando no existan coincidencias y, a continuación, mostrar todos los galpones.
 - **FR-006**: El sistema DEBE permitir filtrar el listado por estado del galpón.
 - **FR-007**: El sistema DEBE permitir ordenar el listado por nombre, capacidad y estado.
 - **FR-008**: El sistema DEBE mostrar 10 galpones por página y permitir navegar entre páginas.
 - **FR-009**: El sistema DEBE permitir abrir el detalle de un galpón desde el listado.
 - **FR-010**: El sistema DEBE mostrar en el detalle el nombre, capacidad y estado del galpón.
 - **FR-011**: El sistema DEBE mostrar el UUID único del lote, su nombre, población inicial, población actual, fecha de ingreso, edad en días y costo total cuando exista un lote cuya llave foránea referencie al galpón consultado.
 - **FR-012**: El sistema DEBE calcular la edad del lote en días a partir de su fecha de ingreso y la fecha actual.
 - **FR-013**: El sistema DEBE considerar como lote activo el lote cuya llave foránea referencie al galpón consultado y tenga la fecha de ingreso más reciente.
 - **FR-014**: El sistema DEBE permitir consultar el historial de lotes cuya llave foránea referencie al galpón consultado.
 - **FR-015**: El sistema DEBE informar cuando ningún lote referencie al galpón consultado.
 - **FR-016**: El sistema DEBE mostrar “No disponible” cuando un campo del lote esté vacío o sea inválido.
 - **FR-017**: El sistema DEBE validar que cada lote consultado tenga un UUID único y válido.
 - **FR-018**: El sistema DEBE mostrar mensajes específicos cuando el galpón no exista, un lote tenga un UUID inválido o se produzca un error durante la consulta.
 - **FR-019**: El sistema DEBE conservar la búsqueda, filtros y ordenamiento cuando ocurra un error técnico.
 - **FR-020**: El sistema DEBE permitir reintentar una consulta que haya fallado.
 - **FR-021**: El sistema NO DEBE mostrar datos de un galpón diferente al seleccionado.
 - **FR-022**: La diferencia de permisos entre administradores y operarios queda pendiente de definición.

 ### Key Entities *(include if feature involves data)*

 - **Galpón**: Representa una unidad física de producción avícola. No recibe ni almacena directamente lotes.
	- UUID único
	- Nombre
	- capacidad
	- Estado: Disponible, Productivo, En cosecha, Vaciado sanitario, Mantenimiento o Aislamiento

 - **Lote**: Representa un grupo de pollos registrado para un galpón.
    - UUID único
	- Nombre
	- Población inicial
	- Población actual
	- Fecha de ingreso
	- Costo total
	- Llave foránea del galpón para el cual fue registrado

 - **Administrador de granja**: Usuario autorizado para consultar la información de galpones y lotes.

 - **Operario de granja**: Usuario autorizado para consultar la información de galpones y lotes.

 ## Success Criteria *(mandatory)*

 ### Measurable Outcomes

 - **SC-001**: El 100 % de los galpones registrados debe poder aparecer en el listado de consulta.
 - **SC-002**: El 100 % de las búsquedas parciales debe devolver todos los galpones coincidentes.
 - **SC-003**: El 100 % de los detalles consultados debe mostrar correctamente los atributos del galpón seleccionado.
 - **SC-004**: El 100 % de los galpones referenciados por uno o más lotes debe mostrar la información disponible, incluido el UUID único, del lote más reciente.
 - **SC-005**: El 100 % de los galpones no referenciados por un lote debe mostrar un mensaje informativo sin impedir la consulta de sus datos.
 - **SC-006**: El 100 % de las respuestas con campos faltantes debe mostrar “No disponible” en lugar de datos incorrectos.
 - **SC-007**: El 100 % de los errores de consulta debe mostrar un mensaje específico, conservar los criterios y permitir reintentar.
 - **SC-008**: El 100 % de los listados debe respetar la paginación de 10 registros y el criterio de ordenamiento seleccionado.
 - **SC-009**: El 100 % de los lotes consultables debe tener un UUID único y válido.
