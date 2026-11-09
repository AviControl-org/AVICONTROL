package com.unimag.avicontrol.infrastructure.in.rest.dto.mantenimiento;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** La única transición permitida desde la API es hacia ATENDIDA; cualquier otro texto es ESTADO_INVALIDO. */
public record AtenderAlertaRequest(@NotNull @Pattern(regexp = "ATENDIDA") String estado) {
}
