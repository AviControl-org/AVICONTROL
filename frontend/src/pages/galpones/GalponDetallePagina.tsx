import { useQuery } from '@tanstack/react-query'
import { useState, type ReactNode } from 'react'
import { Link, useParams } from 'react-router'
import { ErrorApi } from '../../api/cliente'
import { obtenerGalpon, obtenerHistorialLotes, type LoteDetalleResponse } from '../../api/galpones'
import { Banner } from '../../components/Banner'
import { EmptyState } from '../../components/EmptyState'
import { formatoEntero, formatoFecha, formatoPesos } from '../../components/formato'
import { Icono } from '../../components/Icono'
import { PageHeader } from '../../components/PageHeader'
import { StatTile } from '../../components/StatTile'
import { StatusChip } from '../../components/StatusChip'
import { ErrorDeConsulta } from '../../components/ErrorDeConsulta'
import { puede } from '../../app/permisos'
import { useRol } from '../../app/rol-contexto'

/** Un campo del lote que la API omitió por estar vacío o inválido se muestra como "No disponible" (FR-016). */
function oNoDisponible<T>(valor: T | undefined, mostrar: (valor: T) => ReactNode): ReactNode {
  return valor === undefined ? <span className="na">No disponible</span> : mostrar(valor)
}

function IdentificadorLote({ id }: { id: string }) {
  const [mensaje, setMensaje] = useState('')
  async function copiar() {
    try {
      await navigator.clipboard.writeText(id)
      setMensaje('Identificador copiado.')
    } catch {
      setMensaje('Selecciona el identificador y cópialo manualmente.')
    }
  }
  return <>
    <div className="row wrap"><code>{id}</code><button className="btn sm" type="button" onClick={copiar}>Copiar</button></div>
    <span className="cap" role="status">{mensaje}</span>
  </>
}

function textoONoDisponible<T>(valor: T | undefined, mostrar: (valor: T) => string): string {
  return valor === undefined ? 'No disponible' : mostrar(valor)
}

