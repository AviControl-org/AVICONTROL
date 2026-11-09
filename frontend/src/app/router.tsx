import { createBrowserRouter, Navigate } from 'react-router'
import { AppShell } from '../components/AppShell'
import { GalponDetallePagina } from '../pages/galpones/GalponDetallePagina'
import { GalponesPagina } from '../pages/galpones/GalponesPagina'
import { RegistrarGalponPagina } from '../pages/galpones/RegistrarGalponPagina'
import { LotesPagina } from '../pages/lotes/LotesPagina'
import { RegistrarLotePagina } from '../pages/lotes/RegistrarLotePagina'
import { PaginaPendiente } from '../pages/PaginaPendiente'

// Una ruta por pantalla de los mockups. La fase es la de docs/plan-general/plan-modulo-1.md.
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
            element: <RegistrarGalponPagina />,
          },
          {
            path: ':galponId',
            handle: { miga: 'Detalle' },
            children: [
              { index: true, element: <GalponDetallePagina /> },
              {
                path: 'estado',
                handle: { miga: 'Actualizar estado' },
                element: <PaginaPendiente titulo="Actualizar estado" spec="Actualizar estado (US4)" fase="Phase 6" />,
              },
              {
                path: 'editar',
                handle: { miga: 'Editar' },
                element: <PaginaPendiente titulo="Editar galpón" spec="Editar galpón (US9)" fase="Phase 11" />,
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
            element: <RegistrarLotePagina />,
          },
        ],
      },
      {
        path: 'alertas',
        handle: { miga: 'Alertas' },
        element: (
          <PaginaPendiente
            titulo="Alertas de mantenimiento"
            spec="Generar alerta de mantenimiento (US8)"
            fase="Phase 10"
          />
        ),
      },
      {
        path: 'alertas-mantenimiento/generar',
        handle: { miga: 'Alerta de mantenimiento' },
        element: (
          <PaginaPendiente
            titulo="Generar alerta de mantenimiento"
            spec="Generar alerta de mantenimiento (US8)"
            fase="Phase 10"
          />
        ),
      },
      { path: '*', element: <Navigate to="/galpones" replace /> },
    ],
  },
])
