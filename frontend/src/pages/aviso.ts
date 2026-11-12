/** Resultado que una pantalla deja al volver a un listado: banner de éxito y fila resaltada. */
export interface AvisoListado {
  titulo: string
  detalle: string
  resaltarId?: string
}

/** Localiza el UUID con el mismo listado y orden que se muestran tras el alta. */
export async function rutaDelRegistro(
  ruta: string,
  aviso: AvisoListado & { resaltarId: string },
  listar: (pagina: number) => Promise<{ contenido: { id: string }[]; totalPaginas: number }>,
) {
  try {
    let pagina = 1
    let total: number
    do {
      const datos = await listar(pagina)
      if (datos.contenido.some(fila => fila.id === aviso.resaltarId)) {
        return { ruta: pagina === 1 ? ruta : ruta + '?page=' + pagina, aviso }
      }
      total = datos.totalPaginas
      pagina++
    } while (pagina <= total)
  } catch {
    // La escritura ya tuvo éxito, aun si la consulta posterior falla.
  }
  return { ruta, aviso: { ...aviso, detalle: aviso.detalle + ' No se pudo localizar la fila en el listado.' } }
}