/** Consultar galpón y lote · Detalle del galpón, su lote activo y el historial de lotes (US3). */
export function GalponDetallePagina() {
  const { rol } = useRol()
  const { galponId = '' } = useParams()
  const detalle = useQuery({ queryKey: ['galpon', galponId], queryFn: () => obtenerGalpon(galponId) })
  const historial = useQuery({
    queryKey: ['galpon', galponId, 'lotes'],
    queryFn: () => obtenerHistorialLotes(galponId),
    enabled: detalle.isSuccess,
  })

  const volver = (
    <Link className="btn t" to="/galpones">
      <Icono nombre="izquierda" />
      Volver al listado
    </Link>
  )

  if (detalle.isPending) {
    return <p className="cap">Cargando galpón…</p>
  }

  if (detalle.isError) {
    const problema = detalle.error instanceof ErrorApi ? detalle.error.problema : undefined
    // 404 y 400: no se muestran datos de otro galpón y se permite volver al listado (FR-018, FR-021).
    if (problema && (problema.status === 404 || problema.status === 400)) {
      return (
        <>
          <PageHeader titulo={problema.title} subtitulo="No hay datos que mostrar." acciones={volver} />
          <Banner tono="warn" titulo={problema.title}>
            {problema.detail}
          </Banner>
        </>
      )
    }
    return (
      <>
        <PageHeader titulo="Detalle del galpón" subtitulo="Detalle del galpón y su lote activo." acciones={volver} />
        <ErrorDeConsulta
          error={detalle.error}
          recurso="el galpón"
          onReintentar={() => detalle.refetch()}
          reintentando={detalle.isFetching}
        />
      </>
    )
  }

  const galpon = detalle.data
  const lote = galpon.loteActivo
  const lotesHistorial: { lote: LoteDetalleResponse; activo: boolean }[] = historial.data
    ? [
        ...(historial.data.loteActivo ? [{ lote: historial.data.loteActivo, activo: true }] : []),
        ...historial.data.lotesAnteriores.map((anterior) => ({ lote: anterior, activo: false })),
      ]
    : []

  return (
    <>
      <PageHeader
        titulo={galpon.nombre}
        subtitulo="Detalle del galpón y su lote activo."
        acciones={
          <>
            {volver}
            {puede(rol, 'EDITAR_GALPON') && (
              <Link className="btn" to={`/galpones/${galpon.id}/editar`}>
                <Icono nombre="lapiz" />
                Editar galpón
              </Link>
            )}
            {puede(rol, 'ACTUALIZAR_ESTADO') && (
              <Link className="btn p" to={`/galpones/${galpon.id}/estado`}>
                <Icono nombre="reintentar" />
                Ver estado e historial
              </Link>
            )}
          </>
        }
      />

      <div className="detalle-resumen">
        <section className="card lg">
          <h2 className="h3">Galpón</h2>
          <dl className="datos-galpon">
            <div><dt>Nombre</dt><dd className="b6">{galpon.nombre}</dd></div>
            <div><dt>Capacidad</dt><dd className="data">{formatoEntero(galpon.capacidad)} pollos</dd></div>
            <div><dt>Estado</dt><dd><StatusChip estado={galpon.estado} /></dd></div>
          </dl>
        </section>

        <section className="card lg">
          <div className="ch">
            <h2 className="h3">Lote activo{lote && <> · {oNoDisponible(lote.nombre, nombre => nombre)}</>}</h2>
          </div>
          {lote ? (
            <div className="datos-lote-activo">
              <div className="g3" style={{ gap: 'var(--space-3)' }}>
                <StatTile
                  etiqueta="Población inicial"
                  valor={textoONoDisponible(lote.poblacionInicial, formatoEntero)}
                  unidad="pollos"
                />
                <StatTile
                  etiqueta="Población actual"
                  valor={textoONoDisponible(lote.poblacionActual, formatoEntero)}
                  unidad="pollos"
                  clave
                />
                <StatTile etiqueta="Edad del lote" valor={textoONoDisponible(lote.edadDias, String)} unidad="días" />
              </div>
              <dl className="kv">
                <dt>Identificador técnico</dt>
                <dd><IdentificadorLote key={lote.id} id={lote.id}/></dd>
                <dt>Fecha de ingreso</dt>
                <dd className="data">{oNoDisponible(lote.fechaIngreso, formatoFecha)}</dd>
                <dt>Costo total</dt>
                <dd className="data">{oNoDisponible(lote.costoTotal, (costo) => `${formatoPesos(costo)} COP`)}</dd>
              </dl>
            </div>
          ) : (
            <EmptyState titulo={galpon.mensaje ?? 'El galpón no tiene un lote activo'}>
              {galpon.estado === 'DISPONIBLE' && puede(rol, 'REGISTRAR_LOTE') && (
                <>
                  <p className="mut">Registra un lote para iniciar la crianza.</p>
                  <Link className="btn" to={`/lotes/registrar?galponId=${galpon.id}`}>
                    <Icono nombre="mas" />
                    Registrar lote
                  </Link>
                </>
              )}
            </EmptyState>
          )}
        </section>
      </div>

      {galpon.incidencias?.map((incidencia) => (
        <Banner key={incidencia.mensaje} tono="info" titulo="Dato no disponible">
          {incidencia.mensaje}
        </Banner>
      ))}

      <section className="card flush">
        <div className="tab">
          <h2 className="h3">Historial de lotes</h2>
        </div>
        {historial.isPending && <p className="cap tab">Cargando historial…</p>}
        {historial.isError && (
          <div className="tab">
            <ErrorDeConsulta
              error={historial.error}
              recurso="el historial de lotes"
              onReintentar={() => historial.refetch()}
              reintentando={historial.isFetching}
            />
          </div>
        )}
        {historial.isSuccess && lotesHistorial.length === 0 && (
          <p className="tab mut">No existen lotes registrados para este galpón.</p>
        )}
        {lotesHistorial.length > 0 && (
          <table className="tbl">
            <thead>
              <tr>
                <th scope="col">Lote</th>
                <th scope="col">Fecha de ingreso</th>
                <th scope="col" className="r">
                  Población inicial
                </th>
                <th scope="col" className="r">
                  Costo total (COP)
                </th>
                <th scope="col">Condición</th>
              </tr>
            </thead>
            <tbody>
              {lotesHistorial.map(({ lote: fila, activo }) => (
                <tr key={fila.id}>
                  <td className="data">{oNoDisponible(fila.nombre, (nombre) => nombre)}</td>
                  <td className="data">{oNoDisponible(fila.fechaIngreso, formatoFecha)}</td>
                  <td className="r data">{oNoDisponible(fila.poblacionInicial, formatoEntero)}</td>
                  <td className="r data">{oNoDisponible(fila.costoTotal, formatoPesos)}</td>
                  <td>
                    {activo ? (
                      <span className="chip ok">
                        <Icono nombre="check" />
                        Activo
                      </span>
                    ) : (
                      <span className="mut">Anterior</span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </>
  )
}
