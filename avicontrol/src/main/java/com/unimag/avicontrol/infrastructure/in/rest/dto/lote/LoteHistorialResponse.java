package com.unimag.avicontrol.infrastructure.in.rest.dto.lote;

import com.unimag.avicontrol.infrastructure.in.rest.dto.compartido.IncidenciaResponse;
import java.util.List;
import java.util.UUID;

public record LoteHistorialResponse(UUID galponId, LoteDetalleResponse loteActivo,
                                    List<LoteDetalleResponse> lotesAnteriores, List<IncidenciaResponse> incidencias) {
}
