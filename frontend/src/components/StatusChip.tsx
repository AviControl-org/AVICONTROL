import type { EstadoGalpon } from '../api/tipos'
import { ESTADO_GALPON } from './estadoGalpon'
import { Icono } from './Icono'

/** Estado del galpón con icono y palabra, nunca solo con color. */
export function StatusChip({ estado }: { estado: EstadoGalpon }) {
  const { texto, tono, icono } = ESTADO_GALPON[estado]
  return (
    <span className={`chip ${tono}`} data-estado={estado}>
      <Icono nombre={icono} />
      {texto}
    </span>
  )
}
