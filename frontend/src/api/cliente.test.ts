import { afterEach, describe, expect, it, vi } from 'vitest'
import { ErrorApi, ErrorDeConexion, solicitar } from './cliente'

function responder(status: number, cuerpo: string, tipo = 'application/json') {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(cuerpo, { status, headers: { 'Content-Type': tipo } })))
}

afterEach(() => vi.unstubAllGlobals())

describe('solicitar', () => {
  it('antepone /api/v1 y devuelve el JSON de una respuesta correcta', async () => {
    responder(200, '{"id":"1"}')
    await expect(solicitar('/galpones/1')).resolves.toEqual({ id: '1' })
    expect(fetch).toHaveBeenCalledWith('/api/v1/galpones/1', expect.anything())
  })

  it('convierte un error RFC 9457 en ErrorApi conservando code y fieldErrors', async () => {
    const problema = {
      type: 'https://avicontrol.unimag.edu.co/errors/nombre-duplicado',
      title: 'Nombre duplicado',
      status: 409,
      detail: 'Ya existe un galpón con ese nombre.',
      instance: '/api/v1/galpones',
      code: 'NOMBRE_DUPLICADO',
      fieldErrors: [{ field: 'nombre', code: 'DUPLICADO', message: 'El nombre ya está en uso' }],
    }
    responder(409, JSON.stringify(problema), 'application/problem+json')

    const error = await solicitar('/galpones', { method: 'POST', body: '{}' }).catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ErrorApi)
    expect((error as ErrorApi).problema).toEqual(problema)
  })

  it('usa ERROR_CONSULTA en un GET y ERROR_INTERNO en una escritura si el cuerpo no es un problema', async () => {
    responder(502, 'Bad Gateway', 'text/plain')
    const consulta = (await solicitar('/galpones').catch((e: unknown) => e)) as ErrorApi
    expect(consulta.problema.code).toBe('ERROR_CONSULTA')

    responder(502, 'Bad Gateway', 'text/plain')
    const escritura = (await solicitar('/galpones', { method: 'POST', body: '{}' }).catch((e: unknown) => e)) as ErrorApi
    expect(escritura.problema.code).toBe('ERROR_INTERNO')
  })

  it('lanza ErrorDeConexion cuando no hay respuesta del servidor', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))
    await expect(solicitar('/galpones')).rejects.toBeInstanceOf(ErrorDeConexion)
  })
})
