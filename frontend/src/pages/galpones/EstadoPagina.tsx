import { ConfirmDialog, type Confirmacion } from '../../components/ConfirmDialog'
import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import type { AvisoListado } from '../aviso'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { cambiarEstado, verHistorial, verTransiciones } from '../../api/operaciones'
import { ESTADO_GALPON } from '../../components/estadoGalpon'
import { StatusChip } from '../../components/StatusChip'
import { Pager } from '../../components/Pager'
import type { EstadoGalpon } from '../../api/tipos'
import { obtenerGalpon } from '../../api/galpones'

const SIGUIENTE_PASO: Partial<Record<EstadoGalpon, string>> = {
  DISPONIBLE: 'Al registrar un lote, el galpón pasa a Productivo. Si se atiende una alerta de mantenimiento, pasa a Mantenimiento.',
  EN_COSECHA: 'El galpón pasará a Vaciado sanitario cuando el Módulo 2 informe que terminó la cosecha.',
  VACIADO_SANITARIO: 'El galpón volverá automáticamente a Disponible cuando se cumpla el período de limpieza.',
}

const ORIGEN: Record<string, string> = {
  ADMINISTRADOR: 'Administrador', REGISTRO_LOTE: 'Registro de lote',
  ALERTA_SANITARIA_MODULO_2: 'Alerta sanitaria del Módulo 2',
  ALERTA_VACIADO_SANITARIO: 'Alerta de vaciado sanitario', PROCESO_AUTOMATICO: 'Proceso automático',
}

export function EstadoPagina() {
  const { galponId = '' } = useParams()
  const [solicitud, solicitar] = useState<Confirmacion | null>(null)
  const cliente = useQueryClient()
  const navegar = useNavigate()
  const [pagina, setPagina] = useState(1)
  const detalle = useQuery({ queryKey: ['galpon', galponId], queryFn: () => obtenerGalpon(galponId) })
  const transiciones = useQuery({ queryKey: ['galpon', galponId, 'transiciones'], queryFn: () => verTransiciones(galponId) })
  const historial = useQuery({ queryKey: ['galpon', galponId, 'historial', pagina], queryFn: () => verHistorial(galponId, pagina) })
  const cambio = useMutation({
    mutationFn: (par: { desde: EstadoGalpon; hasta: EstadoGalpon }) => cambiarEstado(galponId, par.desde, par.hasta),
    onSuccess: async (_, { hasta }) => {
      await cliente.invalidateQueries()
      const aviso: AvisoListado = {
        titulo: 'Estado actualizado',
        detalle: 'El galpón pasó a ' + ESTADO_GALPON[hasta].texto + '.',
        resaltarId: galponId,
      }
      navegar('/galpones', { state: aviso })
    },
    onError: () => cliente.invalidateQueries(),
  })
  return <section className="col">
    <Link to={'/galpones/' + galponId}>Volver al detalle</Link>
    <h1 className="h1">{detalle.data?.nombre ?? 'Galpón'} · Estado e historial</h1>
    <div className="card lg col panel-estado">
      {transiciones.isPending && <p>Cargando transiciones…</p>}
      {transiciones.error && <p role="alert">{transiciones.error.message} <button onClick={() => transiciones.refetch()}>Reintentar</button></p>}
      {transiciones.data && <>
        <h2>Estado actual</h2><StatusChip estado={transiciones.data.estadoActual} />
        {transiciones.data.destinosPermitidos.length === 0 && <p>{SIGUIENTE_PASO[transiciones.data.estadoActual] ?? 'Este estado no admite cambios manuales.'}</p>}
        {transiciones.data.destinosPermitidos.map(hasta => <button className="btn p" key={hasta} disabled={cambio.isPending} onClick={() => {
          const desde = transiciones.data.estadoActual
          solicitar({ titulo: '¿Estás seguro de actualizar el estado?', detalle: 'El galpón pasará de ' + ESTADO_GALPON[desde].texto + ' a ' + ESTADO_GALPON[hasta].texto + '.', accion: 'Actualizar estado', confirmar: () => cambio.mutate({ desde, hasta }) })
        }}>Pasar a {ESTADO_GALPON[hasta].texto}</button>)}
      </>}
      {cambio.error && <p role="alert">{cambio.error.message}</p>}
    </div>
    <div className="card lg col"><h2>Historial de estados</h2>
      {historial.error && <p role="alert">{historial.error.message} <button onClick={() => historial.refetch()}>Reintentar</button></p>}
      {historial.data && <>
        {!historial.data.contenido.length && <p>Sin cambios registrados.</p>}
        {historial.data.contenido.map(c => <div key={c.id} className="cambio-estado">
          <div className="row wrap"><StatusChip estado={c.estadoAnterior}/> → <StatusChip estado={c.estadoNuevo}/></div>
          <div className="cap"><time dateTime={c.fechaHora}>{new Date(c.fechaHora).toLocaleString('es-CO', { timeZone: 'America/Bogota' })}</time> · {ORIGEN[c.origen] ?? c.origen}</div>
        </div>)}
        {historial.data.totalPaginas > 1 ? <Pager {...historial.data} recurso="cambios" onCambiar={setPagina}/>
          : historial.data.totalElementos > 0 && <span className="cap">{historial.data.totalElementos} {historial.data.totalElementos === 1 ? 'cambio' : 'cambios'}</span>}
      </>}
    </div>
  {solicitud && <ConfirmDialog solicitud={solicitud} cerrar={() => solicitar(null)} />}
  </section>
}

