package com.unimag.avicontrol.application.port.in.lote;

import java.util.UUID;

/** Si {@code duplicado} es true, el {@code alertaId} ya estaba aplicado: las cifras son la población vigente. */
public record ActualizarPoblacionResultado(UUID loteId, long poblacionAnterior, long poblacionActual,
                                           boolean duplicado) {
}
