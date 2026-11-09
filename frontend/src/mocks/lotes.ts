import { http, HttpResponse } from 'msw'
import type { LoteResponse } from '../api/lotes'
import type { ErrorDeCampo } from '../api/tipos'
import { galpones, lotes, type LoteSimulado } from './datos'
import { errorDeNombre, hoyBogota, MENSAJE_CAMPO, problema, simularError } from './galpones'

// Comportamiento y respuestas de docs/spec-registrarLote/plan-registrar-lote.md.

const TAMANIO = 10
const INSTANCIA = '/api/v1/lotes'
const FECHA = /^\d{4}-\d{2}-\d{2}$/

const TITULO_Y_DETALLE: Record<string, [string, string]> = {
  NOMBRE_INVALIDO: [
    'Nombre inválido',
    'El nombre es obligatorio, tiene máximo 100 caracteres y solo admite letras, números, espacios, guiones y guiones bajos.',
  ],
  FECHA_INGRESO_FUTURA: ['Fecha de ingreso inválida', 'La fecha de ingreso no puede ser posterior a hoy.'],
  POBLACION_INICIAL_INVALIDA: [
    'Población inicial inválida',
    'La población inicial debe ser un número entero mayor que cero.',
  ],
  COSTO_TOTAL_INVALIDO: [
    'Costo total inválido',
    'El costo total debe ser un número entero mayor que cero, en pesos colombianos.',
  ],
  GALPON_REQUERIDO: ['Galpón requerido', 'Debes seleccionar un galpón.'],
  GALPON_NO_ENCONTRADO: ['Galpón no encontrado', 'El galpón seleccionado no existe.'],
  NOMBRE_DUPLICADO: ['Nombre duplicado', 'Ya existe un lote con ese nombre.'],
  GALPON_NO_DISPONIBLE: ['Galpón no disponible', 'El galpón seleccionado no está disponible para recibir un lote.'],
  FECHA_INGRESO_INVALIDA: [
    'Fecha de ingreso inválida',
    'La fecha de ingreso es obligatoria y debe tener el formato yyyy-MM-dd.',
  ],
  VALIDACION_FALLIDA: ['Validación fallida', 'Hay campos con datos inválidos. Revisa fieldErrors.'],
  PARAMETRO_INVALIDO: ['Parámetro inválido', 'Revisa los parámetros de la solicitud.'],
}

/** Código del error cuando el único campo inválido es `campo`. */
const CODIGO_POR_CAMPO: Record<string, string> = {
  galponId: 'GALPON_REQUERIDO',
  nombre: 'NOMBRE_INVALIDO',
  fechaIngreso: 'FECHA_INGRESO_INVALIDA',
  poblacionInicial: 'POBLACION_INICIAL_INVALIDA',
  costoTotal: 'COSTO_TOTAL_INVALIDO',
}

function error(status: number, codigo: string, instancia = INSTANCIA, errores?: ErrorDeCampo[]) {
  const [titulo, detalle] = TITULO_Y_DETALLE[codigo]
  return problema(status, codigo, titulo, detalle, instancia, errores)
}

function enteroPositivo(campo: string, valor: unknown): ErrorDeCampo | null {
  if (valor === undefined || valor === null) return { field: campo, code: 'OBLIGATORIO', message: MENSAJE_CAMPO.OBLIGATORIO }
  if (typeof valor !== 'number' || !Number.isInteger(valor) || valor <= 0) {
    return { field: campo, code: 'ENTERO_POSITIVO', message: MENSAJE_CAMPO.ENTERO_POSITIVO }
  }
  return null
}

function aRespuesta(lote: LoteSimulado): LoteResponse {
  return {
    id: lote.id,
    nombre: lote.nombre ?? '',
    galponId: lote.galponId,
    fechaIngreso: lote.fechaIngreso,
    poblacionInicial: lote.poblacionInicial,
    poblacionActual: lote.poblacionActual,
    costoTotal: lote.costoTotal,
    ...(lote.desvinculadoEn ? { desvinculadoEn: lote.desvinculadoEn } : {}),
  }
}

