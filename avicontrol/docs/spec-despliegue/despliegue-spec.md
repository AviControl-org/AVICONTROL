## Feature Specification: Despliegue en AWS

**Created**: 2026-11-18
**Plan general del despliegue**: [plan-despliegue-aws.md](../plan-general/plan-despliegue-aws.md)
**Plan de implementación**: [plan-despliegue.md](plan-despliegue.md)

## User Scenarios & Testing

### User Story 1 - Usar la aplicación desde internet (Priority: P1)

Como administrador o técnico quiero abrir el frontend del Módulo 1 desde un navegador con la dirección del servidor, para gestionar galpones y lotes sin instalar nada en mi computador.

**Why this priority**: Es lo primero que se muestra en la revisión. Sin el frontend en línea no hay forma de ver el sistema funcionando ni de crear los datos que usan los otros módulos.

**Independent Test**: Desde un computador que no sea el servidor, abrir `http://IP_ELASTICA`, registrar un galpón, registrar un lote en él y ver que el galpón pasa a PRODUCTIVO. No depende de los otros módulos.

**Acceptance Scenarios**:

1. **Scenario**: Abrir el frontend
   - **Given** el despliegue está levantado en la instancia de AWS
   - **When** cualquier persona abre `http://IP_ELASTICA` en un navegador
   - **Then** ve el frontend del Módulo 1 con el listado de galpones del servidor

2. **Scenario**: Operar contra la base del servidor
   - **Given** el frontend está abierto desde internet
   - **When** el administrador registra un galpón y luego un lote en ese galpón
   - **Then** los datos quedan guardados en la base de datos del servidor y el galpón queda PRODUCTIVO

3. **Scenario**: Recargar una ruta interna
   - **Given** el usuario está en una página interna del frontend (por ejemplo, el detalle de un galpón)
   - **When** recarga la página o abre ese enlace directamente
   - **Then** el frontend carga la misma página y no aparece un error 404 del servidor web

---

### User Story 2 - Recibir los eventos reales del Módulo 2 (Priority: P1)

Como Módulo 2 quiero publicar mortalidad, alertas sanitarias y vaciados sanitarios en el Kafka que aloja el Módulo 1, para que el Módulo 1 actualice población y estados con datos reales.

**Why this priority**: La revisión pide la aplicación desplegada funcionando con eventos. Es la integración que no se puede mostrar en local.

**Independent Test**: Publicar un evento de mortalidad válido desde fuera de la instancia al puerto 9092 y verificar por la API que la población actual del lote bajó. Antes de que el Módulo 2 esté listo se prueba igual con el productor de consola de Kafka dentro de la instancia.

**Acceptance Scenarios**:

1. **Scenario**: Mortalidad publicada por el Módulo 2
   - **Given** existe un galpón PRODUCTIVO con lote activo en el servidor y la IP del Módulo 2 está permitida en el puerto 9092
   - **When** el Módulo 2 publica en `avicontrol.mortalidad` un evento válido con clave `galponId`
   - **Then** el Módulo 1 descuenta la cantidad de la población actual del lote

2. **Scenario**: Alerta sanitaria
   - **Given** existe un galpón PRODUCTIVO en el servidor
   - **When** el Módulo 2 publica en `avicontrol.alerta-sanitaria` una alerta válida
   - **Then** el galpón pasa a AISLAMIENTO

3. **Scenario**: Evento publicado con el backend detenido
   - **Given** el backend está detenido o reiniciándose y el broker sigue arriba
   - **When** el Módulo 2 publica un evento válido
   - **Then** al volver el backend, el evento se procesa (D-01, `earliest` o el último offset confirmado del grupo)

4. **Scenario**: Conexión desde una IP no permitida
   - **Given** una IP que no está en el grupo de seguridad
   - **When** intenta conectarse al puerto 9092
   - **Then** la conexión no se establece

---

### User Story 3 - Consultas REST de los Módulos 2 y 3 (Priority: P2)

Como Módulo 2 o Módulo 3 quiero consultar galpones y lotes por la API REST del Módulo 1 en el servidor, para trabajar con los datos actuales.

**Why this priority**: Es la otra frontera con los demás módulos. Va después de la P1 porque los mismos `GET` ya los prueba el frontend propio.

**Independent Test**: Desde fuera de la instancia, `GET /api/v1/galpones` y `GET /api/v1/galpones/{galponId}` responden con el contrato de `acuerdo-contratos-entre-modulos.md`.

**Acceptance Scenarios**:

1. **Scenario**: Listar galpones desde otro módulo
   - **Given** la IP del módulo consultante está permitida (o la consulta entra por el puerto 80, según D-03)
   - **When** llama `GET /api/v1/galpones`
   - **Then** recibe `200` con la página de galpones del servidor

2. **Scenario**: Detalle con lote activo
   - **Given** existe un galpón con lote activo en el servidor
   - **When** el módulo consultante llama `GET /api/v1/galpones/{galponId}`
   - **Then** recibe el galpón con `loteActivo`

---

### User Story 4 - Levantar y mantener el despliegue (Priority: P2)

Como integrante del Módulo 1 quiero levantar todo el sistema en la instancia con un solo comando y que los datos se conserven si la máquina se reinicia, para no rehacer el despliegue antes de cada prueba.

**Why this priority**: Reduce el riesgo el día de la revisión. No agrega funcionalidad visible.

