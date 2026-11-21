import { createContext, useContext } from 'react'

// Sin autenticación (P-01): el rol se elige en el encabezado solo para la demostración.
export type Rol = 'ADMINISTRADOR' | 'TECNICO_INFRAESTRUCTURA'

export const NOMBRE_ROL: Record<Rol, string> = {
  ADMINISTRADOR: 'Administrador',
  TECNICO_INFRAESTRUCTURA: 'Técnico de infraestructura',
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
