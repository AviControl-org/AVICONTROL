/** Cifra destacada; `clave` marca el dato principal de la pantalla en ámbar. */
export function StatTile({ etiqueta, valor, unidad, clave }: { etiqueta: string; valor: string; unidad: string; clave?: boolean }) {
  return (
    <div className={clave ? 'stat key' : 'stat'}>
      <span className="cap">{etiqueta}</span>
      <span className="v">{valor}</span>
      <span className="cap">{unidad}</span>
    </div>
  )
}
