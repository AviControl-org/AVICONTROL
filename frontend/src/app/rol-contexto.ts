import { createContext, useContext } from 'react'

// Sin autenticación (P-01): el rol se elige en el encabezado solo para la demostración.
// Los tres actores humanos de `docs/Casos de uso.md`; qué puede cada uno está en `permisos.ts`.
export type Rol = 'ADMINISTRADOR' | 'TECNICO_INFRAESTRUCTURA' | 'OPERARIO'

export const NOMBRE_ROL: Record<Rol, string> = {
  ADMINISTRADOR: 'Administrador',
  TECNICO_INFRAESTRUCTURA: 'Técnico de infraestructura',
  OPERARIO: 'Operario de granja',
}

export const ROLES = Object.keys(NOMBRE_ROL) as Rol[]

// `Object.hasOwn` y no `in`: `in` recorre el prototipo y aceptaria "toString" o "constructor" como rol.
export function esRol(valor: unknown): valor is Rol {
  return typeof valor === 'string' && Object.hasOwn(NOMBRE_ROL, valor)
}

/** `tecnicoId` provisional que pide el plan de generar alerta de mantenimiento (P-14). */
export const TECNICO_ID_DEMO = 'tecnico-01'

export const CLAVE_ROL = 'avicontrol.rol-demo'

interface ContextoRol {
  rol: Rol
  cambiarRol: (rol: Rol) => void
}

export const RolContext = createContext<ContextoRol | null>(null)

export function useRol(): ContextoRol {
  const contexto = useContext(RolContext)
  if (!contexto) {
    throw new Error('useRol debe usarse dentro de RolProvider')
  }
  return contexto
}
