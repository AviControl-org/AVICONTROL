package com.unimag.avicontrol.domain.port.out.galpon;

import java.util.UUID;
import com.unimag.avicontrol.domain.model.compartido.Pagina;
import com.unimag.avicontrol.domain.model.galpon.HistorialEstado;

public interface HistorialEstadoRepository {

    HistorialEstado save(HistorialEstado historial);

    /** Del más reciente al más antiguo; {@code pagina} empieza en 1. */
    Pagina<HistorialEstado> findByGalponId(UUID galponId, int pagina, int tamanio);
}
