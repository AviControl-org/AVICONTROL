import { http, HttpResponse } from 'msw'
import type { GalponDetalleResponse, IncidenciaResponse, LoteDetalleResponse } from '../api/galpones'
import { ESTADOS_GALPON, type ErrorDeCampo, type EstadoGalpon, type ProblemDetail } from '../api/tipos'
import { galpones, lotes, type LoteSimulado } from './datos'

// Comportamiento y respuestas de docs/spec-consultar-galpón-lote/plan-consultar-galpon-lote.md.

const TAMANIO = 10
const SIN_CACHE = { 'Cache-Control': 'no-store' }
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
const CAMPOS_ORDEN = ['nombre', 'capacidad', 'estado'] as const

/** Permite ver en la demo el estado "Error durante la consulta" (escenario 12 del spec). */
export const CLAVE_SIMULAR_ERROR = 'avicontrol.simular-error-consulta'

export function simularError(): boolean {
  try {
    return localStorage.getItem(CLAVE_SIMULAR_ERROR) === 'true'
  } catch {
    return false
  }
}

export function problema(status: number, codigo: string, titulo: string, detalle: string, instancia: string, errores?: ErrorDeCampo[]) {
  const cuerpo: ProblemDetail = {
    type: `https://avicontrol.unimag.edu.co/errors/${codigo.toLowerCase().replaceAll('_', '-')}`,
    title: titulo,
    status,
    detail: detalle,
    instance: instancia,
    code: codigo,
    ...(errores ? { fieldErrors: errores } : {}),
  }
  return HttpResponse.json(cuerpo, { status, headers: { 'Content-Type': 'application/problem+json' } })
}

const parametroInvalido = (instancia: string, error: ErrorDeCampo) =>
  problema(400, 'PARAMETRO_INVALIDO', 'Parámetro inválido', 'Revisa los parámetros de la solicitud.', instancia, [error])

const errorConsulta = (instancia: string) =>
  problema(500, 'ERROR_CONSULTA', 'Error en la consulta', 'No fue posible obtener los datos. Puedes reintentar la consulta.', instancia)

const galponNoEncontrado = (instancia: string) =>
  problema(404, 'GALPON_NO_ENCONTRADO', 'Galpón no encontrado', 'El galpón consultado no existe.', instancia)

export function hoyBogota(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Bogota' }).format(new Date())
}

/** Días entre el ingreso y `hasta`, contando el día de ingreso como día 1 (P-12). */
function edadDias(fechaIngreso: string, hasta: string): number {
  const dias = (Date.parse(hasta) - Date.parse(fechaIngreso)) / 86_400_000
  return Math.round(dias) + 1
}

function aRespuesta(lote: LoteSimulado, incidencias: IncidenciaResponse[]): LoteDetalleResponse {
  if (!lote.nombre) {
    incidencias.push({ codigo: 'CAMPO_NO_DISPONIBLE', mensaje: 'El nombre del lote no está disponible.' })
  }
  return {
    id: lote.id,
    ...(lote.nombre ? { nombre: lote.nombre } : {}),
    poblacionInicial: lote.poblacionInicial,
    poblacionActual: lote.poblacionActual,
    fechaIngreso: lote.fechaIngreso,
    edadDias: edadDias(lote.fechaIngreso, lote.desvinculadoEn?.slice(0, 10) ?? hoyBogota()),
    costoTotal: lote.costoTotal,
  }
}

// Mensajes de fieldErrors del backend (CatalogoErrores).
export const MENSAJE_CAMPO = {
  OBLIGATORIO: 'Es obligatorio',
  LONGITUD_INVALIDA: 'Longitud fuera del rango permitido',
  FORMATO_INVALIDO: 'Formato no válido',
  ENTERO_POSITIVO: 'Debe ser un entero mayor que cero',
} as const

export function errorDeNombre(valor: unknown): ErrorDeCampo | null {
  const nombre = typeof valor === 'string' ? valor.trim() : ''
  if (nombre === '') return { field: 'nombre', code: 'OBLIGATORIO', message: MENSAJE_CAMPO.OBLIGATORIO }
  if (nombre.length > 100) return { field: 'nombre', code: 'LONGITUD_INVALIDA', message: MENSAJE_CAMPO.LONGITUD_INVALIDA }
  if (!/^[\p{L}\p{N} _-]+$/u.test(nombre)) {
    return { field: 'nombre', code: 'FORMATO_INVALIDO', message: MENSAJE_CAMPO.FORMATO_INVALIDO }
  }
  return null
}