**Independent Test**: En la instancia, `docker compose up -d --build` deja los cuatro contenedores arriba. Se reinicia la instancia y, sin tocar nada, el frontend vuelve a responder con los mismos galpones.

**Acceptance Scenarios**:

1. **Scenario**: Primer arranque
   - **Given** la instancia tiene Docker, el repositorio y el archivo `.env` con la IP elástica y la contraseña de la base
   - **When** se ejecuta `docker compose up -d --build` en la carpeta `despliegue`
   - **Then** quedan arriba nginx, backend, postgres y kafka, y Flyway aplica las migraciones sin los datos locales

2. **Scenario**: Reinicio de la instancia
   - **Given** hay galpones y lotes creados en el servidor
   - **When** la instancia se reinicia
   - **Then** los contenedores vuelven a arrancar solos y los datos siguen ahí

3. **Scenario**: Actualizar el código
   - **Given** hay un cambio nuevo en la rama que se despliega
   - **When** se hace `git pull` y `docker compose up -d --build` en la instancia
   - **Then** se reconstruyen las imágenes y los datos de la base se conservan

---

### Edge Cases

- ¿Qué pasa si el backend arranca antes que Kafka? El consumidor de Spring reintenta la conexión y empieza a leer cuando el broker responde. No hay que ordenar el arranque a mano.
- ¿Qué pasa si los tópicos no existen al arrancar? Se crean una vez en la instalación (plan, paso de tópicos). El broker también los crea solo cuando alguien los usa, pero no se depende de eso.
- ¿Qué pasa si la IP elástica no se asocia antes de levantar Kafka? El broker anunciaría una dirección equivocada y el Módulo 2 se conectaría pero no podría publicar. La IP se fija en `.env` antes del primer arranque.
- ¿Qué pasa si el Módulo 2 publica desde una red con IP cambiante? La regla del puerto 9092 se actualiza con la IP del día de la prueba.
- ¿Qué pasa con los datos de ejemplo? En el servidor no se activa el perfil `local`, así que no se cargan ni existe el simulador. Los galpones de prueba se crean desde el frontend.
- ¿Qué pasa con los mensajes de Kafka si se reinicia la instancia? El broker no guarda datos en un volumen (decisión del plan). Los efectos de los eventos ya procesados están en PostgreSQL, que sí persiste.
- ¿Qué pasa si alguien entra a la API sin autenticación? La aplicación no tiene autenticación (P-01, D-05). El riesgo se acepta para una demostración de pocos días y se limita con el grupo de seguridad.

## Requirements

### Functional Requirements

- **FR-001**: El sistema DEBE servir el frontend compilado y la API desde la misma dirección pública, con `/api` reenviado al backend.
- **FR-002**: El servidor web DEBE devolver `index.html` para las rutas del frontend que no correspondan a un archivo, de modo que recargar una ruta interna funcione.
- **FR-003**: El backend DEBE leer la conexión a la base y a Kafka desde variables de entorno, sin cambios en el código ni en `application.properties`.
- **FR-004**: El broker de Kafka DEBE anunciar una dirección interna para el backend y una externa con la IP elástica para el Módulo 2.
- **FR-005**: Los tópicos `avicontrol.mortalidad`, `avicontrol.alerta-sanitaria` y `avicontrol.vaciado-sanitario` DEBEN existir antes de la primera prueba con el Módulo 2.
- **FR-006**: El consumidor del Módulo 1 DEBE usar `auto-offset-reset=earliest` en el servidor (D-01).
- **FR-007**: El servidor NO DEBE activar el perfil `local`.
- **FR-008**: La base de datos DEBE guardar sus datos en un volumen y NO DEBE publicarse fuera de la red de Docker.
- **FR-009**: Los contenedores DEBEN reiniciarse solos si se caen o si la instancia se reinicia.
- **FR-010**: El grupo de seguridad DEBE permitir el puerto 80 a cualquier IP (D-02), y los puertos 22, 9092 y 8080 solo a las IPs autorizadas en `plan-despliegue-aws.md`.
- **FR-011**: Ninguna contraseña real ni clave de AWS DEBE quedar en el repositorio. Los valores reales van en un `.env` que git ignora.
- **FR-012**: El despliegue completo DEBE levantarse con un solo comando de Docker Compose desde la carpeta `despliegue`.

### Key Entities

- **Instancia EC2**: máquina única en us-east-2 con Docker, donde corren los cuatro contenedores.
- **IP elástica**: dirección pública fija de la instancia. Es la que se entrega a los Módulos 2 y 3.
- **Grupo de seguridad**: reglas de entrada por puerto e IP de origen.
- **Contenedores**: nginx (frontend y proxy), backend, postgres y kafka.

## Success Criteria

### Measurable Outcomes

- **SC-001**: El frontend abre desde una red externa en `http://IP_ELASTICA` y permite registrar un galpón y un lote.
- **SC-002**: Un evento de mortalidad válido publicado desde fuera de la instancia se refleja en la población del lote al consultar el detalle del galpón.
- **SC-003**: `GET /api/v1/galpones` responde `200` desde una IP autorizada fuera de la instancia.
- **SC-004**: El puerto 5432 no responde desde fuera de la instancia.
- **SC-005**: Después de reiniciar la instancia, el frontend responde con los mismos datos sin intervención manual.
