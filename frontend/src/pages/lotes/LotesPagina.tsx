import { keepPreviousData, useQueries, useQuery } from '@tanstack/react-query'
import { Link, useLocation, useSearchParams } from 'react-router'
import { obtenerGalpon } from '../../api/galpones'
import { listarLotes } from '../../api/lotes'
import { Banner } from '../../components/Banner'
import { EmptyState } from '../../components/EmptyState'
import { ErrorDeConsulta } from '../../components/ErrorDeConsulta'
import { formatoEntero, formatoFecha, formatoPesos } from '../../components/formato'
import { Icono } from '../../components/Icono'
import { PageHeader } from '../../components/PageHeader'
import { Pager } from '../../components/Pager'
import type { AvisoListado } from '../aviso'
import { puede } from '../../app/permisos'
import { useRol } from '../../app/rol-contexto'

/** Listado de lotes de registrar lote (US2, FR-013): activos y anteriores, del ingreso más reciente al más antiguo. */
export function LotesPagina() {
  const { rol } = useRol()
  const [query, setQuery] = useSearchParams()
  const aviso = useLocation().state as AvisoListado | null
  const pagina = Math.max(1, Number(query.get('page') ?? '1') || 1)

  const consulta = useQuery({
    queryKey: ['lotes', pagina],
    queryFn: () => listarLotes(pagina),
    placeholderData: keepPreviousData,
  })

  // El listado solo trae galponId: el nombre se pide al detalle de cada galpón de la página.
  const idsGalpon = [...new Set(consulta.data?.contenido.map((lote) => lote.galponId) ?? [])]
  const galpones = useQueries({
    queries: idsGalpon.map((id) => ({ queryKey: ['galpon', id], queryFn: () => obtenerGalpon(id) })),
  })
  const nombreGalpon = new Map(galpones.flatMap((g) => (g.data ? [[g.data.id, g.data.nombre] as const] : [])))

  const datos = consulta.data

  return (
    <>
      <PageHeader
        titulo="Lotes"
        subtitulo="Todos los lotes registrados, del ingreso más reciente al más antiguo."
        acciones={
          puede(rol, 'REGISTRAR_LOTE') ? (
            <Link className="btn p" to="/lotes/registrar">
              <Icono nombre="mas" />
              Registrar lote
            </Link>
          ) : undefined
        }
      />

      {aviso && (
        <Banner tono="ok" titulo={aviso.titulo}>
          {aviso.detalle}
        </Banner>
      )}

      {consulta.isError && (
        <ErrorDeConsulta
          error={consulta.error}
          recurso="los lotes"
          onReintentar={() => consulta.refetch()}
          reintentando={consulta.isFetching}
        />
      )}

      {consulta.isPending && <p className="cap">Cargando lotes…</p>}

      {datos && datos.totalElementos === 0 && !consulta.isError && (
        <section className="card">
          <EmptyState titulo="Todavía no hay lotes registrados">
            {puede(rol, 'REGISTRAR_LOTE') ? (
              <Link className="btn p" to="/lotes/registrar">
                <Icono nombre="mas" />
                Registrar lote
              </Link>
            ) : (
              <p className="mut">El administrador es quien registra los lotes.</p>
            )}
          </EmptyState>
        </section>
      )}

      {datos && datos.totalElementos > 0 && !consulta.isError && (
        <section className="card flush" aria-busy={consulta.isFetching}>
          <table className="tbl">
            <thead>
              <tr>
                <th scope="col">Lote</th>
                <th scope="col">Galpón</th>
                <th scope="col">Fecha de ingreso</th>
                <th scope="col" className="r">
                  Población inicial
                </th>
                <th scope="col" className="r">
                  Población actual
                </th>
                <th scope="col" className="r">
                  Costo total (COP)
                </th>
                <th scope="col">Condición</th>
              </tr>
            </thead>
            <tbody>
              {datos.contenido.map((lote) => (
                <tr key={lote.id} className={lote.id === aviso?.resaltarId ? 'new' : undefined}>
                  <td className="data">{lote.nombre}</td>
                  <td>
                    <Link to={`/galpones/${lote.galponId}`}>{nombreGalpon.get(lote.galponId) ?? 'Ver galpón'}</Link>
                  </td>
                  <td className="data">{formatoFecha(lote.fechaIngreso)}</td>
                  <td className="r data">{formatoEntero(lote.poblacionInicial)}</td>
                  <td className="r data">{formatoEntero(lote.poblacionActual)}</td>
                  <td className="r data">{formatoPesos(lote.costoTotal)}</td>
                  <td>
                    {lote.desvinculadoEn ? (
                      <span className="mut">Anterior</span>
                    ) : (
                      <span className="chip ok">
                        <Icono nombre="check" />
                        Activo
                      </span>
                    )}
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
            recurso="lotes"
            onCambiar={(numero) => setQuery(numero === 1 ? {} : { page: String(numero) })}
          />
        </section>
      )}
    </>
  )
}
