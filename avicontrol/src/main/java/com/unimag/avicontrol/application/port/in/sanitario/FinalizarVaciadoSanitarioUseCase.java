package com.unimag.avicontrol.application.port.in.sanitario;

public interface FinalizarVaciadoSanitarioUseCase {

    /** Devuelve a DISPONIBLE los galpones cuyo período de vaciado ya se cumplió. */
    void finalizarPeriodosCumplidos();
}
