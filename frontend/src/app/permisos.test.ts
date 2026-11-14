import { describe, expect, it } from 'vitest'
import { puede, RUTA_INICIAL, type Permiso } from './permisos'
import { esRol, ROLES } from './rol-contexto'

const rolesCon = (permiso: Permiso) => ROLES.filter((rol) => puede(rol, permiso))

// Cada caso cita la regla de docs/Casos de uso.md que verifica.
describe('matriz de permisos', () => {
  it('los tres actores pueden consultar galpones y lotes (caso de uso compartido)', () => {
    expect(rolesCon('CONSULTAR_GALPON_LOTE')).toEqual(ROLES)
  })

  it('el operario solo consulta: «no puede registrar, editar ni cambiar el estado de ningún galpón»', () => {
    const escrituras: Permiso[] = [
      'REGISTRAR_GALPON',
      'EDITAR_GALPON',
      'REGISTRAR_LOTE',
      'ACTUALIZAR_ESTADO',
      'GENERAR_ALERTA_MANTENIMIENTO',
      'ATENDER_ALERTA_MANTENIMIENTO',
    ]
    for (const permiso of escrituras) {
      expect(puede('OPERARIO', permiso), permiso).toBe(false)
    }
  })

  it('el administrador «es el único que puede registrar un galpón, editar un galpón, registrar un lote y actualizar el estado»', () => {
    expect(rolesCon('REGISTRAR_GALPON')).toEqual(['ADMINISTRADOR'])
    expect(rolesCon('EDITAR_GALPON')).toEqual(['ADMINISTRADOR'])
    expect(rolesCon('REGISTRAR_LOTE')).toEqual(['ADMINISTRADOR'])
    expect(rolesCon('ACTUALIZAR_ESTADO')).toEqual(['ADMINISTRADOR'])
  })

  it('el técnico genera las alertas de mantenimiento y el administrador las atiende', () => {
    expect(rolesCon('GENERAR_ALERTA_MANTENIMIENTO')).toEqual(['TECNICO_INFRAESTRUCTURA'])
    expect(rolesCon('ATENDER_ALERTA_MANTENIMIENTO')).toEqual(['ADMINISTRADOR'])
  })

  it('cada rol tiene una pantalla inicial propia', () => {
    for (const rol of ROLES) {
      expect(RUTA_INICIAL[rol], rol).toMatch(/^\//)
    }
  })
})

describe('esRol', () => {
  it('acepta los tres roles', () => {
    expect(ROLES.every(esRol)).toBe(true)
  })

  it('rechaza un valor viejo o manipulado de localStorage', () => {
    for (const valor of [null, undefined, '', 'ADMIN', 'administrador', 'toString', 42]) {
      expect(esRol(valor), String(valor)).toBe(false)
    }
  })
})
