import { ConfirmDialog, type Confirmacion } from '../../components/ConfirmDialog'
import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { listarTodosLosGalpones } from '../../api/galpones'
import { generarAlerta } from '../../api/operaciones'
import { TECNICO_ID_DEMO } from '../../app/rol-contexto'
import { erroresDelServidor } from '../validacion'
import { Field } from '../../components/Field'

export function GenerarAlertaPagina() {
  const [solicitud, solicitar] = useState<Confirmacion | null>(null)
  const cliente = useQueryClient()
  const disponibles = useQuery({ queryKey: ['galpones', 'disponibles'], queryFn: () => listarTodosLosGalpones('DISPONIBLE') })
  const [galponId, seleccionar] = useState('')
  const [descripcion, describir] = useState('')
  const [descripcionTocada, marcarDescripcionTocada] = useState(false)
  const descripcionVacia = !descripcion.trim()
  const [tipo, setTipo] = useState('')
  const [severidad, setSeveridad] = useState('')
  const alta = useMutation({
    mutationFn: () => generarAlerta({ galponId, descripcion: descripcion.trim(), tecnicoId: TECNICO_ID_DEMO,
      ...(tipo.trim() && { tipo: tipo.trim() }), ...(severidad.trim() && { severidad: severidad.trim() }) }),
    onSuccess: () => {
      marcarDescripcionTocada(false)
      seleccionar(''); describir(''); setTipo(''); setSeveridad('')
      return cliente.invalidateQueries({ queryKey: ['alertas'] })
    },
  })
  const errores = erroresDelServidor(alta.error)
  return <section className="col"><h1>Generar alerta de mantenimiento</h1>
    {alta.isSuccess && <p role="status">Alerta generada. Queda pendiente de atención.</p>}
    <form className="card lg col" onSubmit={e => {
      e.preventDefault()
      if (descripcionVacia) {
        marcarDescripcionTocada(true)
        return
      }
      solicitar({ titulo: '¿Generar alerta de mantenimiento?', detalle: 'La alerta quedará pendiente de atención. El galpón seguirá disponible.', accion: 'Generar alerta', confirmar: () => alta.mutate() })
    }}>
      <Field etiqueta="Galpón disponible" error={errores.galponId?.message}>{p => <select {...p} className="input" required value={galponId} onChange={e => seleccionar(e.target.value)}>
        <option value="">Selecciona un galpón</option>{disponibles.data?.map(g => <option key={g.id} value={g.id}>{g.nombre}</option>)}
      </select>}</Field>
      {disponibles.error && <p role="alert">{disponibles.error.message} <button type="button" onClick={() => disponibles.refetch()}>Reintentar</button></p>}
      {disponibles.data?.length === 0 && <p>No hay galpones disponibles.</p>}
      <Field etiqueta="Descripción" error={descripcionTocada && descripcionVacia ? 'Escribe una descripción del problema' : errores.descripcion?.message}>
        {p => <textarea {...p} className="input" required maxLength={500} value={descripcion}
          onBlur={() => marcarDescripcionTocada(true)}
          onChange={e => {
            describir(e.target.value)
            marcarDescripcionTocada(true)
          }}/>}
      </Field>
      <Field etiqueta="Tipo" opcional>{p => <input {...p} className="input" value={tipo} onChange={e => setTipo(e.target.value)}/>}</Field>
      <Field etiqueta="Severidad" opcional>{p => <input {...p} className="input" value={severidad} onChange={e => setSeveridad(e.target.value)}/>}</Field>
      {alta.error && <p role="alert">{alta.error.message}</p>}
      <button className="btn p" disabled={alta.isPending || !galponId || descripcionVacia}>Generar alerta</button>
    </form>{solicitud && <ConfirmDialog solicitud={solicitud} cerrar={() => solicitar(null)} />}
  </section>
}

