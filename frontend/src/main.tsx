import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { App } from './app/App'
import './styles/tokens.css'
import './styles/base.css'

async function iniciarApiSimulada() {
  if (import.meta.env.VITE_API_SIMULADA !== 'true') {
    return
  }
  const { worker } = await import('./mocks/navegador')
  await worker.start({ onUnhandledFrame: 'bypass' })
}

iniciarApiSimulada().then(() => {
  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <App />
    </StrictMode>,
  )
})
