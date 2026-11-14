-- V1001: lotes activos que le faltaban a los datos de ejemplo (solo con el perfil local).
--
-- V1000 crea un galpon en cada estado pero un solo lote, en el galpon PRODUCTIVO. Eso deja dos estados
-- en una situacion de negocio imposible y bloquea la demostracion de dos de los tres eventos del Modulo 2:
--
--   * EN_COSECHA sin lote: no hay nada que cosechar, y "Recibir vaciado sanitario" no se puede demostrar
--     porque el evento necesita un lote activo que desvincular.
--   * AISLAMIENTO sin lote: no hay pollos enfermos que aislar, y la alerta sanitaria dirigida a un lote
--     (alerta-sanitaria-por-lote-payload.json) no se puede demostrar sobre este galpon.
--
-- Va como migracion nueva en vez de modificar V1000 para no invalidar su checksum en las bases locales
-- que ya la tienen aplicada.
--
-- Los otros tres estados se quedan sin lote a proposito: DISPONIBLE y MANTENIMIENTO no deben tener uno, y
-- en VACIADO_SANITARIO el lote ya quedo desvinculado por el propio vaciado.

INSERT INTO lote (id, galpon_id, nombre, fecha_ingreso, poblacion_inicial, poblacion_actual, costo_total) VALUES
    -- Galpón Este (EN_COSECHA, capacidad 4000): lote maduro, listo para cosecha, con mortalidad acumulada.
    ('7a1e4b93-2c6d-4f81-9ab5-3e8d7c2f1b64', '0b6f3c1e-8d24-4a5b-9c71-1e2d3f4a5b02', 'Lote E1',
     CURRENT_DATE - 45, 3800, 3651, 19000000),

    -- Galpón Aislamiento (AISLAMIENTO, capacidad 2000): lote en aislamiento sanitario, con mortalidad alta.
    ('c4f8d207-9b53-4e16-8d7a-5f2c1b9e3a08', '0b6f3c1e-8d24-4a5b-9c71-1e2d3f4a5b04', 'Lote X1',
     CURRENT_DATE - 31, 1800, 1612, 9000000);
