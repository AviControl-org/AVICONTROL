import type { ReactNode } from 'react'
import { Icono, type NombreIcono } from './Icono'

type TonoBanner = 'ok' | 'warn' | 'crit' | 'info'

const ICONO: Record<TonoBanner, NombreIcono> = {
  ok: 'check',
  warn: 'advertencia',
  crit: 'advertencia',
  info: 'info',
}

interface BannerProps {
  tono: TonoBanner
  titulo: string
  children?: ReactNode
  accion?: ReactNode
}

/** Mensaje de resultado (ok), atención (warn), alarma crítica (crit) o información (info). */
export function Banner({ tono, titulo, children, accion }: BannerProps) {
  return (
    <section className={`banner ${tono}`} role={tono === 'warn' || tono === 'crit' ? 'alert' : 'status'}>
      <Icono nombre={ICONO[tono]} tamano="lg" />
      <div className="grow">
        <span className="bt">{titulo}</span>
        {children && <span>{children}</span>}
      </div>
      {accion}
    </section>
  )
}
