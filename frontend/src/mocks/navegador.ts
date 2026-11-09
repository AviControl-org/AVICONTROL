import { setupWorker } from 'msw/browser'
import { manejadores } from './manejadores'

export const worker = setupWorker(...manejadores)
