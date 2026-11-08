package com.unimag.avicontrol.domain.policy.compartido;

import java.time.Clock;
import java.time.LocalDate;

/** Reglas de fecha en la zona del negocio: el {@code Clock} inyectado ya lleva America/Bogota. */
public final class ReglasFecha {

    private ReglasFecha() {
    }

    /** "Hoy" en America/Bogota. */
    public static LocalDate hoy(Clock clock) {
        return LocalDate.now(clock);
    }
}
