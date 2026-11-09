-- V1000: datos de ejemplo para la demostracion (solo con el perfil local).
-- Un galpon en cada estado y un lote activo en el galpon PRODUCTIVO.
-- Los ids de "Galpon Norte" y "Lote A1" coinciden con los de los fixtures de src/test/resources/contratos/.

INSERT INTO galpon (id, nombre, capacidad, estado) VALUES
    ('550e8400-e29b-41d4-a716-446655440000', 'Galpón Norte',      5000, 'PRODUCTIVO'),
    ('0b6f3c1e-8d24-4a5b-9c71-1e2d3f4a5b01', 'Galpón Sur',        3000, 'DISPONIBLE'),
    ('0b6f3c1e-8d24-4a5b-9c71-1e2d3f4a5b02', 'Galpón Este',       4000, 'EN_COSECHA'),
    ('0b6f3c1e-8d24-4a5b-9c71-1e2d3f4a5b03', 'Galpón Oeste',      2500, 'MANTENIMIENTO'),
    ('0b6f3c1e-8d24-4a5b-9c71-1e2d3f4a5b04', 'Galpón Aislamiento', 2000, 'AISLAMIENTO'),
    ('0b6f3c1e-8d24-4a5b-9c71-1e2d3f4a5b05', 'Galpón Lavado',     3500, 'VACIADO_SANITARIO');

INSERT INTO lote (id, galpon_id, nombre, fecha_ingreso, poblacion_inicial, poblacion_actual, costo_total) VALUES
    ('2fc03a21-84c4-4c83-bb15-6607bb75cb90', '550e8400-e29b-41d4-a716-446655440000', 'Lote A1',
     CURRENT_DATE - 20, 1000, 1000, 5000000);