export const manejadoresLotes = [
  http.post(INSTANCIA, async ({ request }) => {
    let cuerpo: Record<string, unknown>
    try {
      cuerpo = (await request.json()) as Record<string, unknown>
    } catch {
      return problema(400, 'CUERPO_INVALIDO', 'Cuerpo inválido', 'El cuerpo de la solicitud no es un JSON válido.', INSTANCIA)
    }

    // 1. Formato y enteros del body.
    const errores = [
      typeof cuerpo.galponId === 'string' && cuerpo.galponId !== ''
        ? null
        : { field: 'galponId', code: 'OBLIGATORIO' as const, message: MENSAJE_CAMPO.OBLIGATORIO },
      errorDeNombre(cuerpo.nombre),
      typeof cuerpo.fechaIngreso === 'string' && FECHA.test(cuerpo.fechaIngreso)
        ? null
        : { field: 'fechaIngreso', code: 'FORMATO_INVALIDO' as const, message: MENSAJE_CAMPO.FORMATO_INVALIDO },
      enteroPositivo('poblacionInicial', cuerpo.poblacionInicial),
      enteroPositivo('costoTotal', cuerpo.costoTotal),
    ].filter((campo): campo is ErrorDeCampo => campo !== null)
    if (errores.length > 1) return error(400, 'VALIDACION_FALLIDA', INSTANCIA, errores)
    if (errores.length === 1) return error(400, CODIGO_POR_CAMPO[errores[0].field], INSTANCIA, errores)

    const galponId = String(cuerpo.galponId)
    const nombre = String(cuerpo.nombre).trim()
    const fechaIngreso = String(cuerpo.fechaIngreso)

    // 2. Fecha futura -> galpón existe -> nombre único -> galpón DISPONIBLE (orden fijo del plan).
    if (fechaIngreso > hoyBogota()) return error(400, 'FECHA_INGRESO_FUTURA')
    const galpon = galpones.find((g) => g.id === galponId)
    if (!galpon) return error(404, 'GALPON_NO_ENCONTRADO')
    if (lotes.some((l) => l.nombre?.toLowerCase() === nombre.toLowerCase())) {
      return error(409, 'NOMBRE_DUPLICADO', INSTANCIA, [
        { field: 'nombre', code: 'DUPLICADO', message: 'El nombre ya está en uso' },
      ])
    }
    if (galpon.estado !== 'DISPONIBLE') return error(409, 'GALPON_NO_DISPONIBLE')
    if (simularError()) {
      return problema(500, 'ERROR_INTERNO', 'Error interno', 'No fue posible completar la operación. Intenta de nuevo.', INSTANCIA)
    }

    // Lote y cambio DISPONIBLE -> PRODUCTIVO (origen REGISTRO_LOTE) en la misma operación.
    const lote: LoteSimulado = {
      id: crypto.randomUUID(),
      galponId,
      nombre,
      fechaIngreso,
      poblacionInicial: cuerpo.poblacionInicial as number,
      poblacionActual: cuerpo.poblacionInicial as number,
      costoTotal: cuerpo.costoTotal as number,
    }
    lotes.push(lote)
    galpon.estado = 'PRODUCTIVO'
    return HttpResponse.json(aRespuesta(lote), { status: 201, headers: { Location: `${INSTANCIA}/${lote.id}` } })
  }),

  http.get(INSTANCIA, ({ request }) => {
    const textoPagina = new URL(request.url).searchParams.get('page') ?? '1'
    if (!/^-?\d+$/.test(textoPagina)) {
      return error(400, 'PARAMETRO_INVALIDO', INSTANCIA, [
        { field: 'page', code: 'FORMATO_INVALIDO', message: MENSAJE_CAMPO.FORMATO_INVALIDO },
      ])
    }
    const pagina = Number(textoPagina)
    if (pagina < 1) {
      return error(400, 'PARAMETRO_INVALIDO', INSTANCIA, [
        { field: 'page', code: 'MINIMO_1', message: 'Debe ser mayor o igual a 1' },
      ])
    }
    if (simularError()) {
      return problema(500, 'ERROR_CONSULTA', 'Error en la consulta', 'No fue posible obtener los datos. Puedes reintentar la consulta.', INSTANCIA)
    }

    // Fecha de ingreso descendente; desempate por id para que la paginación sea estable.
    const ordenados = [...lotes].sort(
      (a, b) => b.fechaIngreso.localeCompare(a.fechaIngreso) || a.id.localeCompare(b.id),
    )
    return HttpResponse.json(
      {
        contenido: ordenados.slice((pagina - 1) * TAMANIO, pagina * TAMANIO).map(aRespuesta),
        pagina,
        tamanio: TAMANIO,
        totalElementos: ordenados.length,
        totalPaginas: Math.ceil(ordenados.length / TAMANIO),
      },
      { headers: { 'Cache-Control': 'no-store' } },
    )
  }),
]
