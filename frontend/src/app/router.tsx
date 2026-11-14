import { createBrowserRouter, Navigate } from 'react-router'
import { AppShell } from '../components/AppShell'
import { GalponDetallePagina } from '../pages/galpones/GalponDetallePagina'
import { GalponesPagina } from '../pages/galpones/GalponesPagina'
import { RegistrarGalponPagina } from '../pages/galpones/RegistrarGalponPagina'
import { LotesPagina } from '../pages/lotes/LotesPagina'
import { RegistrarLotePagina } from '../pages/lotes/RegistrarLotePagina'
import { EstadoPagina } from '../pages/galpones/EstadoPagina'
import { EditarPagina } from '../pages/galpones/EditarPagina'
import { AlertasPagina } from '../pages/mantenimiento/AlertasPagina'
import { GenerarAlertaPagina } from '../pages/mantenimiento/GenerarAlertaPagina'
import { ConPermiso } from './ConPermiso'

// Una ruta por pantalla de los mockups. La fase es la de docs/plan-general/plan-modulo-1.md.
// Cada pantalla que escribe datos declara el permiso que necesita; la matriz esta en permisos.ts.
// Las de consulta no llevan guarda: los tres actores pueden consultar galpones y lotes.
export const router = createBrowserRouter([
  {
    element: <AppShell />,
    children: [
      { index: true, element: <Navigate to="/galpones" replace /> },
      {
        path: 'galpones',
        handle: { miga: 'Galpones' },
        children: [
          { index: true, element: <GalponesPagina /> },
          {
            path: 'registrar',
            handle: { miga: 'Registrar galpón' },
            element: (
              <ConPermiso permiso="REGISTRAR_GALPON">
                <RegistrarGalponPagina />
              </ConPermiso>
            ),
          },
          {
            path: ':galponId',
            handle: { miga: 'Detalle' },
            children: [
              { index: true, element: <GalponDetallePagina /> },
              {
                path: 'estado',
                handle: { miga: 'Actualizar estado' },
                element: (
                  <ConPermiso permiso="ACTUALIZAR_ESTADO">
                    <EstadoPagina />
                  </ConPermiso>
                ),
              },
              {
                path: 'editar',
                handle: { miga: 'Editar' },
                element: (
                  <ConPermiso permiso="EDITAR_GALPON">
                    <EditarPagina />
                  </ConPermiso>
                ),
              },
            ],
          },
        ],
      },
      {
        path: 'lotes',
        handle: { miga: 'Lotes' },
        children: [
          { index: true, element: <LotesPagina /> },
          {
            path: 'registrar',
            handle: { miga: 'Registrar lote' },
            element: (
              <ConPermiso permiso="REGISTRAR_LOTE">
                <RegistrarLotePagina />
              </ConPermiso>
            ),
          },
        ],
      },
      {
        path: 'alertas',
        handle: { miga: 'Alertas' },
        element: (
          <ConPermiso permiso="ATENDER_ALERTA_MANTENIMIENTO">
            <AlertasPagina />
          </ConPermiso>
        ),
      },
      {
        path: 'alertas-mantenimiento/generar',
        handle: { miga: 'Alerta de mantenimiento' },
        element: (
          <ConPermiso permiso="GENERAR_ALERTA_MANTENIMIENTO">
            <GenerarAlertaPagina />
          </ConPermiso>
        ),
      },
      { path: '*', element: <Navigate to="/galpones" replace /> },
    ],
  },
])
