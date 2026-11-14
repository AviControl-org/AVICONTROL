import { useState, type ReactNode } from 'react'
import { CLAVE_ROL, esRol, RolContext, type Rol } from './rol-contexto'

const ROL_POR_DEFECTO: Rol = 'ADMINISTRADOR'

// Se valida contra los roles que existen: así agregar un rol no exige tocar esta función, y un valor
// viejo o manipulado en localStorage cae en el rol por defecto en lugar de romper la aplicación.
function rolGuardado(): Rol {
  try {
    const guardado = localStorage.getItem(CLAVE_ROL)
    return esRol(guardado) ? guardado : ROL_POR_DEFECTO
  } catch {
    return ROL_POR_DEFECTO
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
