package com.unimag.avicontrol.domain.model.galpon;

import java.time.Instant;
import java.util.UUID;

public record HistorialEstado(UUID id, UUID galponId, EstadoGalpon estadoAnterior, EstadoGalpon estadoNuevo,
                              Instant fechaHora, OrigenCambioEstado origen) {
}
