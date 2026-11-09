import type { ReactNode } from 'react'

/** Estado vacío con el motivo de la marca: la bandeja de huevos. */
export function EmptyState({ titulo, children }: { titulo: string; children?: ReactNode }) {
  return (
    <div className="empty">
      <div className="tray" aria-hidden="true">
        {Array.from({ length: 8 }, (_, i) => (
          <i key={i} className={i === 6 ? 'hot' : undefined} />
        ))}
      </div>
      <div className="col t">
        <p className="b6">{titulo}</p>
        {children}
      </div>
    </div>
  )
}
