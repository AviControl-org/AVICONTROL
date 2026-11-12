import type { ReactElement } from 'react'
import { Navigate } from 'react-router'
import { useRol, type Rol } from './rol-contexto'

export function VistaRol({ rol, children }: { rol: Rol; children: ReactElement }) {
  const actual = useRol().rol
  if (actual === rol) return children
  return <Navigate replace to={actual === 'ADMINISTRADOR' ? '/galpones' : '/alertas-mantenimiento/generar'} />
}
