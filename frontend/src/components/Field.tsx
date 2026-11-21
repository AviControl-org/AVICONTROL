import { useId, type ReactNode } from 'react'
import { Icono } from './Icono'

interface FieldProps {
  etiqueta: string
  /** Ayuda permanente bajo el campo. */
  ayuda?: string
  /** Mensaje de error; si existe, el campo se marca inválido y se anuncia. */
  error?: string
  /** Unidad visible dentro del campo, por ejemplo "pollos". */
  unidad?: string
  opcional?: boolean
  /** Recibe el id y los atributos de accesibilidad que debe llevar el control. */
  children: (props: { id: string; 'aria-invalid': boolean; 'aria-describedby': string }) => ReactNode
}

/** Campo de formulario de los mockups: etiqueta, control, error y ayuda. */
export function Field({ etiqueta, ayuda, error, unidad, opcional, children }: FieldProps) {
  const id = useId()
  const idError = `${id}-error`
  const idAyuda = `${id}-ayuda`
  const descritoPor = [error ? idError : null, ayuda ? idAyuda : null].filter(Boolean).join(' ')

  return (
    <div className="field">
      <label htmlFor={id}>
        {etiqueta}
        {opcional && <span className="opt"> (opcional)</span>}
      </label>
      <div className="inw">
        {children({ id, 'aria-invalid': Boolean(error), 'aria-describedby': descritoPor })}
        {unidad && <span className="unit">{unidad}</span>}
      </div>
      {error && (
        <span className="err" id={idError} role="alert">
          <Icono nombre="advertencia" />
          {error}
        </span>
      )}
      {ayuda && (
        <span className="help" id={idAyuda}>
          {ayuda}
        </span>
      )}
    </div>
  )
}
