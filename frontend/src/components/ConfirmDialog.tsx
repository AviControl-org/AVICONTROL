import { useEffect, useId, useRef } from 'react'
import { Icono } from './Icono'

export interface Confirmacion {
  titulo: string
  detalle: string
  accion: string
  confirmar: () => void
}

export function ConfirmDialog({ solicitud, cerrar }: { solicitud: Confirmacion; cerrar: () => void }) {
  const ref = useRef<HTMLDialogElement>(null)
  const titulo = useId()
  const detalle = useId()

  useEffect(() => {
    const dialogo = ref.current!
    const origen = document.activeElement
    dialogo.showModal()
    dialogo.querySelector('button')?.focus()
    return () => {
      dialogo.close()
      if (origen instanceof HTMLElement && origen.isConnected) origen.focus()
    }
  }, [])

  return (
    <dialog ref={ref} className="confirmacion" aria-labelledby={titulo} aria-describedby={detalle}
      onCancel={evento => { evento.preventDefault(); cerrar() }}>
      <div className="confirmacion-cuerpo">
        <span className="confirmacion-icono"><Icono nombre="info" /></span>
        <h2 className="h2" id={titulo}>{solicitud.titulo}</h2>
        <p id={detalle}>{solicitud.detalle}</p>
      </div>
      <div className="confirmacion-acciones">
        <button type="button" className="btn" onClick={cerrar}>Cancelar</button>
        <button type="button" className="btn p" onClick={() => { cerrar(); solicitud.confirmar() }}>{solicitud.accion}</button>
      </div>
    </dialog>
  )
}
