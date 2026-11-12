import { ConfirmDialog, type Confirmacion } from '../../components/ConfirmDialog'
import { useState } from 'react'
import { Link } from 'react-router'
import { useQueries, useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { obtenerGalpon } from '../../api/galpones'
import { atenderAlerta, listarAlertas } from '../../api/operaciones'
import { Pager } from '../../components/Pager'

export function AlertasPagina() {
  const [estado, setEstado] = useState('PENDIENTE')
  const [pagina, setPagina] = useState(1)
  const [solicitud, solicitar] = useState<Confirmacion | null>(null)
  const cliente = useQueryClient()
  const alertas = useQuery({ queryKey: ['alertas', estado, pagina], queryFn: () => listarAlertas(estado, pagina) })
  const ids = [...new Set(alertas.data?.contenido.map(a => a.galponId) ?? [])]
  const detalles = useQueries({ queries: ids.map(id => ({
    queryKey: ['galpon', id], queryFn: () => obtenerGalpon(id),
  })) })
  const nombres = new Map(detalles.flatMap(d => d.data ? [[d.data.id, d.data.nombre] as const] : []))
  const atender = useMutation({ mutationFn: atenderAlerta, onSuccess: () => { setPagina(1); return cliente.invalidateQueries() } })
  return <section className="col"><h1>Alertas de mantenimiento</h1>
    <label>Estado de la alerta <select className="input" value={estado} onChange={e => { setEstado(e.target.value); setPagina(1) }}>
      <option value="PENDIENTE">Pendientes</option><option value="ATENDIDA">Atendidas</option><option value="">Todas</option>
    </select></label>
    {alertas.isPending && <p>Cargando alertas…</p>}
    {alertas.error && <p role="alert">{alertas.error.message} <button onClick={() => alertas.refetch()}>Reintentar</button></p>}
    {atender.error && <p role="alert">{atender.error.message}</p>}
    {atender.isSuccess && <p role="status">Alerta atendida. El galpón pasó a mantenimiento.</p>}
    {alertas.data?.contenido.map(a => <article key={a.id} className="card lg col">
      <h2 className="h3">{nombres.get(a.galponId) ?? 'Galpón ' + a.galponId}</h2>
      <p>{a.descripcion}</p><span>{a.estado === 'PENDIENTE' ? 'Pendiente' : 'Atendida'} · {a.tecnicoId} · {new Date(a.fechaHora).toLocaleString('es-CO')}</span>
      {a.tipo && <span>Tipo: {a.tipo}</span>}{a.severidad && <span>Severidad: {a.severidad}</span>}
      <div className="row"><Link className="btn" to={'/galpones/' + a.galponId}>Ver galpón</Link>
      {a.estado === 'PENDIENTE' && <button className="btn p" disabled={atender.isPending} onClick={() => {
        solicitar({ titulo: '¿Estás seguro de actualizar el estado?', detalle: 'La alerta «' + a.descripcion + '» quedará atendida y ' + (nombres.get(a.galponId) ?? 'el galpón ' + a.galponId) + ' pasará a Mantenimiento.', accion: 'Atender alerta', confirmar: () => atender.mutate(a.id) })
      }}>Atender alerta</button>}</div>
    </article>)}
    {alertas.data && <>{!alertas.data.contenido.length && <p>No hay alertas para este filtro.</p>}<Pager {...alertas.data} recurso="alertas" onCambiar={setPagina}/></>}
  {solicitud && <ConfirmDialog solicitud={solicitud} cerrar={() => solicitar(null)} />}
  </section>
}

