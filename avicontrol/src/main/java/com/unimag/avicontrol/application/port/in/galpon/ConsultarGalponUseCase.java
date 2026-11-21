package com.unimag.avicontrol.application.port.in.galpon;

import com.unimag.avicontrol.domain.model.compartido.Pagina;
import com.unimag.avicontrol.domain.model.galpon.ConsultaGalpones;
import com.unimag.avicontrol.domain.model.galpon.Galpon;
import com.unimag.avicontrol.domain.model.lote.Lote;
import java.util.List;
import java.util.UUID;

public interface ConsultarGalponUseCase {

    ResultadoListado listar(ConsultaGalpones consulta);

    DetalleGalpon detalle(UUID galponId);

    HistorialLotes historialLotes(UUID galponId);

    /** {@code sinCoincidencias}: la búsqueda por nombre no encontró nada y {@code pagina} trae todos los galpones. */
    record ResultadoListado(Pagina<Galpon> pagina, boolean sinCoincidencias) {
    }

    /** {@code loteActivo} es null si el galpón no tiene lote activo; {@code sinLotes}, si nunca tuvo ninguno. */
    record DetalleGalpon(Galpon galpon, Lote loteActivo, boolean sinLotes, List<Incidencia> incidencias) {
    }

    record HistorialLotes(UUID galponId, Lote loteActivo, List<Lote> lotesAnteriores, List<Incidencia> incidencias) {
    }

    /** Dato faltante o inconsistente que no tumba la consulta (FR-016, FR-017). */
    record Incidencia(String codigo, String mensaje) {
    }
}
