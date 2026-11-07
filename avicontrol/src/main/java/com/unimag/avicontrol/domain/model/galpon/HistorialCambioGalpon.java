package com.unimag.avicontrol.domain.model.galpon;

import java.time.Instant;
import java.util.UUID;

/** Un campo (nombre o capacidad) que cambió al editar un galpón. */
public record HistorialCambioGalpon(UUID id, UUID galponId, String campo, String valorAnterior, String valorNuevo,
                                    Instant fechaHora) {
}
