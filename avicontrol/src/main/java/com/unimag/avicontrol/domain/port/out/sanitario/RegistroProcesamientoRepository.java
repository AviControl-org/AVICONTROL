package com.unimag.avicontrol.domain.port.out.sanitario;

import com.unimag.avicontrol.domain.model.sanitario.RegistroProcesamiento;
import java.util.Optional;
import java.util.UUID;

public interface RegistroProcesamientoRepository {

    RegistroProcesamiento save(RegistroProcesamiento registro);

    Optional<RegistroProcesamiento> findById(UUID alertaId);
}