function errorDeCapacidad(valor: unknown): ErrorDeCampo | null {
  if (valor === undefined || valor === null) {
    return { field: 'capacidad', code: 'OBLIGATORIO', message: MENSAJE_CAMPO.OBLIGATORIO }
  }
  if (typeof valor !== 'number' || !Number.isInteger(valor) || valor <= 0) {
    return { field: 'capacidad', code: 'ENTERO_POSITIVO', message: MENSAJE_CAMPO.ENTERO_POSITIVO }
  }
  return null
}

export const manejadoresGalpones = [
  // docs/spec-registrarGalpon/plan-registrar-galpon.md
  http.post('/api/v1/galpones', async ({ request }) => {
    const instancia = '/api/v1/galpones'
    let cuerpo: Record<string, unknown>
    try {
      cuerpo = (await request.json()) as Record<string, unknown>
    } catch {
      return problema(400, 'CUERPO_INVALIDO', 'Cuerpo inválido', 'El cuerpo de la solicitud no es un JSON válido.', instancia)
    }

    const errores = [errorDeNombre(cuerpo.nombre), errorDeCapacidad(cuerpo.capacidad)].filter(
      (error): error is ErrorDeCampo => error !== null,
    )
    if (errores.length > 1) {
      return problema(400, 'VALIDACION_FALLIDA', 'Validación fallida', 'Hay campos con datos inválidos. Revisa fieldErrors.', instancia, errores)
    }
    if (errores[0]?.field === 'nombre') {
      return problema(
        400,
        'NOMBRE_INVALIDO',
        'Nombre inválido',
        'El nombre es obligatorio, tiene máximo 100 caracteres y solo admite letras, números, espacios, guiones y guiones bajos.',
        instancia,
        errores,
      )
    }
    if (errores[0]?.field === 'capacidad') {
      return problema(400, 'CAPACIDAD_INVALIDA', 'Capacidad inválida', 'La capacidad debe ser un número entero mayor que cero.', instancia, errores)
    }

    const nombre = String(cuerpo.nombre).trim()
    if (galpones.some((g) => g.nombre.toLowerCase() === nombre.toLowerCase())) {
      return problema(409, 'NOMBRE_DUPLICADO', 'Nombre duplicado', 'Ya existe un galpón con ese nombre.', instancia, [
        { field: 'nombre', code: 'DUPLICADO', message: 'El nombre ya está en uso' },
      ])
    }
    if (simularError()) {
      return problema(500, 'ERROR_INTERNO', 'Error interno', 'No fue posible completar la operación. Intenta de nuevo.', instancia)
    }

    const galpon = { id: crypto.randomUUID(), nombre, capacidad: cuerpo.capacidad as number, estado: 'DISPONIBLE' as const }
    galpones.push(galpon)
    return HttpResponse.json(galpon, { status: 201, headers: { Location: `/api/v1/galpones/${galpon.id}` } })
  }),


  http.get('/api/v1/galpones', ({ request }) => {
    const instancia = '/api/v1/galpones'
    const query = new URL(request.url).searchParams

    const [campo, direccion, ...resto] = (query.get('orden') ?? 'nombre,asc').split(',')
    if (resto.length > 0 || !CAMPOS_ORDEN.includes(campo as never) || !['asc', 'desc'].includes(direccion)) {
      return parametroInvalido(instancia, {
        field: 'orden',
        code: 'FORMATO_INVALIDO',
        message: 'Usa campo,direccion: nombre, capacidad o estado; asc o desc',
      })
    }
    const estado = query.get('estado')
    if (estado !== null && !ESTADOS_GALPON.includes(estado as EstadoGalpon)) {
      return parametroInvalido(instancia, {
        field: 'estado',
        code: 'VALOR_NO_PERMITIDO',
        message: `Debe ser uno de: ${ESTADOS_GALPON.join(', ')}`,
      })
    }
    const textoPagina = query.get('page') ?? '1'
    if (!/^-?\d+$/.test(textoPagina)) {
      return parametroInvalido(instancia, { field: 'page', code: 'FORMATO_INVALIDO', message: 'Formato no válido' })
    }
    const pagina = Number(textoPagina)
    if (pagina < 1) {
      return parametroInvalido(instancia, { field: 'page', code: 'MINIMO_1', message: 'Debe ser mayor o igual a 1' })
    }
    if (simularError()) {
      return errorConsulta(instancia)
    }

    const porEstado = galpones.filter((g) => estado === null || g.estado === estado)
    const nombre = (query.get('nombre') ?? '').trim().toLowerCase()
    let resultado = nombre ? porEstado.filter((g) => g.nombre.toLowerCase().includes(nombre)) : porEstado
    const sinCoincidencias = nombre !== '' && resultado.length === 0
    if (sinCoincidencias) {
      resultado = porEstado
    }

    const signo = direccion === 'desc' ? -1 : 1
    const ordenados = [...resultado].sort((a, b) => {
      const comparacion =
        campo === 'capacidad'
          ? a.capacidad - b.capacidad
          : String(a[campo as 'nombre' | 'estado']).localeCompare(String(b[campo as 'nombre' | 'estado']), 'es', {
              sensitivity: 'base',
            })
      return comparacion !== 0 ? signo * comparacion : a.id.localeCompare(b.id)
    })

    return HttpResponse.json(
      {
        contenido: ordenados.slice((pagina - 1) * TAMANIO, pagina * TAMANIO),
        pagina,
        tamanio: TAMANIO,
        totalElementos: ordenados.length,
        totalPaginas: Math.ceil(ordenados.length / TAMANIO),
        sinCoincidencias,
        ...(sinCoincidencias
          ? { mensaje: 'No se encontraron galpones con ese nombre. Se muestran todos los galpones.' }
          : {}),
      },
      { headers: SIN_CACHE },
    )
  }),

  http.get('/api/v1/galpones/:galponId', ({ params }) => {
    const galponId = String(params.galponId)
    const instancia = `/api/v1/galpones/${galponId}`
    if (!UUID.test(galponId)) {
      return parametroInvalido(instancia, { field: 'galponId', code: 'FORMATO_INVALIDO', message: 'Formato no válido' })
    }
    if (simularError()) {
      return errorConsulta(instancia)
    }
    const galpon = galpones.find((g) => g.id === galponId)
    if (!galpon) {
      return galponNoEncontrado(instancia)
    }

    const incidencias: IncidenciaResponse[] = []
    const activo = lotes.find((l) => l.galponId === galponId && !l.desvinculadoEn)
    const tieneLotes = lotes.some((l) => l.galponId === galponId)
    const respuesta: GalponDetalleResponse = {
      ...galpon,
      ...(activo ? { loteActivo: aRespuesta(activo, incidencias) } : {}),
      ...(tieneLotes ? {} : { mensaje: 'No existen lotes registrados para este galpón' }),
    }
    if (incidencias.length > 0) {
      respuesta.incidencias = incidencias
    }
    return HttpResponse.json(respuesta, { headers: SIN_CACHE })
  }),

  http.get('/api/v1/galpones/:galponId/lotes', ({ params }) => {
    const galponId = String(params.galponId)
    const instancia = `/api/v1/galpones/${galponId}/lotes`
    if (!UUID.test(galponId)) {
      return parametroInvalido(instancia, { field: 'galponId', code: 'FORMATO_INVALIDO', message: 'Formato no válido' })
    }
    if (simularError()) {
      return errorConsulta(instancia)
    }
    if (!galpones.some((g) => g.id === galponId)) {
      return galponNoEncontrado(instancia)
    }

    const incidencias: IncidenciaResponse[] = []
    const delGalpon = lotes
      .filter((l) => l.galponId === galponId)
      .sort((a, b) => b.fechaIngreso.localeCompare(a.fechaIngreso))
    const activo = delGalpon.find((l) => !l.desvinculadoEn)
    return HttpResponse.json(
      {
        galponId,
        ...(activo ? { loteActivo: aRespuesta(activo, incidencias) } : {}),
        lotesAnteriores: delGalpon.filter((l) => l.desvinculadoEn).map((l) => aRespuesta(l, incidencias)),
        ...(incidencias.length > 0 ? { incidencias } : {}),
      },
      { headers: SIN_CACHE },
    )
  }),
]
