package com.unimag.avicontrol.infrastructure.in.rest.dto.galpon;

import com.unimag.avicontrol.domain.model.galpon.EstadoGalpon;
import com.unimag.avicontrol.infrastructure.in.rest.dto.compartido.IncidenciaResponse;
import com.unimag.avicontrol.infrastructure.in.rest.dto.lote.LoteDetalleResponse;
import java.util.List;
import java.util.UUID;

/** {@code loteActivo}, {@code mensaje} e {@code incidencias} se omiten cuando no aplican (nunca viajan como null). */
public record GalponDetalleResponse(UUID id, String nombre, long capacidad, EstadoGalpon estado,
                                    LoteDetalleResponse loteActivo, String mensaje,
                                    List<IncidenciaResponse> incidencias) {
}
