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
import { VistaRol } from './VistaRol'

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
                element: <VistaRol rol="ADMINISTRADOR"><EstadoPagina/></VistaRol>,
              },
              {
                path: 'editar',
                handle: { miga: 'Editar' },
                element: <VistaRol rol="ADMINISTRADOR"><EditarPagina/></VistaRol>,
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
          <VistaRol rol="ADMINISTRADOR"><AlertasPagina/></VistaRol>
        ),
      },
      {
        path: 'alertas-mantenimiento/generar',
        handle: { miga: 'Alerta de mantenimiento' },
        element: (
          <VistaRol rol="TECNICO_INFRAESTRUCTURA"><GenerarAlertaPagina/></VistaRol>
        ),
      },
      { path: '*', element: <Navigate to="/galpones" replace /> },
    ],
  },
])


