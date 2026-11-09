import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { ErrorApi, ErrorDeConexion } from '../../api/cliente'
import { registrarGalpon } from '../../api/galpones'
import { Banner } from '../../components/Banner'
import { Field } from '../../components/Field'
import { PageHeader } from '../../components/PageHeader'
import { StatusChip } from '../../components/StatusChip'
import { enteroPositivo, erroresDelServidor, nombreValido } from '../validacion'
import type { AvisoListado } from './avisoListado'

// Textos fijos de error de docs/spec-registrarGalpon/plan-registrar-galpon.md.
const TEXTO = {
  NOMBRE_INVALIDO:
    'El nombre es obligatorio, tiene máximo 100 caracteres y solo admite letras, números, espacios, guiones y guiones bajos.',
  NOMBRE_DUPLICADO: 'Ya existe un galpón con ese nombre.',
  CAPACIDAD_INVALIDA: 'La capacidad debe ser un número entero mayor que cero.',
}

type Campo = 'nombre' | 'capacidad'

function validar(nombre: string, capacidad: string): Partial<Record<Campo, string>> {
  return {
    ...(nombreValido(nombre) ? {} : { nombre: TEXTO.NOMBRE_INVALIDO }),
    ...(enteroPositivo(capacidad) === null ? { capacidad: TEXTO.CAPACIDAD_INVALIDA } : {}),
  }
}

/** Registrar galpón (US1, FR-001 a FR-010). */
export function RegistrarGalponPagina() {
  const navegar = useNavigate()
  const consultas = useQueryClient()
  const [nombre, setNombre] = useState('')
  const [capacidad, setCapacidad] = useState('')
  const [errores, setErrores] = useState<Partial<Record<Campo, string>>>({})

  const registro = useMutation({
    mutationFn: registrarGalpon,
    onSuccess: async (galpon) => {
      await consultas.invalidateQueries({ queryKey: ['galpones'] })
      const aviso: AvisoListado = {
        titulo: `${galpon.nombre} registrado`,
        detalle: 'Quedó con estado Disponible y con un identificador único.',
        resaltarId: galpon.id,
      }
      navegar('/galpones', { state: aviso })
    },
    onError: (error) => {
      const delServidor = erroresDelServidor(error)
      setErrores({
        ...(delServidor.nombre
          ? { nombre: delServidor.nombre.code === 'DUPLICADO' ? TEXTO.NOMBRE_DUPLICADO : TEXTO.NOMBRE_INVALIDO }
          : {}),
        ...(delServidor.capacidad ? { capacidad: TEXTO.CAPACIDAD_INVALIDA } : {}),
      })
    },
  })

  const enviar = (evento: FormEvent) => {
    evento.preventDefault()
    const encontrados = validar(nombre, capacidad)
    setErrores(encontrados)
    if (Object.keys(encontrados).length > 0) return
    registro.mutate({ nombre: nombre.trim(), capacidad: enteroPositivo(capacidad)! })
  }

  const cantidadErrores = Object.keys(errores).length
  const errorGeneral =
    registro.isError && cantidadErrores === 0
      ? registro.error instanceof ErrorDeConexion
        ? 'Se perdió la conexión con el servidor.'
        : registro.error instanceof ErrorApi
          ? registro.error.problema.detail
          : 'No fue posible completar la operación. Intenta de nuevo.'
      : null

  return (
    <>
      <PageHeader titulo="Registrar galpón" subtitulo="Da de alta un galpón para poder asignarle lotes." />

      {cantidadErrores > 0 && (
        <Banner tono="warn" titulo={`Revisa ${cantidadErrores === 1 ? '1 campo' : `${cantidadErrores} campos`} antes de registrar`}>
          No se creó el galpón. Lo que escribiste se conservó para que lo corrijas.
        </Banner>
      )}
      {errorGeneral && (
        <Banner tono="warn" titulo="No se registró el galpón">
          {errorGeneral} Tus datos siguen en el formulario.
        </Banner>
      )}

      <div className="g21">
        <form className="card lg col" onSubmit={enviar} noValidate>
          <Field
            etiqueta="Nombre del galpón"
            ayuda="Hasta 100 caracteres: letras, números, espacios, guiones y guiones bajos."
            error={errores.nombre}
          >
            {(props) => (
              <input
                {...props}
                className="input"
                type="text"
                autoComplete="off"
                maxLength={150}
                placeholder="Ej. Galpón Norte"
                value={nombre}
                onChange={(evento) => setNombre(evento.target.value)}
              />
            )}
          </Field>
          <Field etiqueta="Capacidad" ayuda="Número entero mayor que cero." error={errores.capacidad} unidad="pollos">
            {(props) => (
              <input
                {...props}
                className="input mono"
                type="text"
                inputMode="numeric"
                autoComplete="off"
                placeholder="Ej. 1500"
                value={capacidad}
                onChange={(evento) => setCapacidad(evento.target.value)}
              />
            )}
          </Field>
          <div className="row">
            <button type="submit" className="btn p" disabled={registro.isPending}>
              {registro.isPending ? 'Registrando…' : 'Registrar galpón'}
            </button>
            <Link className="btn" to="/galpones">
              Cancelar
            </Link>
          </div>
        </form>

        <aside className="card lg">
          <h2 className="h3">Al registrar</h2>
          <dl className="kv">
            <dt>Estado inicial</dt>
            <dd>
              <StatusChip estado="DISPONIBLE" />
            </dd>
            <dt>Identificador</dt>
            <dd className="cap">UUID asignado por el sistema</dd>
            <dt>Después</dt>
            <dd className="cap">Vuelves al listado de galpones</dd>
          </dl>
        </aside>
      </div>
    </>
  )
}
