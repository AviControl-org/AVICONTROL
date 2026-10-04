 # Feature Specification: Recibir mortalidad del galpón

 **Created**: 2026-09-04

 ## User Scenarios & Testing

 ### User Story 1 - Recibir una alerta válida de mortalidad (Priority: P1)

 Como administrador, quiero recibir una alerta enviada por el Módulo 2 con la cantidad de pollos retirados por muerte para remitirla al proceso de actualización de la población actual del lote activo asociado al galpón.

 **Why this priority**: La alerta debe llegar al proceso responsable de actualizar la población actual, conservando la separación entre la recepción del evento y la modificación del lote.

 **Independent Test**: Se puede enviar una alerta válida para un galpón productivo con un lote activo y verificar que el administrador la reciba, que sea validada y que se remita al spec “Actualizar población actual” con los datos correctos.

 **Acceptance Scenarios**:

 1. **Scenario**: Recepción exitosa de una alerta de mortalidad
	 - **Given** existe un galpón en estado “Productivo” con un lote activo relacionado mediante `galpon_id`, y la población actual del lote es 1000
	 - **When** el Módulo 2 envía una alerta con UUID de alerta, UUID de galpón, UUID de lote, fecha y hora del evento, y cantidad de muertos igual a 25
	- **Then** el sistema valida los datos y la relación entre el lote y el galpón
	- **And** remite la alerta al spec “Actualizar población actual”
	- **And** entrega el UUID de alerta, el UUID del lote, el UUID del galpón, la fecha y hora del evento y la cantidad de muertos
	- **And** no modifica directamente la población del lote ni el estado del galpón

 2. **Scenario**: Recepción repetida de la misma alerta
	 - **Given** una alerta con el mismo UUID ya fue procesada correctamente
	 - **When** el Módulo 2 reenvía la alerta
	- **Then** el sistema no remite nuevamente la alerta al spec “Actualizar población actual”
	- **And** no genera una segunda solicitud de actualización
	- **And** conserva el resultado original del procesamiento

 3. **Scenario**: Población actual queda en cero
	 - **Given** la población actual del lote es igual a la cantidad de muertos recibida
	 - **When** el sistema procesa una alerta válida
	- **Then** remite la alerta al spec “Actualizar población actual”
	- **And** deja que ese spec determine la actualización de la población actual a cero
	- **And** no cambia el estado del galpón
	- **And** espera una alerta independiente de vaciado sanitario para cambiar el estado del galpón

 ### User Story 2 - Rechazar alertas de mortalidad inválidas (Priority: P1)

 Como sistema AVICONTROL, quiero rechazar alertas incompletas o inconsistentes para evitar descontar pollos del lote equivocado o reducir la población por debajo de cero.

 **Independent Test**: Se pueden enviar alertas con datos faltantes, entidades inexistentes, relación lote-galpón inválida, estado incompatible o cantidades inválidas y verificar que la población del lote permanezca sin cambios.

 **Acceptance Scenarios**:

 1. **Scenario**: Datos obligatorios faltantes
	 - **Given** la alerta no contiene el UUID de alerta, UUID de galpón, UUID de lote, fecha y hora del evento o cantidad de muertos
	 - **When** el sistema recibe el mensaje
	 - **Then** rechaza la alerta
	 - **And** no modifica el lote ni el galpón

 2. **Scenario**: Entidad o relación inexistente
	 - **Given** el UUID del galpón o del lote no existe, o el lote no referencia al galpón recibido
	 - **When** el sistema recibe la alerta
	 - **Then** rechaza la alerta
	 - **And** no modifica la población actual

 3. **Scenario**: Galpón en estado incompatible
	 - **Given** el galpón existe, pero no está en estado “Productivo”
	 - **When** el sistema recibe la alerta
	 - **Then** rechaza la alerta
	 - **And** conserva sin cambios la población del lote y el estado del galpón

 4. **Scenario**: Cantidad de muertos inválida
	 - **Given** la cantidad de muertos es cero, negativa, decimal o no numérica
	 - **When** el sistema recibe la alerta
	 - **Then** rechaza la alerta
	 - **And** no modifica la población actual

 5. **Scenario**: Cantidad de muertos mayor que la población actual
	 - **Given** la población actual del lote es 100 y la alerta informa 101 muertos
	 - **When** el sistema recibe la alerta
	 - **Then** rechaza la alerta
	 - **And** no permite que la población actual sea negativa

 6. **Scenario**: UUID reutilizado con datos diferentes
	 - **Given** ya fue procesada una alerta con un UUID determinado
	 - **When** llega otra alerta con ese UUID y una cantidad, lote, galpón o fecha diferente
	 - **Then** el sistema rechaza la inconsistencia
	 - **And** no modifica la población actual

 ## Edge Cases

 - La cantidad de muertos debe ser un entero positivo mayor que cero.
 - La validación de que la cantidad de muertos no supere la población actual corresponde al spec “Actualizar población actual”.
 - La población inicial se conserva sin modificaciones; la fórmula `población actual nueva = población actual anterior - cantidad de muertos` corresponde al spec “Actualizar población actual”.
 - Solo se aceptan alertas para lotes activos, entendidos como lotes cuya `galpon_id` referencia actualmente al galpón recibido.
 - El galpón debe estar en estado “Productivo” al momento de procesar la alerta.
 - La fecha y hora del evento no puede ser futura y debe corresponder al día de recepción de la alerta.
 - Una alerta duplicada debe ser idempotente: no puede volver a remitirse al spec “Actualizar población actual”.
 - El mismo UUID de alerta no puede representar datos diferentes.
 - Las alertas rechazadas no deben modificar el lote, el galpón ni el registro técnico de alertas procesadas.
 - La recepción de mortalidad no cambia directamente la población del lote ni el estado del galpón.
 - El cambio a “Vaciado sanitario” pertenece a otro flujo y solo se ejecuta al recibir la orden correspondiente.
 - El sistema no expone una consulta de alertas de mortalidad para administradores u operarios.

 ## Requirements

 ### Functional Requirements

 - **FR-001**: El sistema DEBE recibir alertas de mortalidad emitidas por el Módulo 2.
 - **FR-002**: La alerta DEBE contener obligatoriamente el UUID de alerta, UUID de galpón, UUID de lote, fecha y hora del evento y cantidad de pollos muertos.
 - **FR-003**: El sistema DEBE validar que el UUID de alerta no haya sido procesado previamente.
 - **FR-004**: El sistema DEBE procesar de forma idempotente una alerta recibida más de una vez.
 - **FR-005**: El sistema DEBE rechazar una alerta cuyo UUID ya exista con datos diferentes.
 - **FR-006**: El sistema DEBE validar que exista el galpón identificado en la alerta.
 - **FR-007**: El sistema DEBE validar que exista el lote identificado y que su `galpon_id` corresponda al galpón recibido.
 - **FR-008**: El sistema DEBE aceptar la alerta únicamente cuando el galpón esté en estado “Productivo”.
 - **FR-009**: El sistema DEBE validar que el lote relacionado sea el lote activo del galpón.
 - **FR-010**: El sistema DEBE validar que la cantidad de muertos sea un entero positivo.
 - **FR-011**: El sistema DEBE remitir al spec “Actualizar población actual” la cantidad de muertos y los identificadores validados de la alerta.
 - **FR-012**: El sistema NO DEBE actualizar directamente la población actual del lote.
 - **FR-013**: El sistema NO DEBE modificar la población inicial del lote.
 - **FR-014**: El sistema NO DEBE cambiar el estado del galpón como consecuencia de la recepción de una alerta de mortalidad.
 - **FR-015**: El sistema DEBE remitir al spec “Actualizar población actual” una alerta válida aunque la cantidad de muertos pueda dejar la población actual en cero.
 - **FR-016**: El sistema DEBE validar que la fecha y hora del evento no sea futura ni corresponda a un día diferente al de recepción.
 - **FR-017**: La validación de la alerta y su remisión al spec “Actualizar población actual” DEBEN ejecutarse de forma atómica.
 - **FR-018**: Si falla cualquier validación o la remisión, el sistema NO DEBE conservar cambios parciales.
 - **FR-019**: El sistema DEBE mantener un registro técnico mínimo de UUID y datos procesados para garantizar la idempotencia, sin crear una entidad de negocio consultable de alertas de mortalidad.
 - **FR-020**: Las alertas rechazadas NO DEBEN remitirse al spec “Actualizar población actual” ni marcarse como procesadas exitosamente.
 - **FR-021**: El procesamiento de mortalidad NO DEBE generar ni reemplazar la orden de vaciado sanitario.

 ### Key Entities

 - **Alerta de mortalidad**: Mensaje de integración recibido del Módulo 2. No se expone como entidad consultable por usuarios.
	- UUID único de la alerta
	- UUID del galpón
	- UUID del lote
	- Cantidad de pollos muertos
	- Fecha y hora del evento

 - **Registro técnico de procesamiento**: Control interno mínimo para impedir descuentos duplicados y detectar reutilización de UUID. No representa un historial funcional consultable.

 - **Lote**: Grupo de pollos asociado actualmente a un galpón. Su población se actualiza exclusivamente mediante el spec “Actualizar población actual”.
	- UUID único
	- Población inicial, inmutable durante este flujo
	- Población actual, valor que se reduce en el spec “Actualizar población actual”
	- `galpon_id`, llave foránea obligatoria para el lote activo

 - **Galpón**: Unidad física cuyo estado debe permanecer “Productivo” durante este flujo.

 - **Módulo 2**: Actor externo que emite la alerta de mortalidad.

 ## Success Criteria

 ### Measurable Outcomes

 - **SC-001**: El 100 % de las alertas válidas se remite al spec “Actualizar población actual” con la cantidad de muertos correcta.
 - **SC-002**: El 100 % de las alertas duplicadas evita una segunda remisión al spec “Actualizar población actual”.
 - **SC-003**: El 100 % de las alertas inválidas no se remite al spec “Actualizar población actual” ni modifica el estado del galpón.
 - **SC-004**: El 100 % de los lotes afectados conserva intacta su población inicial.
 - **SC-005**: El 100 % de las alertas aceptadas se procesa sin cambiar directamente la población actual ni el estado del galpón.
 - **SC-006**: El 100 % de las alertas aceptadas mantiene el galpón en su estado vigente y deja el cambio de población al spec correspondiente.
 - **SC-007**: El 100 % de las operaciones aceptadas es atómico y no deja remisiones parciales.
