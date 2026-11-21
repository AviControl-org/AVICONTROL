// Formato de cifras y fechas de los mockups: "1.500", "$ 6.000.000", "24 ago 2026".
const MESES = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic']

const ENTERO = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 0, useGrouping: 'always' })

export function formatoEntero(valor: number): string {
  return ENTERO.format(valor)
}

export function formatoPesos(valor: number): string {
  return `$ ${ENTERO.format(valor)}`
}

/** `fecha` llega como `yyyy-MM-dd` (convenciones de contrato); se lee sin pasar por zonas horarias. */
export function formatoFecha(fecha: string): string {
  const [anio, mes, dia] = fecha.split('-')
  return `${dia} ${MESES[Number(mes) - 1]} ${anio}`
}
