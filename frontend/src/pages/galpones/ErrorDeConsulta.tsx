import { ErrorApi, ErrorDeConexion } from '../../api/cliente'
import { Banner } from '../../components/Banner'
import { Icono } from '../../components/Icono'

interface ErrorDeConsultaProps {
  error: Error
  /** Lo que no se pudo cargar, por ejemplo "los galpones". */
  recurso: string
  /** Búsqueda, filtro y orden que se conservan (FR-019); los nulos no se muestran. */
  criterios?: (string | null)[]
  onReintentar: () => void
  reintentando: boolean
}

/** Error durante la consulta: mensaje específico, criterios conservados y reintento (FR-018 a FR-020). */
export function ErrorDeConsulta({ error, recurso, criterios = [], onReintentar, reintentando }: ErrorDeConsultaProps) {
  const detalle =
    error instanceof ErrorDeConexion
      ? 'Se perdió la conexión con el servidor.'
      : error instanceof ErrorApi
        ? error.problema.detail
        : 'Ocurrió un error inesperado.'
  const erroresDeCampo = error instanceof ErrorApi ? (error.problema.fieldErrors ?? []) : []
  const visibles = criterios.filter((criterio): criterio is string => Boolean(criterio))

  return (
    <div className="col t">
      <Banner
        tono="warn"
        titulo={`No pudimos cargar ${recurso}`}
        accion={
          <button type="button" className="btn p" onClick={onReintentar} disabled={reintentando}>
            <Icono nombre="reintentar" />
            Reintentar
          </button>
        }
      >
        {detalle}
        {erroresDeCampo.map((campo) => (
          <span key={campo.field} className="help" style={{ display: 'block', color: 'inherit' }}>
            {campo.field}: {campo.message}
          </span>
        ))}
      </Banner>
      {visibles.length > 0 && (
        <div className="row wrap">
          <span className="cap">Criterios conservados</span>
          {visibles.map((criterio) => (
            <span key={criterio} className="origin">
              {criterio}
            </span>
          ))}
        </div>
      )}
    </div>
  )
}
