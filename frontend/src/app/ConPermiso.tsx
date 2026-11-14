import type { ReactElement } from 'react'
import { Navigate } from 'react-router'
import { puede, RUTA_INICIAL, type Permiso } from './permisos'
import { useRol } from './rol-contexto'

/**
 * Deja pasar a la pantalla solo si el rol actual tiene el permiso que esa pantalla necesita, y si no lo tiene
 * lo devuelve a su pantalla inicial.
 *
 * La guarda nombra el permiso, no el rol: dice qué hace la pantalla en lugar de quién puede entrar hoy, así
 * cambiar la matriz de `permisos.ts` no obliga a tocar el router.
 */
export function ConPermiso({ permiso, children }: { permiso: Permiso; children: ReactElement }) {
  const { rol } = useRol()
  return puede(rol, permiso) ? children : <Navigate replace to={RUTA_INICIAL[rol]} />
}
