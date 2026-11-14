/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'

/** Backend al que el proxy manda `/api` cuando no se configura otro. */
const BACKEND_POR_DEFECTO = 'http://localhost:8080'

// `VITE_BACKEND_URL` permite apuntar a otro puerto sin tocar este archivo, para cuando el 8080 de la
// maquina ya esta ocupado por otra aplicacion. Se define en .env.local (ignorado por git) o en el entorno.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
    plugins: [react()],
    server: {
      proxy: {
        '/api': env.VITE_BACKEND_URL || BACKEND_POR_DEFECTO,
      },
    },
    test: {
      environment: 'jsdom',
      setupFiles: ['./src/test/setup.ts'],
    },
  }
})
