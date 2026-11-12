import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { ErrorApi, ErrorDeConexion } from '../../api/cliente'
import { listarTodosLosGalpones } from '../../api/galpones'
import { listarLotes, registrarLote } from '../../api/lotes'
import { Banner } from '../../components/Banner'
import { EmptyState } from '../../components/EmptyState'
import { ErrorDeConsulta } from '../../components/ErrorDeConsulta'
import { Field } from '../../components/Field'
import { formatoEntero } from '../../components/formato'
import { Icono } from '../../components/Icono'
import { PageHeader } from '../../components/PageHeader'
import { StatusChip } from '../../components/StatusChip'
import { rutaDelRegistro } from '../aviso'
import { enteroPositivo, erroresDelServidor, hoyEnBogota, nombreValido } from '../validacion'

// Textos fijos de error de docs/spec-registrarLote/plan-registrar-lote.md.
const TEXTO = {
  GALPON_REQUERIDO: 'Debes seleccionar un galpón.',
  GALPON_NO_ENCONTRADO: 'El galpón seleccionado no existe.',
  GALPON_NO_DISPONIBLE: 'El galpón seleccionado no está disponible para recibir un lote.',
  NOMBRE_INVALIDO:
    'El nombre es obligatorio, tiene máximo 100 caracteres y solo admite letras, números, espacios, guiones y guiones bajos.',
  NOMBRE_DUPLICADO: 'Ya existe un lote con ese nombre.',
  FECHA_INGRESO_INVALIDA: 'La fecha de ingreso es obligatoria y debe tener el formato yyyy-MM-dd.',
  FECHA_INGRESO_FUTURA: 'La fecha de ingreso no puede ser posterior a hoy.',
  POBLACION_INICIAL_INVALIDA: 'La población inicial debe ser un número entero mayor que cero.',
  COSTO_TOTAL_INVALIDO: 'El costo total debe ser un número entero mayor que cero, en pesos colombianos.',
}

type Campo = 'galponId' | 'nombre' | 'fechaIngreso' | 'poblacionInicial' | 'costoTotal'
type Errores = Partial<Record<Campo, string>>

interface Formulario {
  galponId: string
  nombre: string
  fechaIngreso: string
  poblacionInicial: string
  costoTotal: string
}

function validar(datos: Formulario): Errores {
  const errores: Errores = {}
  if (!datos.galponId) errores.galponId = TEXTO.GALPON_REQUERIDO
  if (!nombreValido(datos.nombre)) errores.nombre = TEXTO.NOMBRE_INVALIDO
  if (!/^\d{4}-\d{2}-\d{2}$/.test(datos.fechaIngreso)) errores.fechaIngreso = TEXTO.FECHA_INGRESO_INVALIDA
  else if (datos.fechaIngreso > hoyEnBogota()) errores.fechaIngreso = TEXTO.FECHA_INGRESO_FUTURA
  if (enteroPositivo(datos.poblacionInicial) === null) errores.poblacionInicial = TEXTO.POBLACION_INICIAL_INVALIDA
  if (enteroPositivo(datos.costoTotal) === null) errores.costoTotal = TEXTO.COSTO_TOTAL_INVALIDO
  return errores
}

/** Traduce la respuesta de error del servidor a mensajes junto a cada campo. */
function erroresDeRespuesta(error: unknown): Errores {
  const porCampo = erroresDelServidor(error)
  const errores: Errores = {}
  if (porCampo.galponId) errores.galponId = TEXTO.GALPON_REQUERIDO
  if (porCampo.nombre) errores.nombre = porCampo.nombre.code === 'DUPLICADO' ? TEXTO.NOMBRE_DUPLICADO : TEXTO.NOMBRE_INVALIDO
  if (porCampo.fechaIngreso) errores.fechaIngreso = TEXTO.FECHA_INGRESO_INVALIDA
  if (porCampo.poblacionInicial) errores.poblacionInicial = TEXTO.POBLACION_INICIAL_INVALIDA
  if (porCampo.costoTotal) errores.costoTotal = TEXTO.COSTO_TOTAL_INVALIDO
  // Estos códigos no traen fieldErrors: se asocian a su campo por el código.
  const codigo = error instanceof ErrorApi ? error.problema.code : ''
  if (codigo === 'FECHA_INGRESO_FUTURA') errores.fechaIngreso = TEXTO.FECHA_INGRESO_FUTURA
  if (codigo === 'GALPON_NO_DISPONIBLE') errores.galponId = TEXTO.GALPON_NO_DISPONIBLE
  if (codigo === 'GALPON_NO_ENCONTRADO') errores.galponId = TEXTO.GALPON_NO_ENCONTRADO
  return errores
}

