import type { EstadoGalpon } from '../api/tipos'

// Datos de la API simulada. Los cuatro primeros galpones son los de los ejemplos del plan de consultar
// galpón y lote; los demás existen para que el listado tenga más de una página y todos los estados.

export interface GalponSimulado {
  id: string
  nombre: string
  capacidad: number
  estado: EstadoGalpon
}

export interface LoteSimulado {
  id: string
  galponId: string
  nombre?: string
  fechaIngreso: string
  poblacionInicial: number
  poblacionActual: number
  costoTotal: number
  /** Fecha (yyyy-MM-dd) en que se desvinculó; ausente = lote activo. */
  desvinculadoEn?: string
}

export const galpones: GalponSimulado[] = [
  { id: 'f47ac10b-58cc-4372-a567-0e02b2c3d479', nombre: 'Galpón Este', capacidad: 800, estado: 'EN_COSECHA' },
  { id: '550e8400-e29b-41d4-a716-446655440000', nombre: 'Galpón Norte', capacidad: 1500, estado: 'PRODUCTIVO' },
  { id: '6ba7b810-9dad-41d1-80b4-00c04fd430c8', nombre: 'Galpón Sur', capacidad: 2000, estado: 'DISPONIBLE' },
  { id: 'c5e8f3a2-6b17-4d09-9f2e-8a1b3c4d5e6f', nombre: 'Galpón Oeste', capacidad: 1800, estado: 'PRODUCTIVO' },
  { id: '0b6a1c2e-1a11-4a2b-9c01-000000000001', nombre: 'Galpón A', capacidad: 1500, estado: 'PRODUCTIVO' },
  { id: '0b6a1c2e-1a11-4a2b-9c01-000000000002', nombre: 'Galpón B', capacidad: 2000, estado: 'PRODUCTIVO' },
  { id: '0b6a1c2e-1a11-4a2b-9c01-000000000003', nombre: 'Galpón C', capacidad: 1200, estado: 'DISPONIBLE' },
  { id: '0b6a1c2e-1a11-4a2b-9c01-000000000005', nombre: 'Galpón E', capacidad: 1000, estado: 'MANTENIMIENTO' },
  { id: '0b6a1c2e-1a11-4a2b-9c01-000000000006', nombre: 'Galpón F', capacidad: 1500, estado: 'AISLAMIENTO' },
  { id: '0b6a1c2e-1a11-4a2b-9c01-000000000009', nombre: 'Galpón I', capacidad: 1500, estado: 'VACIADO_SANITARIO' },
  { id: '0b6a1c2e-1a11-4a2b-9c01-000000000010', nombre: 'Galpón J', capacidad: 1200, estado: 'DISPONIBLE' },
  { id: '0b6a1c2e-1a11-4a2b-9c01-000000000011', nombre: 'Galpón K', capacidad: 2000, estado: 'PRODUCTIVO' },
]

export const lotes: LoteSimulado[] = [
  // Ejemplo 10 del plan: Galpón Norte con su lote activo.
  {
    id: '2fc03a21-84c4-4c83-bb15-6607bb75cb90',
    galponId: '550e8400-e29b-41d4-a716-446655440000',
    nombre: 'Lote A1',
    fechaIngreso: '2026-09-15',
    poblacionInicial: 1000,
    poblacionActual: 975,
    costoTotal: 5000000,
  },
  {
    id: '9a1f4c70-2d3b-4a55-8e61-0b7f3c9d2e18',
    galponId: '550e8400-e29b-41d4-a716-446655440000',
    nombre: 'Lote A0',
    fechaIngreso: '2026-07-01',
    poblacionInicial: 1000,
    poblacionActual: 940,
    costoTotal: 5000000,
    desvinculadoEn: '2026-08-21',
  },
  // Ejemplo 12 del plan: lote activo sin nombre ("No disponible").
  {
    id: '7c2e9b14-3f5a-4d81-a6c0-2b9e8d7f1a35',
    galponId: 'c5e8f3a2-6b17-4d09-9f2e-8a1b3c4d5e6f',
    fechaIngreso: '2026-10-20',
    poblacionInicial: 1800,
    poblacionActual: 1780,
    costoTotal: 8100000,
  },
  {
    id: '4d8b1e6a-9c27-4f03-b5e2-7a1c0d9e3f64',
    galponId: 'f47ac10b-58cc-4372-a567-0e02b2c3d479',
    nombre: 'Lote E1',
    fechaIngreso: '2026-09-01',
    poblacionInicial: 800,
    poblacionActual: 770,
    costoTotal: 3900000,
  },
  {
    id: '1e5f7a3b-6c84-4d29-8f10-3b2a9c7d5e41',
    galponId: '0b6a1c2e-1a11-4a2b-9c01-000000000001',
    nombre: 'Lote A1-24',
    fechaIngreso: '2026-10-24',
    poblacionInicial: 1200,
    poblacionActual: 1200,
    costoTotal: 6000000,
  },
  {
    id: '3a9d7e5c-2b1f-4e80-a7d6-5c4b3a2e1f09',
    galponId: '0b6a1c2e-1a11-4a2b-9c01-000000000002',
    nombre: 'Lote B1',
    fechaIngreso: '2026-10-10',
    poblacionInicial: 2000,
    poblacionActual: 1980,
    costoTotal: 9400000,
  },
  {
    id: '6b5a4c3d-8e7f-4a19-b2c3-9d8e7f6a5b42',
    galponId: '0b6a1c2e-1a11-4a2b-9c01-000000000011',
    nombre: 'Lote K1',
    fechaIngreso: '2026-10-28',
    poblacionInicial: 1900,
    poblacionActual: 1900,
    costoTotal: 8900000,
  },
  {
    id: '8f3c2d1e-5b4a-4c69-9e87-6d5c4b3a2f10',
    galponId: '0b6a1c2e-1a11-4a2b-9c01-000000000006',
    nombre: 'Lote F1',
    fechaIngreso: '2026-10-01',
    poblacionInicial: 1400,
    poblacionActual: 1310,
    costoTotal: 7000000,
  },
]
