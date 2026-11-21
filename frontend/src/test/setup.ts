import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterAll, afterEach, beforeAll } from 'vitest'
import { servidor } from './servidor'

// El fetch de Node no acepta rutas relativas como "/api/v1/galpones": se resuelven contra el origen de jsdom.
const fetchOriginal = globalThis.fetch
globalThis.fetch = (entrada, opciones) =>
  fetchOriginal(typeof entrada === 'string' ? new URL(entrada, window.location.origin) : entrada, opciones)

beforeAll(() => servidor.listen({ onUnhandledFrame: 'error' }))
afterEach(() => {
  cleanup()
  servidor.resetHandlers()
  localStorage.clear()
})
afterAll(() => servidor.close())
// jsdom no implementa la apertura modal ni el cierre de HTMLDialogElement.
if (!HTMLDialogElement.prototype.showModal) {
  HTMLDialogElement.prototype.showModal = function () { this.open = true }
  HTMLDialogElement.prototype.close = function () { this.open = false }
}
