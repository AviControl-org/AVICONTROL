package com.unimag.avicontrol.domain.policy.compartido;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Reglas de fecha en la zona del negocio: el {@code Clock} inyectado ya lleva America/Bogota. */
public final class ReglasFecha {

    private ReglasFecha() {
    }

    /** "Hoy" en America/Bogota. */
    public static LocalDate hoy(Clock clock) {
        return LocalDate.now(clock);
    }

    /** La fecha-hora cae en el día de hoy en America/Bogota (la hora puede variar). */
    public static boolean esDeHoy(OffsetDateTime fechaHora, Clock clock) {
        return fechaHora.atZoneSameInstant(clock.getZone()).toLocalDate().equals(hoy(clock));
    }

    /** La fecha-hora es posterior al momento actual. */
    public static boolean esFutura(OffsetDateTime fechaHora, Clock clock) {
        return fechaHora.toInstant().isAfter(clock.instant());
    }
}
