package com.unimag.avicontrol.infrastructure.in.rest.dto.lote;

import java.util.List;

public record LotePaginaResponse(List<LoteResponse> contenido, int pagina, int tamanio, long totalElementos,
                                 int totalPaginas) {
}
