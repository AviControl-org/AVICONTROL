package com.unimag.avicontrol.application.port.in.galpon;

import com.unimag.avicontrol.domain.model.galpon.Galpon;

public interface RegistrarGalponUseCase {

    Galpon registrar(Comando comando);

    record Comando(String nombre, long capacidad) {
    }
}
