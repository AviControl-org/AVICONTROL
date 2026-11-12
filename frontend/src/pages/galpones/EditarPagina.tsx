import { ConfirmDialog, type Confirmacion } from '../../components/ConfirmDialog'
import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { obtenerGalpon, type GalponDetalleResponse } from '../../api/galpones'
import { editarGalpon } from '../../api/operaciones'
import { Field } from '../../components/Field'
import type { AvisoListado } from '../aviso'
import { enteroPositivo, erroresDelServidor, nombreValido } from '../validacion'

export function EditarPagina() {
  const { galponId = '' } = useParams()
  const detalle = useQuery({ queryKey: ['galpon', galponId], queryFn: () => obtenerGalpon(galponId) })
  if (detalle.error) return <p role="alert">{detalle.error.message} <button onClick={() => detalle.refetch()}>Reintentar</button></p>
  return detalle.data ? <Formulario key={galponId} galpon={detalle.data}/> : <p>Cargando galpón…</p>
}

function Formulario({ galpon }: { galpon: GalponDetalleResponse }) {
  const [nombre, setNombre] = useState(galpon.nombre)
  const [capacidad, setCapacidad] = useState(String(galpon.capacidad))
  const [solicitud, solicitar] = useState<Confirmacion | null>(null)
  const cliente = useQueryClient()
  const navegar = useNavigate()
  const detalle = '/galpones/' + galpon.id
  const permiteCapacidad = galpon.estado === 'MANTENIMIENTO'
  const numero = enteroPositivo(capacidad)
  const cambios = {
    ...(nombre.trim() !== galpon.nombre && { nombre: nombre.trim() }),
    ...(permiteCapacidad && numero && numero !== galpon.capacidad ? { capacidad: numero } : {}),
  }
  const guardar = useMutation({
    mutationFn: () => editarGalpon(galpon.id, cambios),
    onSuccess: async (editado) => {
      await cliente.invalidateQueries()
      const aviso: AvisoListado = {
        titulo: 'Galpón actualizado',
        detalle: 'Se guardaron los cambios de ' + editado.nombre + '.',
        resaltarId: editado.id,
      }
      navegar('/galpones', { state: aviso })
    },
  })
  const errores = erroresDelServidor(guardar.error)
  return <section className="col"><h1>Editar galpón</h1>
    <form className="card lg col" onSubmit={e => {
      e.preventDefault()
      solicitar({ titulo: '¿Estás seguro de guardar los cambios?', detalle: 'Se actualizarán los datos de ' + galpon.nombre + '.', accion: 'Guardar cambios', confirmar: () => guardar.mutate() })
    }}>
      <Field etiqueta="Nombre" error={errores.nombre?.message || (!nombreValido(nombre) ? 'Ingresa un nombre válido de hasta 100 caracteres.' : undefined)}>
        {props => <input {...props} className="input" value={nombre} onChange={e => setNombre(e.target.value)} required maxLength={100}/>}
      </Field>
      <Field etiqueta="Capacidad" error={errores.capacidad?.message || (!numero ? 'Ingresa un entero positivo.' : undefined)} ayuda={permiteCapacidad ? undefined : 'La capacidad se puede editar durante el mantenimiento.'}>
        {props => <input {...props} className="input" inputMode="numeric" disabled={!permiteCapacidad} value={capacidad} onChange={e => setCapacidad(e.target.value)}/>}
      </Field>
      {guardar.error && <p role="alert">{guardar.error.message}</p>}
      <div className="row"><button className="btn p" disabled={guardar.isPending || !nombreValido(nombre) || !numero || !Object.keys(cambios).length}>Guardar cambios</button><Link className="btn" to={detalle}>Cancelar</Link></div>
    </form>{solicitud && <ConfirmDialog solicitud={solicitud} cerrar={() => solicitar(null)} />}
  </section>
}

