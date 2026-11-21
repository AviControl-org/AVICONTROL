import type { ProblemDetail } from './tipos'

export const BASE_API = '/api/v1'

/** El backend respondió con un error en formato RFC 9457. */
export class ErrorApi extends Error {
  readonly problema: ProblemDetail

  constructor(problema: ProblemDetail) {
    super(problema.detail)
    this.name = 'ErrorApi'
    this.problema = problema
  }
}

/** No hubo respuesta del servidor (sin conexión o servidor caído). */
export class ErrorDeConexion extends Error {
  constructor(causa: unknown) {
    super('No se pudo conectar con el servidor.', { cause: causa })
    this.name = 'ErrorDeConexion'
  }
}

/** Único punto del frontend que hace peticiones HTTP a la API. `ruta` es relativa a /api/v1. */
export async function solicitar<T>(ruta: string, opciones: RequestInit = {}): Promise<T> {
  const cabeceras = new Headers(opciones.headers)
  cabeceras.set('Accept', 'application/json, application/problem+json')
  if (opciones.body !== undefined) {
    cabeceras.set('Content-Type', 'application/json')
  }

  let respuesta: Response
  try {
    respuesta = await fetch(`${BASE_API}${ruta}`, { ...opciones, headers: cabeceras })
  } catch (causa) {
    throw new ErrorDeConexion(causa)
  }

  const texto = await respuesta.text()
  if (respuesta.ok) {
    return (texto ? JSON.parse(texto) : undefined) as T
  }
  const esConsulta = (opciones.method ?? 'GET').toUpperCase() === 'GET'
  throw new ErrorApi(leerProblema(respuesta, texto, ruta, esConsulta))
}

function leerProblema(respuesta: Response, texto: string, ruta: string, esConsulta: boolean): ProblemDetail {
  try {
    const cuerpo = JSON.parse(texto) as Partial<ProblemDetail>
    if (typeof cuerpo.code === 'string') {
      return cuerpo as ProblemDetail
    }
  } catch {
    // El cuerpo no es JSON: se arma un problema genérico abajo.
  }
  return {
    type: 'about:blank',
    title: respuesta.statusText || 'Error',
    status: respuesta.status,
    detail: 'Ocurrió un error inesperado. Inténtalo de nuevo.',
    instance: `${BASE_API}${ruta}`,
    code: esConsulta ? 'ERROR_CONSULTA' : 'ERROR_INTERNO',
  }
}
