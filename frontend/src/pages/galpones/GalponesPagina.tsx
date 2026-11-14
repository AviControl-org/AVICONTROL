import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { Link, useLocation, useSearchParams } from 'react-router'
import { listarGalpones, type ParametrosListado } from '../../api/galpones'
import { ESTADOS_GALPON, type EstadoGalpon } from '../../api/tipos'
import { Banner } from '../../components/Banner'
import { EmptyState } from '../../components/EmptyState'
import { ESTADO_GALPON } from '../../components/estadoGalpon'
import { formatoEntero } from '../../components/formato'
import { Icono } from '../../components/Icono'
import { PageHeader } from '../../components/PageHeader'
import { Pager } from '../../components/Pager'
import { StatusChip } from '../../components/StatusChip'
import type { AvisoListado } from '../aviso'
import { ErrorDeConsulta } from '../../components/ErrorDeConsulta'
import { puede } from '../../app/permisos'
import { useRol } from '../../app/rol-contexto'

type Orden = NonNullable<ParametrosListado['orden']>

const ORDENES: { valor: Orden; texto: string }[] = [
  { valor: 'nombre,asc', texto: 'Nombre (A a Z)' },
  { valor: 'nombre,desc', texto: 'Nombre (Z a A)' },
  { valor: 'capacidad,asc', texto: 'Capacidad (menor a mayor)' },
  { valor: 'capacidad,desc', texto: 'Capacidad (mayor a menor)' },
  { valor: 'estado,asc', texto: 'Estado (A a Z)' },
  { valor: 'estado,desc', texto: 'Estado (Z a A)' },
]

// Orden de los filtros en los mockups.
const FILTROS: EstadoGalpon[] = ['DISPONIBLE', 'PRODUCTIVO', 'EN_COSECHA', 'VACIADO_SANITARIO', 'MANTENIMIENTO', 'AISLAMIENTO']

function leerCriterios(query: URLSearchParams): ParametrosListado {
  const estado = query.get('estado')
  const orden = query.get('orden')
  const pagina = Number(query.get('page') ?? '1')
  return {
    nombre: query.get('nombre') ?? '',
    estado: ESTADOS_GALPON.includes(estado as EstadoGalpon) ? (estado as EstadoGalpon) : undefined,
    orden: ORDENES.some((o) => o.valor === orden) ? (orden as Orden) : 'nombre,asc',
    page: Number.isInteger(pagina) && pagina >= 1 ? pagina : 1,
  }
}

