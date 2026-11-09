import type { HttpHandler } from 'msw'
import { manejadoresGalpones } from './galpones'
import { manejadoresLotes } from './lotes'

// Respuestas simuladas de /api/v1 mientras el backend no tenga controladores.
// Cada manejador copia la ruta, los campos y los ejemplos JSON del plan de su spec (sección Contrato).
export const manejadores: HttpHandler[] = [...manejadoresGalpones, ...manejadoresLotes]
