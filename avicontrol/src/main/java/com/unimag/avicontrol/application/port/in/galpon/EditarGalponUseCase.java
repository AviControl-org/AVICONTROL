package com.unimag.avicontrol.application.port.in.galpon;

import com.unimag.avicontrol.domain.model.galpon.Galpon;
import java.util.UUID;

public interface EditarGalponUseCase {

    Galpon editar(UUID galponId, Comando comando);

    /** Un campo en null significa "no modificar". */
    record Comando(String nombre, Long capacidad) {
    }
}
