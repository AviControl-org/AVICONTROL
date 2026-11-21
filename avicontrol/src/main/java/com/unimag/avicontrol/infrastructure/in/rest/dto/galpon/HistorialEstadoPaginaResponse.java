package com.unimag.avicontrol.infrastructure.in.rest.dto.galpon;

import java.util.List;

public record HistorialEstadoPaginaResponse(List<HistorialEstadoResponse> contenido, int pagina, int tamanio,
                                            long totalElementos, int totalPaginas) {
}
