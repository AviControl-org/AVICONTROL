package com.unimag.avicontrol.domain.model.compartido;

import java.time.ZoneId;

/** Zona horaria de la operación: los días, las edades y las fechas que ve el usuario se calculan en Bogotá. */
public final class ZonaNegocio {

    public static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    private ZonaNegocio() {
    }
}
