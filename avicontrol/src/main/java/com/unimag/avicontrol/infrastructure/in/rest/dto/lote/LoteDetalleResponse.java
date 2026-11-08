package com.unimag.avicontrol.infrastructure.in.rest.dto.lote;

import java.time.LocalDate;
import java.util.UUID;

/** Un campo vacío del lote (nombre, fechaIngreso, edadDias) se omite y se informa en {@code incidencias}. */
public record LoteDetalleResponse(UUID id, String nombre, long poblacionInicial, long poblacionActual,
                                  LocalDate fechaIngreso, Integer edadDias, long costoTotal) {
}
