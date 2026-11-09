import { ErrorApi } from '../api/cliente'

// Mismas reglas que el backend (NombreNormalizado y los DTO de request) para avisar antes de enviar.
// El servidor sigue siendo quien decide: sus fieldErrors reemplazan a estos mensajes.

const NOMBRE_PERMITIDO = /^[\p{L}\p{N} _-]+$/u
const ENTERO_POSITIVO = /^\d+$/

/** Nombre válido: 1 a 100 caracteres tras recortar; letras, números, espacios, guiones y guiones bajos. */
export function nombreValido(nombre: string): boolean {
  const recortado = nombre.trim()
  return recortado.length >= 1 && recortado.length <= 100 && NOMBRE_PERMITIDO.test(recortado)
}

/** Devuelve el entero si `texto` es un entero mayor que cero sin decimales ni letras; si no, `null`. */
export function enteroPositivo(texto: string): number | null {
  const limpio = texto.trim()
  if (!ENTERO_POSITIVO.test(limpio)) return null
  const valor = Number(limpio)
  return valor > 0 && Number.isSafeInteger(valor) ? valor : null
}

/** Fecha de hoy (`yyyy-MM-dd`) en la zona de negocio America/Bogota, para la regla de "fecha no futura". */
export function hoyEnBogota(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Bogota' }).format(new Date())
}

/** Errores por campo que devolvió el servidor, indexados por nombre de campo. */
export function erroresDelServidor(error: unknown): Record<string, { code: string; message: string }> {
  if (!(error instanceof ErrorApi)) return {}
  return Object.fromEntries((error.problema.fieldErrors ?? []).map((campo) => [campo.field, campo]))
}
