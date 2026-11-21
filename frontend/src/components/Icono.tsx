// Iconos provisionales de los mockups: la marca aún no define los iconos de interfaz.
type Figura = string | readonly [cx: number, cy: number, r: number]

const FIGURAS = {
  galpon: ['M3 10l9-6 9 6', 'M5 9v11h14V9', 'M10 20v-6h4v6'],
  lotes: ['M12 3l9 5-9 5-9-5z', 'M3 13l9 5 9-5'],
  alerta: ['M6 17v-6a6 6 0 0112 0v6l1.5 2h-15z', 'M10 21h4'],
  llave: [[16, 8, 3.5], 'M13.5 10.5L5 19'],
  usuario: [[12, 8, 4], 'M4 21c1-4 4-6 8-6s7 2 8 6'],
  disponible: [[12, 12, 9], 'M8 12.5l3 3 5-6'],
  productivo: ['M3 12h4l3-7 4 14 3-7h4'],
  cosecha: ['M3 7h11v9H3z', 'M14 10h4l3 3v3h-7z', [7, 18, 1.8], [17, 18, 1.8]],
  vaciado: ['M12 3c4 5 6 8 6 11a6 6 0 01-12 0c0-3 2-6 6-11z'],
  aislamiento: ['M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6z', 'M12 8v4', 'M12 15h.01'],
  buscar: [[11, 11, 6], 'M20 20l-4.5-4.5'],
  mas: ['M12 5v14M5 12h14'],
  izquierda: ['M15 6l-6 6 6 6'],
  derecha: ['M9 6l6 6-6 6'],
  abajo: ['M6 9l6 6 6-6'],
  check: ['M5 12.5l4 4 10-10'],
  advertencia: ['M12 4l9 16H3z', 'M12 10v4', 'M12 17h.01'],
  info: [[12, 12, 9], 'M12 11v5', 'M12 8h.01'],
  reintentar: ['M20 12a8 8 0 11-2.3-5.7', 'M20 4v5h-5'],
  lapiz: ['M4 20h4L19 9l-4-4L4 16z'],
} as const satisfies Record<string, readonly Figura[]>

export type NombreIcono = keyof typeof FIGURAS

export function Icono({ nombre, tamano }: { nombre: NombreIcono; tamano?: 'sm' | 'lg' }) {
  const figuras: readonly Figura[] = FIGURAS[nombre]
  return (
    <svg className={tamano ? `ic ${tamano}` : 'ic'} viewBox="0 0 24 24" aria-hidden="true">
      {figuras.map((figura, i) =>
        typeof figura === 'string' ? (
          <path key={i} d={figura} />
        ) : (
          <circle key={i} cx={figura[0]} cy={figura[1]} r={figura[2]} />
        ),
      )}
    </svg>
  )
}

export function Isotipo() {
  return (
    <svg width="40" height="40" viewBox="0 0 96 96" role="img" aria-label="AviControl">
      <rect className="mk-bg" width="96" height="96" rx="24" />
      <ellipse className="mk-egg" cx="48" cy="50" rx="22" ry="28" />
      <circle className="mk-yolk" cx="48" cy="54" r="9" />
    </svg>
  )
}
