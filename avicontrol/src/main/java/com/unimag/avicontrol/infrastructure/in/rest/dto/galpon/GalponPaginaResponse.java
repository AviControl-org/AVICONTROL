package com.unimag.avicontrol.infrastructure.in.rest.dto.galpon;

import java.util.List;

/** {@code mensaje} solo viene cuando {@code sinCoincidencias} es true; entonces {@code contenido} trae todos los galpones. */
public record GalponPaginaResponse(List<GalponResumenResponse> contenido, int pagina, int tamanio,
                                   long totalElementos, int totalPaginas, boolean sinCoincidencias, String mensaje) {
}
