import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

// Evidencia de contrato (arquitectura.md, Pruebas): la API simulada debe responder con la misma forma que los
// JSON del backend en avicontrol/src/test/resources/contratos/. Se comparan campos y tipos, no valores.

const CONTRATOS = resolve(import.meta.dirname, '../../../avicontrol/src/test/resources/contratos')

function contrato(archivo: string): unknown {
  return JSON.parse(readFileSync(resolve(CONTRATOS, archivo), 'utf8'))
}

/** Estructura de un JSON: nombres de campo y tipos, recorriendo objetos y el primer elemento de cada lista. */
function forma(valor: unknown): unknown {
  if (Array.isArray(valor)) return valor.length > 0 ? [forma(valor[0])] : []
  if (valor !== null && typeof valor === 'object') {
    return Object.fromEntries(
      Object.entries(valor)
        .sort(([a], [b]) => a.localeCompare(b))
        .map(([clave, contenido]) => [clave, forma(contenido)]),
    )
  }
  return typeof valor
}

async function pedir(ruta: string, opciones?: RequestInit) {
  const respuesta = await fetch(`/api/v1${ruta}`, {
    ...opciones,
    headers: { 'Content-Type': 'application/json' },
  })
  return { status: respuesta.status, cuerpo: (await respuesta.json()) as unknown }
}

describe('la API simulada coincide con los contratos del backend', () => {
  it.each([
    ['galpon-lista-response.json', '/galpones?page=1'],
    ['galpon-lista-sin-coincidencias-response.json', '/galpones?nombre=xyz'],
    ['galpon-detalle-response.json', '/galpones/550e8400-e29b-41d4-a716-446655440000'],
    ['galpon-detalle-sin-lote-response.json', '/galpones/6ba7b810-9dad-41d1-80b4-00c04fd430c8'],
    ['galpon-historial-lotes-response.json', '/galpones/550e8400-e29b-41d4-a716-446655440000/lotes'],
    ['error-galpon-no-encontrado.json', '/galpones/d9b2d63d-a233-4123-847a-3f2c0c1e6b55'],
    ['lote-lista-response.json', '/lotes?page=1'],
  ])('%s', async (archivo, ruta) => {
    const { cuerpo } = await pedir(ruta)
    expect(forma(cuerpo)).toEqual(forma(contrato(archivo)))
  })

  it.each([
    ['galpon-registrar-response.json', { nombre: 'Galpón Contrato', capacidad: 1500 }, 201],
    ['error-nombre-duplicado.json', { nombre: 'galpón norte', capacidad: 800 }, 409],
    ['error-capacidad-invalida.json', { nombre: 'Galpón Sur 2', capacidad: 10.5 }, 400],
  ])('%s', async (archivo, body, status) => {
    const respuesta = await pedir('/galpones', { method: 'POST', body: JSON.stringify(body) })
    expect(respuesta.status).toBe(status)
    expect(forma(respuesta.cuerpo)).toEqual(forma(contrato(archivo)))
  })

  it('lote-registrar-request.json produce la forma de lote-registrar-response.json', async () => {
    const request = contrato('lote-registrar-request.json') as Record<string, unknown>
    // El request de ejemplo apunta a un galpón Productivo de los datos simulados: se usa uno Disponible.
    const body = { ...request, galponId: '0b6a1c2e-1a11-4a2b-9c01-000000000010', nombre: 'Lote Contrato' }
    const respuesta = await pedir('/lotes', { method: 'POST', body: JSON.stringify(body) })
    expect(respuesta.status).toBe(201)
    expect(forma(respuesta.cuerpo)).toEqual(forma(contrato('lote-registrar-response.json')))
  })

  it('error-galpon-no-disponible.json', async () => {
    const body = { ...(contrato('lote-registrar-request.json') as object), nombre: 'Lote Otro' }
    const respuesta = await pedir('/lotes', { method: 'POST', body: JSON.stringify(body) })
    expect(respuesta.status).toBe(409)
    expect(forma(respuesta.cuerpo)).toEqual(forma(contrato('error-galpon-no-disponible.json')))
  })
})
