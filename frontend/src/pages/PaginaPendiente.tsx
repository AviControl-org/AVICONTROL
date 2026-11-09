/** Marcador de una pantalla que aún no se implementa; indica el spec y la fase de plan-modulo-1.md. */
export function PaginaPendiente({ titulo, spec, fase }: { titulo: string; spec: string; fase: string }) {
  return (
    <>
      <div className="head">
        <div>
          <h1 className="h1">{titulo}</h1>
          <p>Pantalla pendiente de implementar.</p>
        </div>
      </div>
      <section className="banner info">
        <div className="grow">
          <span className="bt">{spec}</span>
          <span>{fase}</span>
        </div>
      </section>
    </>
  )
}
