package com.unimag.avicontrol.application.port.in.lote;

import com.unimag.avicontrol.domain.model.lote.Lote;
import java.time.LocalDate;
import java.util.UUID;

public interface RegistrarLoteUseCase {

    Lote registrar(Comando comando);

    record Comando(UUID galponId, String nombre, LocalDate fechaIngreso, long poblacionInicial, long costoTotal) {
    }
}
