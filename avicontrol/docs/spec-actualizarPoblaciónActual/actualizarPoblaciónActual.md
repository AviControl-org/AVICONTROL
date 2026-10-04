 # Feature Specification: Actualizar población actual

 **Created**: 2026-09-04

 ## User Scenarios & Testing

 ### User Story 1 - Actualizar la población actual del lote (Priority: P1)

 Como administrador, quiero actualizar la población actual de un lote a partir de la cantidad de pollos retirados por muerte recibida en una alerta, para mantener disponible el número real de pollos del lote.

 **Why this priority**: La población actual es un dato operativo del lote y debe disminuir cada vez que se confirma una mortalidad. La población inicial debe permanecer como referencia histórica del registro original.

 **Independent Test**: Se puede recibir desde el spec “Recibir mortalidad del galpón” una cantidad válida de 25 pollos muertos para un lote con población actual de 1000 y verificar que la población actual quede en 975, que la población inicial no cambie y que el galpón conserve su estado.

 **Acceptance Scenarios**:

 1. **Scenario**: Actualización exitosa por mortalidad
	 - **Given** existe un lote activo con población inicial de 1200 y población actual de 1000
	 - **When** el administrador recibe del spec “Recibir mortalidad del galpón” una solicitud válida con 25 pollos muertos
	 - **Then** el sistema calcula `población actual nueva = población actual anterior - cantidad de muertos`
	 - **And** actualiza la población actual del lote a 975
	 - **And** conserva la población inicial en 1200
	 - **And** no modifica los demás datos del lote ni el estado del galpón

 2. **Scenario**: Población actual queda exactamente en cero
	 - **Given** el lote tiene una población actual de 25
	 - **When** el administrador recibe una solicitud válida con 25 pollos muertos
	 - **Then** el sistema actualiza la población actual a cero
	 - **And** no cambia automáticamente el estado del galpón
	 - **And** queda pendiente la orden independiente de vaciado sanitario

 3. **Scenario**: Varias actualizaciones consecutivas
	 - **Given** el lote tiene una población actual de 1000
	 - **When** el administrador procesa sucesivamente solicitudes válidas de 25 y 10 pollos muertos
	 - **Then** la población actual queda en 965
	 - **And** cada solicitud se calcula usando la población actual vigente anterior
	 - **And** la población inicial permanece sin cambios

 ### User Story 2 - Rechazar actualizaciones inválidas (Priority: P1)

 Como administrador, quiero que se rechacen solicitudes inconsistentes para evitar que la población actual del lote sea incorrecta o negativa.

 **Independent Test**: Se pueden enviar solicitudes con un lote inexistente, un lote no activo, cantidades inválidas o cantidades superiores a la población actual y verificar que el lote no cambie.

 **Acceptance Scenarios**:

 1. **Scenario**: Lote inexistente
	 - **Given** la solicitud identifica un lote que no existe
	 - **When** el administrador intenta procesarla
	 - **Then** el sistema rechaza la solicitud
	 - **And** no modifica ningún lote

 2. **Scenario**: Cantidad de muertos inválida
	 - **Given** la cantidad recibida es cero, negativa, decimal o no numérica
	 - **When** el administrador intenta procesarla
	 - **Then** el sistema rechaza la solicitud
	 - **And** conserva la población actual y la población inicial sin cambios

 3. **Scenario**: Cantidad mayor que la población actual
	 - **Given** el lote tiene una población actual de 100 y la solicitud informa 101 muertos
	 - **When** el administrador intenta procesarla
	 - **Then** el sistema rechaza la solicitud
	 - **And** no permite que la población actual sea negativa

 4. **Scenario**: Solicitud duplicada
	 - **Given** una solicitud con UUID de alerta ya actualizó el lote correctamente
	 - **When** el administrador recibe nuevamente la misma solicitud
	 - **Then** el sistema no vuelve a descontar la cantidad de muertos
	 - **And** conserva la población actual resultante del primer procesamiento

 5. **Scenario**: UUID reutilizado con datos diferentes
	 - **Given** una solicitud con un UUID de alerta ya fue procesada
	 - **When** llega el mismo UUID con una cantidad de muertos o un lote diferente
	 - **Then** el sistema rechaza la solicitud por inconsistencia
	 - **And** no modifica la población actual

 ## Edge Cases

 - La cantidad de muertos debe ser un entero positivo mayor que cero.
 - La población actual puede llegar exactamente a cero, pero nunca puede ser negativa.
 - La población inicial es inmutable en este flujo y no se utiliza como valor que se reinicie en cada actualización.
 - La fórmula correcta para actualizaciones sucesivas es `población actual anterior - cantidad de muertos`.
 - Solo se puede actualizar un lote activo, es decir, un lote cuya llave foránea `galpon_id` referencia actualmente al galpón recibido en la solicitud.
 - La solicitud debe provenir del spec “Recibir mortalidad del galpón” y contener la cantidad de muertos y los identificadores previamente validados.
 - Una solicitud duplicada debe ser idempotente y no puede producir un segundo descuento.
 - El mismo UUID de alerta no puede representar datos diferentes.
 - La actualización no modifica el estado del galpón, incluso cuando la población actual llega a cero.
 - El cambio a “Vaciado sanitario” se realiza únicamente mediante el flujo de recepción de la orden de vaciado sanitario.
 - Si falla la persistencia, la población actual debe conservar su valor anterior.

 ## Requirements

 ### Functional Requirements

 - **FR-001**: El sistema DEBE permitir al administrador actualizar la población actual de un lote mediante una solicitud remitida por el spec “Recibir mortalidad del galpón”.
 - **FR-002**: La solicitud DEBE contener el UUID de la alerta, UUID del lote, UUID del galpón y cantidad de pollos muertos.
 - **FR-003**: El sistema DEBE validar que exista el lote identificado.
 - **FR-004**: El sistema DEBE validar que el lote sea activo y que su `galpon_id` corresponda al UUID del galpón recibido.
 - **FR-005**: El sistema DEBE validar que la cantidad de muertos sea un entero positivo.
 - **FR-006**: El sistema DEBE rechazar la solicitud cuando la cantidad de muertos sea mayor que la población actual.
 - **FR-007**: El sistema DEBE calcular la nueva población aplicando `población actual nueva = población actual anterior - cantidad de muertos`.
 - **FR-008**: El sistema DEBE persistir la nueva población actual del lote.
 - **FR-009**: El sistema NO DEBE modificar la población inicial del lote.
 - **FR-010**: El sistema NO DEBE modificar los demás atributos propios del lote.
 - **FR-011**: El sistema NO DEBE cambiar el estado del galpón como consecuencia de esta actualización.
 - **FR-012**: El sistema DEBE permitir que la población actual llegue a cero.
 - **FR-013**: El sistema DEBE procesar de forma idempotente una solicitud recibida más de una vez.
 - **FR-014**: El sistema DEBE rechazar un UUID de alerta ya procesado cuando sus demás datos sean diferentes.
 - **FR-015**: La validación y persistencia de la nueva población DEBEN ejecutarse en una única transacción atómica.
 - **FR-016**: Si la actualización falla, el sistema NO DEBE conservar cambios parciales.
 - **FR-017**: El sistema DEBE mantener un registro técnico del UUID de alerta y los datos aplicados para garantizar la idempotencia.
 - **FR-018**: El sistema NO DEBE iniciar automáticamente el vaciado sanitario cuando la población actual llegue a cero.
 - **FR-019**: Las solicitudes rechazadas NO DEBEN modificar la población actual, la población inicial ni el estado del galpón.

 ### Key Entities

 - **Solicitud de actualización de población**: Datos remitidos por el spec “Recibir mortalidad del galpón”.
	- UUID de alerta
	- UUID del lote
	- UUID del galpón
	- Cantidad de pollos muertos

 - **Registro técnico de procesamiento**: Control interno para impedir el doble procesamiento de una alerta y detectar la reutilización de su UUID.

 - **Lote**: Grupo de pollos registrado en un galpón.
	- UUID único
	 - Nombre, obligatorio y único
	- Población inicial, inmutable
	- Población actual, modificable por este flujo
	 - Fecha de ingreso, no futura
	 - Costo total, entero positivo en pesos colombianos
	- `galpon_id`, referencia al galpón del lote activo
	 - Este caso de uso no modifica el nombre, la fecha de ingreso, el costo total, la población inicial ni el `galpon_id`.

 - **Galpón**: Unidad física relacionada con el lote. Su estado no se modifica en este caso de uso.

 - **Administrador**: Actor responsable de recibir y procesar la solicitud de actualización.

 ## Success Criteria

 ### Measurable Outcomes

 - **SC-001**: El 100 % de las solicitudes válidas actualiza la población actual con la resta exacta de la cantidad de muertos.
 - **SC-002**: El 100 % de las solicitudes con cantidad mayor que la población actual es rechazado.
 - **SC-003**: El 100 % de los lotes actualizados conserva intacta su población inicial.
 - **SC-004**: El 100 % de las solicitudes duplicadas evita un segundo descuento.
 - **SC-005**: El 100 % de las solicitudes inválidas deja sin cambios el lote y el galpón.
 - **SC-006**: El 100 % de las actualizaciones permite llegar a población cero sin producir valores negativos.
 - **SC-007**: El 100 % de las actualizaciones aceptadas conserva el estado vigente del galpón.
 - **SC-008**: El 100 % de las operaciones aceptadas es atómico y no deja persistencia parcial.
