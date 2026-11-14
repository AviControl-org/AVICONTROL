import type { Rol } from './rol-contexto'

/**
 * Matriz de permisos de la interfaz, derivada de los actores de `docs/Casos de uso.md`.
 *
 * Un solo lugar dice quién puede qué, igual que `TransicionesPermitidas` concentra en el backend los orígenes
 * autorizados de cada cambio de estado. Antes esta información estaba repartida en las redirecciones del router,
 * así que no se podía revisar ni probar como una regla.
 *
 * Esto **no es control de acceso**: la API no valida identidad ni rol porque la autenticación está fuera del
 * alcance del módulo (P-01). Es la regla de negocio sobre qué ofrece la interfaz a cada actor, escrita en un
 * lugar donde se puede verificar, y lista para conectarse a la autorización real cuando exista.
 */
export type Permiso =
  | 'CONSULTAR_GALPON_LOTE'
  | 'REGISTRAR_GALPON'
  | 'EDITAR_GALPON'
  | 'REGISTRAR_LOTE'
  | 'ACTUALIZAR_ESTADO'
  | 'GENERAR_ALERTA_MANTENIMIENTO'
  | 'ATENDER_ALERTA_MANTENIMIENTO'

/**
 * - **Administrador**: «es el único que puede registrar un galpón, editar un galpón, registrar un lote y
 *   actualizar el estado del galpón», y atiende las alertas de mantenimiento.
 * - **Técnico de infraestructura**: «generar una alerta al administrador para avisarle que se encontraron
 *   fallas en la infraestructura del galpón». Necesita consultar para elegir el galpón en el formulario.
 * - **Operario de granja**: «puede consultar el listado de galpones y el detalle de cada galpón con su lote
 *   activo. No puede registrar, editar ni cambiar el estado de ningún galpón».
 */
export const PERMISOS: Record<Rol, readonly Permiso[]> = {
  ADMINISTRADOR: [
    'CONSULTAR_GALPON_LOTE',
    'REGISTRAR_GALPON',
    'EDITAR_GALPON',
    'REGISTRAR_LOTE',
    'ACTUALIZAR_ESTADO',
    'ATENDER_ALERTA_MANTENIMIENTO',
  ],
  TECNICO_INFRAESTRUCTURA: ['CONSULTAR_GALPON_LOTE', 'GENERAR_ALERTA_MANTENIMIENTO'],
  OPERARIO: ['CONSULTAR_GALPON_LOTE'],
}

export function puede(rol: Rol, permiso: Permiso): boolean {
  return PERMISOS[rol].includes(permiso)
}

/**
 * Pantalla a la que se envía cada rol cuando pide una ruta que no le corresponde, y destino del enlace raíz.
 *
 * Cada rol necesita un destino propio al que sí tenga acceso: si dos roles se redirigen entre sí, el navegador
 * entra en un bucle de redirecciones.
 */
export const RUTA_INICIAL: Record<Rol, string> = {
  ADMINISTRADOR: '/galpones',
  TECNICO_INFRAESTRUCTURA: '/alertas-mantenimiento/generar',
  OPERARIO: '/galpones',
}
