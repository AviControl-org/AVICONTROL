package com.unimag.avicontrol.infrastructure.in.rest.dto.mantenimiento;

import java.util.List;

public record AlertaMantenimientoPaginaResponse(List<AlertaMantenimientoResponse> contenido, int pagina, int tamanio,
                                                long totalElementos, int totalPaginas) {
}
