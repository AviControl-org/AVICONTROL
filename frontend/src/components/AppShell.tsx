import { Link, NavLink, Outlet, useMatches } from 'react-router'
import { NOMBRE_ROL, ROLES, useRol, type Rol } from '../app/rol-contexto'
import { Icono, Isotipo, type NombreIcono } from './Icono'

interface EnlaceNav {
  a: string
  texto: string
  icono: NombreIcono
}

// Navegación por rol, como en los mockups. Cada rol ve solo las pantallas que su matriz de permisos
// le permite usar (app/permisos.ts); el operario de granja solo consulta.
const NAV: Record<Rol, EnlaceNav[]> = {
  ADMINISTRADOR: [
    { a: '/galpones', texto: 'Galpones', icono: 'galpon' },
    { a: '/lotes', texto: 'Lotes', icono: 'lotes' },
    { a: '/alertas', texto: 'Alertas', icono: 'alerta' },
  ],
  TECNICO_INFRAESTRUCTURA: [
    { a: '/alertas-mantenimiento/generar', texto: 'Alerta de mantenimiento', icono: 'llave' },
  ],
  OPERARIO: [
    { a: '/galpones', texto: 'Galpones', icono: 'galpon' },
    { a: '/lotes', texto: 'Lotes', icono: 'lotes' },
  ],
}

export interface MigaRuta {
  miga: string
}

/** Barra lateral con navegación por rol, barra superior con la ruta y el rol, y el contenido. */
export function AppShell() {
  const { rol, cambiarRol } = useRol()
  const migas = useMatches()
    .flatMap(ruta => {
      const texto = (ruta.handle as MigaRuta | undefined)?.miga
      return texto ? [{ texto, ruta: ruta.pathname }] : []
    })

  return (
    <div className="app">
      <aside className="side">
        <NavLink className="logo" to="/">
          <Isotipo />
          <b>AviControl</b>
        </NavLink>
        <nav className="nav" aria-label="Principal">
          {NAV[rol].map((enlace) => (
            <NavLink key={enlace.a} to={enlace.a} className={({ isActive }) => (isActive ? 'on' : undefined)}>
              <Icono nombre={enlace.icono} />
              {enlace.texto}
            </NavLink>
          ))}
        </nav>
        <div className="foot cap">
          <span className="b6">Módulo 1</span>
          <span>Gestión de galpones</span>
        </div>
      </aside>
      <div className="main">
        <header className="top">
          <nav className="crumbs" aria-label="Ruta de navegación">
            {migas.map(({ texto, ruta }, i) => i === migas.length - 1
              ? <b key={ruta} aria-current="page">{texto}</b>
              : <span key={ruta}><Link to={ruta}>{texto}</Link> <span aria-hidden="true">›</span></span>)}
          </nav>
          <div className="who">
            <Icono nombre="usuario" />
            <label className="lbl" htmlFor="rol-demo">
              Rol (demo)
            </label>
            <select
              id="rol-demo"
              className="rol-demo"
              value={rol}
              onChange={(evento) => cambiarRol(evento.target.value as Rol)}
            >
              {ROLES.map((opcion) => (
                <option key={opcion} value={opcion}>
                  {NOMBRE_ROL[opcion]}
                </option>
              ))}
            </select>
          </div>
        </header>
        <main className="content">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
