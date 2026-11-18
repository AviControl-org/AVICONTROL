# Implementation Plan: Despliegue en AWS

**Fecha**: 2026-11-18
**Spec**: [despliegue-spec.md](despliegue-spec.md)
**Plan general del despliegue**: [plan-despliegue-aws.md](../plan-general/plan-despliegue-aws.md) (plataforma, red, decisiones D-01 a D-05 y acuerdos con los Módulos 2 y 3)
**Arquitectura y tecnologías**: [arquitectura.md](../arquitectura/arquitectura.md)

## Summary

El sistema completo del Módulo 1 corre en una instancia EC2 con Docker Compose: nginx sirve el frontend y reenvía `/api` al backend, el backend usa PostgreSQL y consume de Kafka, y Kafka recibe los eventos del Módulo 2 desde afuera.

**Enfoque técnico:**

1. El backend se empaqueta con un Dockerfile de dos etapas: Maven compila el jar y una imagen JRE 21 lo ejecuta.
2. El frontend se compila con Node 24 y el resultado (`dist`) se copia a una imagen de nginx, junto con su configuración de proxy.
3. Un compose de producción en la carpeta `despliegue` levanta los cuatro contenedores en una red interna. La configuración viene de un `.env` que no se versiona.
4. Kafka anuncia dos listeners: `INTERNO` para el backend dentro de Docker y `EXTERNO` con la IP elástica para el Módulo 2.
5. La instalación en AWS (grupo de seguridad, instancia, IP elástica, Docker, tópicos) se hace a mano siguiendo la sección *Pasos en AWS*.

No se cambia código Java ni TypeScript, ni `application.properties`.

## Contexto específico

- **Lenguajes y versiones**: Java 21 y Spring Boot 4.1.1 (backend), Node 24 y Vite (frontend), las mismas del CI.
- **Imágenes**: `maven:3.9-eclipse-temurin-21` y `eclipse-temurin:21-jre` (backend), `node:24-alpine` y `nginx:1.27-alpine` (frontend), `postgres:16` y `apache/kafka:4.0.0` (las mismas de desarrollo).
- **Sistema de la instancia**: Ubuntu Server 24.04 LTS, porque el script oficial de Docker instala el motor y el plugin de Compose en un paso.
- **Dónde se construyen las imágenes**: en la propia instancia, con `docker compose up -d --build`. No se usa registro de imágenes.
- **Base de datos**: Flyway aplica `db/migration` al arrancar. Sin perfil `local` no se aplica `db/local`, así que la base empieza vacía.
- **Frontend en producción**: `npm run build` usa el modo `production`, donde `VITE_API_SIMULADA` no está definida. `main.tsx` solo activa MSW cuando vale `true`, así que el build llama a la API real en la ruta relativa `/api/v1`.

## Estructura del proyecto

```text
AVICONTROL/
├── avicontrol/
│   ├── Dockerfile                    # TD01
│   └── .dockerignore                 # TD01
├── frontend/
│   ├── Dockerfile                    # TD02
│   ├── .dockerignore                 # TD02
│   └── nginx.conf                    # TD03
└── despliegue/
    ├── docker-compose.yml            # TD04
    ├── .env.example                  # TD05
    └── .gitignore                    # TD05 (ignora .env)
```

El `docker-compose.yml` de `avicontrol/` sigue siendo el de desarrollo y no se toca.

## Decisiones de diseño

**Compilar con Maven de la imagen y no con `mvnw`.** En Windows git deja los archivos con fin de línea CRLF, y `mvnw` con CRLF falla dentro de un contenedor Linux. La imagen de Maven evita depender de ese archivo.

**Sin pruebas en el build de la imagen.** Las pruebas ya corren en el CI de cada PR y algunas usan Testcontainers, que no funciona dentro de un build de Docker. El Dockerfile usa `-DskipTests`.

**Compose en una carpeta aparte.** El compose de producción construye desde `avicontrol/` y desde `frontend/`, así que vive en `despliegue/`, al mismo nivel. Así el compose de desarrollo queda igual y no se confunden.

**Dos listeners en Kafka.** Un cliente de Kafka primero pide al broker su dirección anunciada y luego se conecta a ella. Por eso el backend necesita `kafka:29092` (nombre interno de Docker) y el Módulo 2 necesita `IP_ELASTICA:9092`. Solo el 9092 se publica fuera de la instancia. Esto cambia el valor de `SPRING_KAFKA_BOOTSTRAP_SERVERS` a `kafka:29092`, que se corrige en `plan-despliegue-aws.md`.

**Kafka sin volumen.** La imagen de Kafka corre con un usuario sin privilegios y un volumen nuevo puede quedar sin permisos de escritura. Como los efectos de los eventos se guardan en PostgreSQL y la idempotencia va por `alertaId`, perder los mensajes del broker en un reinicio no afecta los datos. Después de un reinicio de Kafka los tópicos se vuelven a crear con el paso de tópicos.

