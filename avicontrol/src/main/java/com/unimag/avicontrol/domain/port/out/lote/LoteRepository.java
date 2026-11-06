package com.unimag.avicontrol.domain.port.out.lote;

import com.unimag.avicontrol.domain.model.lote.Lote;
import java.util.Optional;
import java.util.UUID;

public interface LoteRepository {

    Lote save(Lote lote);

    Optional<Lote> findById(UUID id);

    /** El lote activo es el que no está desvinculado (`desvinculado_en IS NULL`). */
    Optional<Lote> findActivoByGalponId(UUID galponId);
}
