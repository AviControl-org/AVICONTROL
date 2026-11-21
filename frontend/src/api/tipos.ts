// Tipos comunes de docs/arquitectura/arquitectura.md, sección "Convenciones de contrato".
// Los DTO de cada servicio van en el archivo de su recurso y copian los nombres del plan de su spec.

export const ESTADOS_GALPON = [
  'DISPONIBLE',
  'VACIADO_SANITARIO',
  'PRODUCTIVO',
  'EN_COSECHA',
  'MANTENIMIENTO',
  'AISLAMIENTO',
] as const

export type EstadoGalpon = (typeof ESTADOS_GALPON)[number]

export interface Pagina<T> {
  contenido: T[]
  pagina: number
  tamanio: number
  totalElementos: number
  totalPaginas: number
}

export type CodigoErrorCampo =
  | 'OBLIGATORIO'
  | 'LONGITUD_INVALIDA'
  | 'FORMATO_INVALIDO'
  | 'ENTERO_POSITIVO'
  | 'VALOR_NO_PERMITIDO'
  | 'MINIMO_1'
  | 'FUERA_DE_RANGO'
  | 'DUPLICADO'

export interface ErrorDeCampo {
  field: string
  code: CodigoErrorCampo
  message: string
}

export interface ProblemDetail {
  type: string
  title: string
  status: number
  detail: string
  instance: string
  code: string
  fieldErrors?: ErrorDeCampo[]
}
