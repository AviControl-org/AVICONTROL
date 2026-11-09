import { setupServer } from 'msw/node'
import { manejadores } from '../mocks/manejadores'

/** Las pruebas usan los mismos manejadores que la API simulada del navegador. */
export const servidor = setupServer(...manejadores)
