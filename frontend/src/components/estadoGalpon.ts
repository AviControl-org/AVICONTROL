import type { EstadoGalpon } from '../api/tipos'
import type { NombreIcono } from './Icono'

type Tono = 'ok' | 'warn' | 'crit'

// Textos e iconos de la tabla "Estados del galpón" de los mockups.
// Tono: normal (brand-soft), atención (accent) y crítico (danger), según la marca.
export const ESTADO_GALPON: Record<EstadoGalpon, { texto: string; tono: Tono; icono: NombreIcono }> = {
  DISPONIBLE: { texto: 'Disponible', tono: 'ok', icono: 'disponible' },
  PRODUCTIVO: { texto: 'Productivo', tono: 'ok', icono: 'productivo' },
  EN_COSECHA: { texto: 'En cosecha', tono: 'ok', icono: 'cosecha' },
  VACIADO_SANITARIO: { texto: 'Vaciado sanitario', tono: 'ok', icono: 'vaciado' },
  MANTENIMIENTO: { texto: 'Mantenimiento', tono: 'warn', icono: 'llave' },
  AISLAMIENTO: { texto: 'Aislamiento', tono: 'crit', icono: 'aislamiento' },
}
