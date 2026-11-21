import { Icono } from './Icono'

interface PagerProps {
  pagina: number
  tamanio: number
  totalElementos: number
  totalPaginas: number
  /** Texto del recurso en plural, por ejemplo "galpones". */
  recurso: string
  onCambiar: (pagina: number) => void
}

/** Paginación de los listados: la página empieza en 1 (convenciones de contrato). */
export function Pager({ pagina, tamanio, totalElementos, totalPaginas, recurso, onCambiar }: PagerProps) {
  const desde = totalElementos === 0 ? 0 : (pagina - 1) * tamanio + 1
  const hasta = Math.min(pagina * tamanio, totalElementos)
  const paginas = Array.from({ length: totalPaginas }, (_, i) => i + 1)

  return (
    <div className="pager">
      <span className="cap">
        Mostrando {desde} a {hasta} de {totalElementos} {recurso} · {tamanio} por página
      </span>
      <nav className="row" aria-label="Paginación">
        <button type="button" className="btn sm" disabled={pagina <= 1} onClick={() => onCambiar(pagina - 1)}>
          <Icono nombre="izquierda" tamano="sm" />
          Anterior
        </button>
        {paginas.map((numero) => (
          <button
            key={numero}
            type="button"
            className={numero === pagina ? 'btn p sm' : 'btn sm'}
            aria-current={numero === pagina ? 'page' : undefined}
            onClick={() => onCambiar(numero)}
          >
            {numero}
          </button>
        ))}
        <button
          type="button"
          className="btn sm"
          disabled={pagina >= totalPaginas}
          onClick={() => onCambiar(pagina + 1)}
        >
          <Icono nombre="derecha" tamano="sm" />
          Siguiente
        </button>
      </nav>
    </div>
  )
}
