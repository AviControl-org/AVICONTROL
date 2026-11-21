package com.unimag.avicontrol.infrastructure.in.job.sanitario;

import com.unimag.avicontrol.application.port.in.sanitario.FinalizarVaciadoSanitarioUseCase;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Adaptador de entrada programado: solo activa el caso de uso, sin reglas de negocio. Una sola instancia (una granja). */
@Component
public class VaciadoSanitarioJob {

    private final FinalizarVaciadoSanitarioUseCase useCase;

    public VaciadoSanitarioJob(FinalizarVaciadoSanitarioUseCase useCase) {
        this.useCase = useCase;
    }

    /** Por defecto cada hora; se cambia con {@code avicontrol.vaciado-sanitario.cron}. */
    @Scheduled(cron = "${avicontrol.vaciado-sanitario.cron:0 0 * * * *}", zone = "America/Bogota")
    public void ejecutar() {
        useCase.finalizarPeriodosCumplidos();
    }
}
