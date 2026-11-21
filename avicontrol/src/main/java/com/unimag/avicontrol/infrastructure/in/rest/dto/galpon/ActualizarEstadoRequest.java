package com.unimag.avicontrol.infrastructure.in.rest.dto.galpon;

import com.unimag.avicontrol.domain.model.galpon.EstadoGalpon;
import jakarta.validation.constraints.NotNull;

/** Un valor fuera de los seis estados falla al deserializar y se responde como ESTADO_INVALIDO. */
public record ActualizarEstadoRequest(@NotNull EstadoGalpon estadoEsperado, @NotNull EstadoGalpon estadoDestino) {
}
