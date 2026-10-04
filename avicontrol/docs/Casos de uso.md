# Definición de los casos de uso - Modulo 1

Este documento explica brevemente los casos de uso encontrados en el diagrama, dando una información mas profunda de lo que se quiere conseguir con cada caso de uso.
### Actores
- **Administrador**: Este actor tiene a su cargo la fase operativa del galpón, es el único que puede registrar un galpón, editar un galpón, registrar un lote y actualizar el estado del galpón. 

- **Módulo 2**: Este modulo se encarga de remover pollos en caso de que muera, también genera una alerta al administrador en la que avisa que en el galpón se encuentran uno o varios pollos enfermos, además, el modulo necesita saber la edad del lote para poder llevar control de la dieta de los pollos.

- **Módulo 3**: Este modulo necesita conocer los pollos que se encuentran en el lote para calcular la venta bruta, además, necesita conocer cuanto costó el lote para calcular los costos de crianza.

- **Técnico de infraestructura**: Este actor tiene a su cargo generar una alerta al administrador para avisarle que se encontraron fallas en la infraestructura del galpón que necesitan ser atendidas.

- **Operario de granja**: Este actor puede consultar el listado de galpones y el detalle de cada galpón con su lote activo. No puede registrar, editar ni cambiar el estado de ningún galpón.

---
## Casos de uso
### 1. Administrador
- **Registrar galpón**: En este caso de uso el administrador ocupa llenar un formulario donde ingresa los datos necesarios del galpón (nombre y capacidad).

- **Registrar Lote**: En este caso de uso el administrador ocupa llenar un formulario donde ingresa los datos necesarios del lote (nombre, fecha de ingreso, población inicial y el costo del lote).

- **Editar galpón**: En este caso de uso el administrador ocupa editar el galpón en caso de que sea ampliado o necesite un cambio de nombre.

- **Actualizar estado**: Este es el único caso de uso que valida y persiste cualquier cambio de estado del galpón. Recibe solicitudes originadas por el administrador, el registro de lote, las alertas sanitarias, la atención de alertas de mantenimiento, el vaciado sanitario y el proceso automático de finalización del vaciado.

**Nota**: las transiciones tienen las siguientes restricciones:
- **Disponible -> Productivo**: Solo mediante una solicitud del caso de uso "Registrar lote" después de crear correctamente el lote.
- **Disponible -> Mantenimiento**: Mediante una solicitud del administrador al atender una alerta de mantenimiento.
- **Productivo -> En cosecha**: Mediante una solicitud del administrador.
- **Productivo -> Aislamiento**: Solo mediante una solicitud válida del caso de uso "Recibir alerta sanitaria".
- **En cosecha -> Vaciado sanitario**: Solo mediante una solicitud válida del caso de uso "Recibir vaciado sanitario".
- **Aislamiento -> Productivo**: Solo mediante una solicitud válida de reanudación del caso de uso "Recibir alerta sanitaria".
- **Aislamiento -> En cosecha**: Mediante una solicitud del administrador, para retomar la cosecha una vez resuelto el aislamiento sanitario.
- **Mantenimiento -> Disponible**: Mediante una solicitud del administrador al finalizar el mantenimiento.
- **Vaciado sanitario -> Disponible**: Mediante una solicitud del proceso automático al finalizar el periodo configurado.
- Toda solicitud DEBE validarse contra el estado vigente dentro de la misma transacción que persiste el cambio.

### 2. Módulo 2
- **Recibir mortalidad del galpón**: En este caso de uso el módulo 2 avisa al módulo 1 que murieron uno o varios pollos en el galpón, para que se descuente la cantidad de la población actual del lote. 

- **Actualizar población actual**: En este caso de uso el sistema actualiza la población actual del lote a partir de uno o varios pollos retirados por muerte. No modifica el estado del galpón; cualquier cambio de estado debe remitirse al caso de uso "Actualizar estado".

- **Recibir alerta sanitaria**: En este caso de uso el modulo 2 ocupa avisar al administrador de que en el galpón hay una enfermedad que contagió a uno o varios pollos y el galpón necesita estar en aislamiento.

- **Recibir vacíado sanitario**: En este caso de uso el módulo 2 avisa al módulo 1 que la cosecha finalizó y el galpón debe pasar a estado Vaciado sanitario. Una vez cumplido el periodo de limpieza configurado, el proceso automático lo devuelve a Disponible.

### 3. Técnico de infraestructura
- **Generar alerta de mantenimiento**: En este caso de uso el técnico de infraestructura ocupa avisarle al administrador que hay fallas en el galpón y necesitan atención.

### 4. Compartidos (Administrador, Operario de granja, Módulo 2 y Módulo 3)
- **Consultar galpón y/o lote**: En este caso de uso el: 
	1. **Administrador**: Ocupa visualizar los galpones y lotes activos en el sistema.
	2. **Operario de granja**: Ocupa visualizar los galpones y lotes activos para supervisar la situación operativa de la granja.
	3. **Módulo 2**: Ocupa visualizar los galpones y lotes activos para conocer la edad del lote para el control de la dieta de los pollos.
	4. **Módulo 3**: Ocupa visualizar los galpones y lotes activos para conocer el costo del lote y los pollos finales para poder calcular los costos de crianza y la venta bruta del lote. 
