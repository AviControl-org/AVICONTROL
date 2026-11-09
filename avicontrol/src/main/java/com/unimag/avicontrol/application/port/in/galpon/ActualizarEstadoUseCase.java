package com.unimag.avicontrol.application.port.in.galpon;

import com.unimag.avicontrol.domain.model.compartido.Pagina;
import com.unimag.avicontrol.domain.model.galpon.EstadoGalpon;
import com.unimag.avicontrol.domain.model.galpon.HistorialEstado;
import java.util.List;
import java.util.UUID;

public interface ActualizarEstadoUseCase {

    /** Cambio pedido por el administrador; devuelve la fila de historial que quedó escrita. */
    HistorialEstado actualizarEstado(UUID galponId, EstadoGalpon estadoEsperado, EstadoGalpon estadoDestino);

    TransicionesDisponibles transiciones(UUID galponId);

    Pagina<HistorialEstado> historial(UUID galponId, int pagina, int tamanio);

    record TransicionesDisponibles(UUID galponId, EstadoGalpon estadoActual, List<EstadoGalpon> destinos) {
    }
}
