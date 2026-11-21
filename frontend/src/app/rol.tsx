import { useState, type ReactNode } from 'react'
import { CLAVE_ROL, RolContext, type Rol } from './rol-contexto'

function rolGuardado(): Rol {
  try {
    return localStorage.getItem(CLAVE_ROL) === 'TECNICO_INFRAESTRUCTURA' ? 'TECNICO_INFRAESTRUCTURA' : 'ADMINISTRADOR'
  } catch {
    return 'ADMINISTRADOR'
  }
}

export function RolProvider({ children }: { children: ReactNode }) {
  const [rol, setRol] = useState<Rol>(rolGuardado)

  const cambiarRol = (nuevo: Rol) => {
    setRol(nuevo)
    try {
      localStorage.setItem(CLAVE_ROL, nuevo)
    } catch {
      // Sin almacenamiento disponible: el rol dura solo la sesión.
    }
  }

  return <RolContext.Provider value={{ rol, cambiarRol }}>{children}</RolContext.Provider>
}