**Puerto 8080 publicado.** El compose publica el 8080 para que los Módulos 2 y 3 puedan llamar al backend directo si así se decide en D-03. Mientras D-03 no se cierre, el grupo de seguridad no abre el 8080 a nadie y todo entra por el 80.

**Proxy sin CORS.** El frontend y la API salen por el mismo origen gracias a nginx, igual que con el proxy de Vite. CORS solo se agrega si D-04 lo exige.

**Reinicio automático.** Todos los servicios usan `restart: unless-stopped`, y Docker arranca solo con la instancia en Ubuntu. Con eso un reinicio de la máquina deja todo arriba (FR-009).

## Código de implementación

### TD01 `avicontrol/Dockerfile`

```dockerfile
# Etapa 1: compila el jar con Maven y JDK 21
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

# Etapa 2: ejecuta el jar con solo el JRE
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

`avicontrol/.dockerignore`:

```text
target
.idea
.mvn
mvnw
mvnw.cmd
docs
*.iml
```

### TD02 `frontend/Dockerfile`

```dockerfile
# Etapa 1: compila el frontend
FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# Etapa 2: nginx sirve dist y reenvía /api al backend
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
```

`frontend/.dockerignore`:

```text
node_modules
dist
.env.local
```

### TD03 `frontend/nginx.conf`

```nginx
server {
    listen 80;
    server_name _;

    root /usr/share/nginx/html;
    index index.html;

    # La API va al contenedor del backend, con la misma ruta /api/...
    location /api/ {
        proxy_pass http://backend:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # Rutas de React Router: si no es un archivo, se sirve index.html
    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

### TD04 `despliegue/docker-compose.yml`

```yaml
services:
  postgres:
    image: postgres:16
    container_name: avicontrol-postgres
    restart: unless-stopped
    environment:
      POSTGRES_DB: avicontrol
      POSTGRES_USER: avicontrol
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    volumes:
      - avicontrol-postgres-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U avicontrol -d avicontrol"]
      interval: 10s
      timeout: 5s
      retries: 5

  kafka:
    image: apache/kafka:4.0.0
    container_name: avicontrol-kafka
    restart: unless-stopped
    ports:
      - "9092:9092"
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_LISTENERS: INTERNO://:29092,EXTERNO://:9092,CONTROLLER://:9093
      KAFKA_ADVERTISED_LISTENERS: INTERNO://kafka:29092,EXTERNO://${IP_PUBLICA}:9092
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: INTERNO:PLAINTEXT,EXTERNO:PLAINTEXT,CONTROLLER:PLAINTEXT
      KAFKA_INTER_BROKER_LISTENER_NAME: INTERNO
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:9093
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
      KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS: 0

  backend:
    build: ../avicontrol
    container_name: avicontrol-backend
    restart: unless-stopped
    depends_on:
      postgres:
        condition: service_healthy
      kafka:
        condition: service_started
    ports:
      - "8080:8080"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/avicontrol
      SPRING_DATASOURCE_USERNAME: avicontrol
      SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD}
      SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:29092
      SPRING_KAFKA_CONSUMER_AUTO_OFFSET_RESET: earliest
      TZ: America/Bogota

  nginx:
    build: ../frontend
    container_name: avicontrol-nginx
    restart: unless-stopped
    depends_on:
      - backend
    ports:
      - "80:80"

volumes:
  avicontrol-postgres-data:
```

### TD05 `despliegue/.env.example` y `despliegue/.gitignore`

`.env.example` (se copia a `.env` en la instancia y se llena con valores reales):

```text
# IP elástica de la instancia. Kafka la anuncia al Módulo 2.
IP_PUBLICA=

# Contraseña de PostgreSQL en el servidor. No usar la de desarrollo.
POSTGRES_PASSWORD=
```

`.gitignore`:

```text
.env
```

## Pasos en AWS

Se hacen en la consola con la sesión de cada integrante. Nadie comparte claves de acceso.

1. Región us-east-2 (Ohio) seleccionada arriba a la derecha.
2. Grupo de seguridad `avicontrol-sg` con estas reglas de entrada:
   - 22/TCP desde la IP del Módulo 1 (opción "Mi IP").
   - 80/TCP desde `0.0.0.0/0` (D-02).
   - 9092/TCP desde la IP del Módulo 2, cuando la entregue.
   - 8080/TCP: sin regla hasta que se cierre D-03.
3. Par de claves para SSH, descargado como `.pem` y guardado fuera del repositorio.
4. Instancia EC2: Ubuntu Server 24.04 LTS, tipo `m7i-flex.large` (verificar que la consola lo marque como admitido por el plan gratuito), disco gp3 de 30 GB, grupo `avicontrol-sg`.
5. IP elástica: asignarla y asociarla a la instancia. Esa es la `IP_ELASTICA` de todos los documentos.
6. Conexión por SSH: `ssh -i avicontrol.pem ubuntu@IP_ELASTICA`.
7. Docker:
   ```bash
   curl -fsSL https://get.docker.com | sudo sh
   sudo usermod -aG docker ubuntu
   ```
   Cerrar la sesión SSH y volver a entrar para que el grupo `docker` aplique.
8. Repositorio: `git clone https://github.com/AviControl-org/AVICONTROL.git` y quedarse en `develop`, que ya incluye estos archivos tras el merge de `feature/despliegue-aws`. El repositorio es público, así que no se necesita token.
9. Configuración: `cd AVICONTROL/despliegue`, `cp .env.example .env` y llenar `IP_PUBLICA` y `POSTGRES_PASSWORD`.
10. Arranque: `docker compose up -d --build` y revisar con `docker compose ps` y `docker compose logs -f backend`.
11. Tópicos (también después de cada reinicio de Kafka):
    ```bash
    for t in avicontrol.mortalidad avicontrol.alerta-sanitaria avicontrol.vaciado-sanitario; do
      docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:29092 \
        --create --if-not-exists --topic "$t" --partitions 1 --replication-factor 1
    done
    ```
12. Datos de prueba: desde el frontend, crear un galpón y un lote y anotar sus ids para el Módulo 2.

## Pruebas manuales

| # | Prueba | Cómo | Resultado esperado | Cubre |
|---|---|---|---|---|
| 1 | Frontend | Abrir `http://IP_ELASTICA` desde otra red | Se ve el listado de galpones | SC-001 |
| 2 | Ruta interna | Recargar la página de detalle de un galpón | Carga la misma página | FR-002 |
| 3 | API | `curl http://IP_ELASTICA/api/v1/galpones` | `200` con la página de galpones | SC-003 |
| 4 | Evento interno | Productor de consola dentro de la instancia (abajo) | La población del lote baja | US2 |
| 5 | Evento externo | El Módulo 2 publica en `IP_ELASTICA:9092` | La población del lote baja | SC-002 |
| 6 | Base cerrada | `Test-NetConnection IP_ELASTICA -Port 5432` desde afuera | Falla la conexión | SC-004 |
| 7 | Reinicio | Reiniciar la instancia desde la consola | El frontend vuelve con los mismos datos | SC-005 |

Productor de consola para la prueba 4 (clave y valor separados por `|`, el JSON en una sola línea):

```bash
docker compose exec -it kafka /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server kafka:29092 --topic avicontrol.mortalidad \
  --property parse.key=true --property key.separator='|'
```

Luego se pega una línea `GALPON_ID|{...sobre del evento...}` con el ejemplo de mortalidad de `acuerdo-contratos-entre-modulos.md`, usando ids del servidor y la fecha del día.

## Fuera de alcance

- HTTPS y dominio propio.
- Autenticación (P-01, D-05).
- Alta disponibilidad, copias de seguridad y monitoreo.
- Despliegue automático desde GitHub Actions.
- CORS, salvo que D-04 lo exija.

## Tareas

| ID | Tarea | Depende de |
|---|---|---|
| TD01 | Crear `avicontrol/Dockerfile` y `avicontrol/.dockerignore` | |
| TD02 | Crear `frontend/Dockerfile` y `frontend/.dockerignore` | |
| TD03 | Crear `frontend/nginx.conf` | |
| TD04 | Crear `despliegue/docker-compose.yml` | TD01, TD02, TD03 |
| TD05 | Crear `despliegue/.env.example` y `despliegue/.gitignore` | |
| TD06 | Corregir en `plan-despliegue-aws.md` el bootstrap del backend a `kafka:29092` y enlazar esta spec y este plan | |
| TD07 | Crear en AWS el grupo de seguridad, el par de claves, la instancia y la IP elástica (pasos 1 a 5) | |
| TD08 | Instalar Docker, clonar, configurar `.env` y levantar el compose (pasos 6 a 10) | TD04, TD05, TD07 |
| TD09 | Crear los tópicos y los datos de prueba (pasos 11 y 12) | TD08 |
| TD10 | Pruebas manuales 1 a 4, 6 y 7 | TD09 |
| TD11 | Abrir 9092 (y 8080 si D-03 lo decide) a las IPs de los otros módulos y hacer la prueba 5 con el Módulo 2 | TD10, acuerdos de la sección 6 del plan general |

## Dependencias

- Rama de trabajo: `feature/despliegue-aws`, salida de `develop`. El despliegue en la instancia sale de `develop`, después del merge.
- Los archivos TD01 a TD05 se crean solo después de aprobar esta spec y este plan.
- TD07 en adelante necesitan la cuenta de AWS con sesión iniciada por un integrante del Módulo 1.
- TD11 necesita las IPs y la confirmación de los Módulos 2 y 3.

## Pendientes de clarificación

| ID | Tema | Estado |
|---|---|---|
| D-03 | Entrada de los Módulos 2 y 3 por el puerto 80 o por el 8080 | Pendiente de los tres grupos |
| D-04 | CORS si el Módulo 3 llama desde un navegador | Pendiente del Módulo 3 |
| PD-01 | Cómo se clona el repositorio en la instancia | Decidido: el repositorio es público, `git clone` sin credenciales |
| PD-02 | Qué rama se despliega | Decidido: `develop`, después del merge de `feature/despliegue-aws` |