/** Consultar galpón y lote · Listado (US3, FR-001 a FR-009, FR-019, FR-020). */
export function GalponesPagina() {
  const { rol } = useRol()
  const [query, setQuery] = useSearchParams()
  const aviso = useLocation().state as AvisoListado | null
  const criterios = leerCriterios(query)
  const [busqueda, setBusqueda] = useState(criterios.nombre ?? '')

  const consulta = useQuery({
    queryKey: ['galpones', criterios],
    queryFn: () => listarGalpones(criterios),
    placeholderData: keepPreviousData,
  })

  const actualizar = (cambios: Partial<Record<'nombre' | 'estado' | 'orden' | 'page', string | undefined>>) => {
    setQuery((actual) => {
      const siguiente = new URLSearchParams(actual)
      for (const [clave, valor] of Object.entries(cambios)) {
        if (valor) siguiente.set(clave, valor)
        else siguiente.delete(clave)
      }
      return siguiente
    })
  }

  // La búsqueda se aplica al dejar de escribir y vuelve a la primera página.
  useEffect(() => {
    if (busqueda.trim() === (criterios.nombre ?? '').trim()) return
    const espera = setTimeout(() => actualizar({ nombre: busqueda.trim() || undefined, page: undefined }), 350)
    return () => clearTimeout(espera)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [busqueda])

  const datos = consulta.data

  return (
    <>
      <PageHeader
        titulo="Galpones"
        subtitulo="Consulta el estado de cada galpón y abre su detalle."
        acciones={
          <>
            {puede(rol, 'REGISTRAR_LOTE') && (
              <Link className="btn" to="/lotes/registrar">
                <Icono nombre="mas" />
                Registrar lote
              </Link>
            )}
            {puede(rol, 'REGISTRAR_GALPON') && (
              <Link className="btn p" to="/galpones/registrar">
                <Icono nombre="mas" />
                Registrar galpón
              </Link>
            )}
          </>
        }
      />

      {aviso && (
        <Banner tono="ok" titulo={aviso.titulo}>
          {aviso.detalle}
        </Banner>
      )}

      <div className="row sp wrap">
        <div className="search">
          <Icono nombre="buscar" />
          <input
            className="input"
            type="search"
            value={busqueda}
            placeholder="Buscar galpón por nombre"
            aria-label="Buscar galpón por nombre"
            onChange={(evento) => setBusqueda(evento.target.value)}
          />
        </div>
        <div className="row">
          <label className="lbl" htmlFor="orden">
            Ordenar por
          </label>
          <div className="inw" style={{ width: 260 }}>
            <select
              className="input"
              id="orden"
              value={criterios.orden}
              onChange={(evento) => actualizar({ orden: evento.target.value, page: undefined })}
            >
              {ORDENES.map((orden) => (
                <option key={orden.valor} value={orden.valor}>
                  {orden.texto}
                </option>
              ))}
            </select>
            <span className="chev">
              <Icono nombre="abajo" />
            </span>
          </div>
        </div>
      </div>

      <div className="filtros-estado"><span id="titulo-filtro" className="lbl">Filtrar por estado</span>
      <nav className="seg" aria-labelledby="titulo-filtro">
        <button
          type="button"
          className={criterios.estado ? undefined : 'on'}
          aria-pressed={!criterios.estado}
          onClick={() => actualizar({ estado: undefined, page: undefined })}
        >
          Todos
        </button>
        {FILTROS.map((estado) => (
          <button
            key={estado}
            type="button"
            className={criterios.estado === estado ? 'on' : undefined}
            aria-pressed={criterios.estado === estado}
            onClick={() => actualizar({ estado, page: undefined })}
          >
            {ESTADO_GALPON[estado].texto}
          </button>
        ))}
      </nav></div>

      {consulta.isError && (
        <ErrorDeConsulta
          error={consulta.error}
          recurso="los galpones"
          criterios={[
            criterios.nombre ? `Búsqueda: ${criterios.nombre}` : null,
            criterios.estado ? `Estado: ${ESTADO_GALPON[criterios.estado].texto}` : null,
            `Orden: ${ORDENES.find((o) => o.valor === criterios.orden)?.texto}`,
          ]}
          onReintentar={() => consulta.refetch()}
          reintentando={consulta.isFetching}
        />
      )}

      {datos?.sinCoincidencias && (
        <Banner tono="info" titulo={`No encontramos galpones con «${criterios.nombre}»`}>
          {datos.mensaje}
        </Banner>
      )}

      {consulta.isPending && <p className="cap">Cargando galpones…</p>}

      {datos && datos.totalElementos === 0 && !consulta.isError && (
        <section className="card">
          {criterios.estado ? <EmptyState titulo={'No hay galpones en ' + ESTADO_GALPON[criterios.estado].texto}>
            <button type="button" className="btn" onClick={() => actualizar({ estado: undefined, page: undefined })}>Quitar filtro</button>
          </EmptyState> : <EmptyState titulo="Todavía no hay galpones registrados">
            {puede(rol, 'REGISTRAR_GALPON') ? (
              <Link className="btn p" to="/galpones/registrar">
                <Icono nombre="mas" />
                Registrar galpón
              </Link>
            ) : (
              <p className="mut">El administrador es quien registra los galpones.</p>
            )}
          </EmptyState>}
        </section>
      )}

      {datos && datos.totalElementos > 0 && !consulta.isError && (
        <section className="card flush" aria-busy={consulta.isFetching}>
          <table className="tbl">
            <thead>
              <tr>
                <th scope="col">Nombre</th>
                <th scope="col" className="r">
                  Capacidad (pollos)
                </th>
                <th scope="col">Estado</th>
                <th scope="col">Acciones</th>
              </tr>
            </thead>
            <tbody>
              {datos.contenido.map((galpon) => (
                <tr key={galpon.id} className={galpon.id === aviso?.resaltarId ? 'new' : undefined}>
                  <td>
                    <Link className="b6" to={`/galpones/${galpon.id}`}>
                      {galpon.nombre}
                    </Link>
                  </td>
                  <td className="r">
                    <span className="data">{formatoEntero(galpon.capacidad)}</span>
                  </td>
                  <td>
                    <StatusChip estado={galpon.estado} />
                  </td>
                  <td>
                    <div className="acts">
                      <Link className="btn t sm" to={`/galpones/${galpon.id}/editar`}>
                        Editar
                      </Link>
                      <Link className="btn t sm" to={`/galpones/${galpon.id}/estado`}>
                        Ver estado e historial
                      </Link>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <Pager
            pagina={datos.pagina}
            tamanio={datos.tamanio}
            totalElementos={datos.totalElementos}
            totalPaginas={datos.totalPaginas}
            recurso="galpones"
            onCambiar={(pagina) => actualizar({ page: pagina === 1 ? undefined : String(pagina) })}
          />
        </section>
      )}
    </>
  )
}