/** Registrar lote (US2, FR-001 a FR-014). */
export function RegistrarLotePagina() {
  const navegar = useNavigate()
  const consultas = useQueryClient()
  const [query] = useSearchParams()
  const [datos, setDatos] = useState<Formulario>({
    galponId: query.get('galponId') ?? '',
    nombre: '',
    fechaIngreso: '',
    poblacionInicial: '',
    costoTotal: '',
  })
  const [errores, setErrores] = useState<Errores>({})

  // FR-002: solo se ofrecen galpones en estado DISPONIBLE.
  const disponibles = useQuery({
    queryKey: ['galpones', 'disponibles'],
    queryFn: () => listarTodosLosGalpones('DISPONIBLE'),
  })

  const registro = useMutation({
    mutationFn: registrarLote,
    onSuccess: async (lote) => {
      const galpon = disponibles.data?.find((g) => g.id === lote.galponId)
      await Promise.all([
        consultas.invalidateQueries({ queryKey: ['lotes'] }),
        consultas.invalidateQueries({ queryKey: ['galpones'] }),
        consultas.invalidateQueries({ queryKey: ['galpon'] }),
      ])
      const aviso = {
        titulo: `${lote.nombre} registrado`,
        detalle: `La población actual quedó igual a la inicial (${formatoEntero(lote.poblacionActual)} pollos). Se remitió a Actualizar estado la solicitud Disponible → Productivo ${galpon ? `del ${galpon.nombre}` : 'del galpón'}.`,
        resaltarId: lote.id,
      }
      const destino = await rutaDelRegistro('/lotes', aviso, listarLotes)
      navegar(destino.ruta, { state: destino.aviso })
    },
    onError: (error) => {
      setErrores(erroresDeRespuesta(error))
      if (error instanceof ErrorApi && ['GALPON_NO_DISPONIBLE', 'GALPON_NO_ENCONTRADO'].includes(error.problema.code)) {
        void disponibles.refetch()
      }
    },
  })

  const cambiar = (campo: Campo) => (valor: string) => setDatos((actual) => ({ ...actual, [campo]: valor }))

  const enviar = (evento: FormEvent) => {
    evento.preventDefault()
    const encontrados = validar(datos)
    setErrores(encontrados)
    if (Object.keys(encontrados).length > 0) return
    registro.mutate({
      galponId: datos.galponId,
      nombre: datos.nombre.trim(),
      fechaIngreso: datos.fechaIngreso,
      poblacionInicial: enteroPositivo(datos.poblacionInicial)!,
      costoTotal: enteroPositivo(datos.costoTotal)!,
    })
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

  const cabecera = <PageHeader titulo="Registrar lote" subtitulo="Registra un lote de pollos en un galpón disponible." />

  if (disponibles.isPending) {
    return (
      <>
        {cabecera}
        <p className="cap">Cargando galpones disponibles…</p>
      </>
    )
  }

  if (disponibles.isError) {
    return (
      <>
        {cabecera}
        <ErrorDeConsulta
          error={disponibles.error}
          recurso="los galpones disponibles"
          onReintentar={() => disponibles.refetch()}
          reintentando={disponibles.isFetching}
        />
      </>
    )
  }

  if (disponibles.data.length === 0) {
    return (
      <>
        {cabecera}
        <section className="card">
          <EmptyState titulo="No hay galpones disponibles para recibir un lote">
            <p className="mut">Un lote solo se registra en un galpón en estado Disponible.</p>
            <Link className="btn" to="/galpones">
              Ver galpones
            </Link>
          </EmptyState>
        </section>
      </>
    )
  }

  return (
    <>
      {cabecera}

      {cantidadErrores > 0 && (
        <Banner
          tono="warn"
          titulo={`Revisa ${cantidadErrores === 1 ? '1 campo' : `${cantidadErrores} campos`} antes de registrar el lote`}
        >
          No se creó el lote ni cambió el estado del galpón. Tus datos se conservaron.
        </Banner>
      )}
      {errorGeneral && (
        <Banner tono="warn" titulo="No se registró el lote">
          {errorGeneral} Tus datos siguen en el formulario.
        </Banner>
      )}

      <div className="g21">
        <form className="card lg col" onSubmit={enviar} noValidate>
          <div className="fg2">
            <Field
              etiqueta="Galpón"
              ayuda={`Solo aparecen galpones Disponibles (${disponibles.data.length}).`}
              error={errores.galponId}
            >
              {(props) => (
                <>
                  <select
                    {...props}
                    className="input"
                    value={datos.galponId}
                    onChange={(evento) => cambiar('galponId')(evento.target.value)}
                  >
                    <option value="">Selecciona un galpón</option>
                    {disponibles.data.map((galpon) => (
                      <option key={galpon.id} value={galpon.id}>
                        {galpon.nombre}
                      </option>
                    ))}
                  </select>
                  <span className="chev">
                    <Icono nombre="abajo" />
                  </span>
                </>
              )}
            </Field>
            <Field etiqueta="Nombre del lote" ayuda="Único. Hasta 100 caracteres: letras, números, espacios, guiones y guiones bajos." error={errores.nombre}>
              {(props) => (
                <input
                  {...props}
                  className="input mono"
                  type="text"
                  autoComplete="off"
                  placeholder="Ej. Lote C1"
                  value={datos.nombre}
                  onChange={(evento) => cambiar('nombre')(evento.target.value)}
                />
              )}
            </Field>
            <Field etiqueta="Fecha de ingreso" ayuda="No puede ser futura." error={errores.fechaIngreso}>
              {(props) => (
                <input
                  {...props}
                  className="input mono"
                  type="date"
                  max={hoyEnBogota()}
                  value={datos.fechaIngreso}
                  onChange={(evento) => cambiar('fechaIngreso')(evento.target.value)}
                />
              )}
            </Field>
            <Field
              etiqueta="Población inicial"
              ayuda="Número entero mayor que cero."
              error={errores.poblacionInicial}
              unidad="pollos"
            >
              {(props) => (
                <input
                  {...props}
                  className="input mono"
                  type="text"
                  inputMode="numeric"
                  autoComplete="off"
                  placeholder="Ej. 1000"
                  value={datos.poblacionInicial}
                  onChange={(evento) => cambiar('poblacionInicial')(evento.target.value)}
                />
              )}
            </Field>
          </div>
          <Field etiqueta="Costo total" ayuda="Pesos colombianos, sin decimales." error={errores.costoTotal} unidad="COP">
            {(props) => (
              <input
                {...props}
                className="input mono"
                type="text"
                inputMode="numeric"
                autoComplete="off"
                placeholder="Ej. 5000000"
                value={datos.costoTotal}
                onChange={(evento) => cambiar('costoTotal')(evento.target.value)}
              />
            )}
          </Field>
          <div className="row">
            <button type="submit" className="btn p" disabled={registro.isPending}>
              {registro.isPending ? 'Registrando…' : 'Registrar lote'}
            </button>
            <Link className="btn" to="/lotes">
              Cancelar
            </Link>
          </div>
        </form>

        <aside className="card lg">
          <h2 className="h3">Al registrar</h2>
          <dl className="kv">
            <dt>Población actual</dt>
            <dd className="cap">Igual a la población inicial</dd>
            <dt>Estado del galpón</dt>
            <dd className="row wrap">
              <StatusChip estado="DISPONIBLE" />
              <Icono nombre="derecha" tamano="sm" />
              <StatusChip estado="PRODUCTIVO" />
            </dd>
            <dt>Quién cambia el estado</dt>
            <dd className="cap">Se remite a Actualizar estado; este formulario no lo cambia</dd>
            <dt>Después</dt>
            <dd className="cap">Vuelves al listado de lotes</dd>
          </dl>
        </aside>
      </div>
    </>
  )
}
