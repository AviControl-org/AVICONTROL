package com.unimag.avicontrol.infrastructure.in.job.sanitario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.unimag.avicontrol.application.port.in.sanitario.FinalizarVaciadoSanitarioUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

/** El job solo activa el caso de uso; la lógica se prueba en VaciadoSanitarioServiceTest. */
@ExtendWith(MockitoExtension.class)
class VaciadoSanitarioJobTest {

    @Mock
    private FinalizarVaciadoSanitarioUseCase useCase;

    @Test
    void cadaEjecucionInvocaUnaSolaVezElCasoDeUso() {
        VaciadoSanitarioJob job = new VaciadoSanitarioJob(useCase);

        job.ejecutar();

        verify(useCase, times(1)).finalizarPeriodosCumplidos();
        verifyNoMoreInteractions(useCase);
    }

    @Test
    void dosEjecucionesInvocanElCasoDeUsoDosVeces() {
        VaciadoSanitarioJob job = new VaciadoSanitarioJob(useCase);

        job.ejecutar();
        job.ejecutar();

        verify(useCase, times(2)).finalizarPeriodosCumplidos();
    }

    @Test
    void estaProgramadoConElCronConfigurableYLaZonaDeBogota() throws Exception {
        Scheduled programado = VaciadoSanitarioJob.class.getMethod("ejecutar").getAnnotation(Scheduled.class);

        assertThat(programado).isNotNull();
        assertThat(programado.cron()).isEqualTo("${avicontrol.vaciado-sanitario.cron:0 0 * * * *}");
        assertThat(programado.zone()).isEqualTo("America/Bogota");
    }
}
