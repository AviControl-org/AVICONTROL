package com.unimag.avicontrol.domain.port.out.galpon;

import com.unimag.avicontrol.domain.model.galpon.Galpon;
import java.util.Optional;
import java.util.UUID;

public interface GalponRepository {

    Galpon save(Galpon galpon);

    Optional<Galpon> findById(UUID id);

    /** Lee el galpón bloqueándolo (`SELECT ... FOR UPDATE`) hasta el fin de la transacción. */
    Optional<Galpon> findByIdForUpdate(UUID id);

    /** `clave` es el nombre normalizado en minúsculas ({@code NombreNormalizado#clave()}). */
    boolean existsByNombreClave(String clave);

    boolean existsByNombreClaveAndIdNot(String clave, UUID id);
}
