-- V1: esquema inicial del Modulo 1 (Gestion de Galpones).
-- Fuente: plan-general/plan-modulo-1.md, seccion "Modelo de datos" (T006).

CREATE TABLE galpon (
    id        uuid         PRIMARY KEY,
    nombre    varchar(100) NOT NULL,
    capacidad bigint       NOT NULL,
    estado    varchar(20)  NOT NULL,
    version   bigint       NOT NULL DEFAULT 0,
    CONSTRAINT ck_galpon_capacidad CHECK (capacidad > 0),
    CONSTRAINT ck_galpon_estado CHECK (estado IN
        ('DISPONIBLE', 'VACIADO_SANITARIO', 'PRODUCTIVO', 'EN_COSECHA', 'MANTENIMIENTO', 'AISLAMIENTO'))
);

CREATE UNIQUE INDEX ux_galpon_nombre ON galpon (lower(nombre));

CREATE TABLE lote (
    id                uuid         PRIMARY KEY,
    galpon_id         uuid         NOT NULL REFERENCES galpon (id),
    nombre            varchar(100) NOT NULL,
    fecha_ingreso     date         NOT NULL,
    poblacion_inicial bigint       NOT NULL,
    poblacion_actual  bigint       NOT NULL,
    costo_total       bigint       NOT NULL,
    desvinculado_en   timestamptz,
    version           bigint       NOT NULL DEFAULT 0,
    CONSTRAINT ck_lote_valores CHECK (
        poblacion_inicial > 0
        AND costo_total > 0
        AND poblacion_actual BETWEEN 0 AND poblacion_inicial)
);

CREATE UNIQUE INDEX ux_lote_nombre ON lote (lower(nombre));
-- Un solo lote activo por galpon.
CREATE UNIQUE INDEX ux_lote_activo_por_galpon ON lote (galpon_id) WHERE desvinculado_en IS NULL;
-- Historial de lotes del galpon.
CREATE INDEX ix_lote_galpon_fecha_ingreso ON lote (galpon_id, fecha_ingreso DESC);

CREATE TABLE historial_estado (
    id              uuid        PRIMARY KEY,
    galpon_id       uuid        NOT NULL REFERENCES galpon (id),
    estado_anterior varchar(20) NOT NULL,
    estado_nuevo    varchar(20) NOT NULL,
    fecha_hora      timestamptz NOT NULL,
    origen          varchar(40) NOT NULL
);

CREATE INDEX ix_historial_estado_galpon_fecha ON historial_estado (galpon_id, fecha_hora DESC);

CREATE TABLE historial_cambio_galpon (
    id             uuid         PRIMARY KEY,
    galpon_id      uuid         NOT NULL REFERENCES galpon (id),
    campo          varchar(20)  NOT NULL,
    valor_anterior varchar(100) NOT NULL,
    valor_nuevo    varchar(100) NOT NULL,
    fecha_hora     timestamptz  NOT NULL
);

CREATE TABLE alerta_mantenimiento (
    id          uuid         PRIMARY KEY,
    galpon_id   uuid         NOT NULL REFERENCES galpon (id),
    descripcion varchar(500) NOT NULL,
    fecha_hora  timestamptz  NOT NULL,
    tecnico_id  varchar(100) NOT NULL,
    estado      varchar(20)  NOT NULL,
    severidad   varchar(100),
    tipo        varchar(100),
    CONSTRAINT ck_alerta_mantenimiento_descripcion CHECK (char_length(descripcion) <= 500),
    CONSTRAINT ck_alerta_mantenimiento_estado CHECK (estado IN ('PENDIENTE', 'ATENDIDA', 'ELIMINADA'))
);

-- Una sola alerta pendiente por galpon, tambien en concurrencia.
CREATE UNIQUE INDEX ux_alerta_mantenimiento_pendiente_por_galpon
    ON alerta_mantenimiento (galpon_id) WHERE estado = 'PENDIENTE';

CREATE TABLE alerta_vaciado_sanitario (
    alerta_id            uuid        PRIMARY KEY,
    galpon_id            uuid        NOT NULL REFERENCES galpon (id),
    lote_id              uuid        NOT NULL REFERENCES lote (id),
    fecha_hora_evento    timestamptz NOT NULL,
    fecha_hora_recepcion timestamptz NOT NULL,
    resultado            varchar(20) NOT NULL
);

-- Control tecnico de idempotencia de mortalidad; sin FK a proposito.
CREATE TABLE registro_procesamiento (
    alerta_id         uuid        PRIMARY KEY,
    galpon_id         uuid        NOT NULL,
    lote_id           uuid        NOT NULL,
    cantidad_muertos  bigint      NOT NULL,
    fecha_hora_evento timestamptz NOT NULL,
    procesado_en      timestamptz NOT NULL
);
