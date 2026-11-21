import type { ReactNode } from 'react'

/** Título de la pantalla, subtítulo de apoyo y acciones. Un solo botón primario por vista. */
export function PageHeader({ titulo, subtitulo, acciones }: { titulo: string; subtitulo: string; acciones?: ReactNode }) {
  return (
    <div className="head">
      <div>
        <h1 className="h1">{titulo}</h1>
        <p>{subtitulo}</p>
      </div>
      {acciones && <div className="row">{acciones}</div>}
    </div>
  )
}
