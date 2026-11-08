package com.unimag.avicontrol.application.usecase.lote;

import com.unimag.avicontrol.application.port.in.lote.ActualizarPoblacionCommand;
import com.unimag.avicontrol.application.port.in.lote.ActualizarPoblacionResultado;
import com.unimag.avicontrol.application.port.in.lote.ActualizarPoblacionUseCase;
import com.unimag.avicontrol.domain.exception.ConflictoException;
import com.unimag.avicontrol.domain.exception.RecursoNoEncontradoException;
import com.unimag.avicontrol.domain.exception.ValidacionNegocioException;
import com.unimag.avicontrol.domain.model.lote.Lote;
import com.unimag.avicontrol.domain.model.sanitario.RegistroProcesamiento;
import com.unimag.avicontrol.domain.port.out.lote.LoteRepository;
import com.unimag.avicontrol.domain.port.out.sanitario.RegistroProcesamientoRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Único escritor de RegistroProcesamiento; MortalidadService solo lo consulta. */
@Service
public class PoblacionService implements ActualizarPoblacionUseCase {

    private final LoteRepository lotes;
    private final RegistroProcesamientoRepository registros;
    private final Clock clock;

    public PoblacionService(LoteRepository lotes, RegistroProcesamientoRepository registros, Clock clock) {
        this.lotes = lotes;
        this.registros = registros;
        this.clock = clock;
    }

    /**
     * Valida, descuenta y registra en una sola transacción: si algo falla, la población conserva su valor (FR-015).
     * Dos descuentos simultáneos del mismo lote chocan por @Version y el segundo se reintenta (P-05).
     */
    @Override
    @Transactional
    public ActualizarPoblacionResultado actualizar(ActualizarPoblacionCommand comando) {
        Lote lote = lotes.findById(comando.loteId()).orElseThrow(() ->
                new RecursoNoEncontradoException("LOTE_NO_ENCONTRADO", "El lote de la alerta no existe."));

        var previo = registros.findById(comando.alertaId());
        if (previo.isPresent()) {
            boolean mismosDatos = previo.get().mismosDatos(comando.galponId(), comando.loteId(),
                    comando.cantidadMuertos(), comando.fechaHoraEvento());
            if (!mismosDatos) {
                throw new ConflictoException("ALERTA_INCONSISTENTE",
                        "El alertaId ya fue procesado con datos distintos.");
            }
            return new ActualizarPoblacionResultado(lote.id(), lote.poblacionActual(), lote.poblacionActual(), true);
        }

        if (!lote.galponId().equals(comando.galponId())) {
            throw new ValidacionNegocioException("LOTE_NO_PERTENECE_AL_GALPON",
                    "El lote no corresponde al galpón de la alerta.");
        }
        if (!lote.activo()) {
            throw new ValidacionNegocioException("LOTE_NO_ACTIVO", "El lote no es el lote activo del galpón.");
        }

        Lote actualizado = lotes.save(lote.descontar(comando.cantidadMuertos()));
        registros.save(new RegistroProcesamiento(comando.alertaId(), comando.galponId(), comando.loteId(),
                comando.cantidadMuertos(), comando.fechaHoraEvento(), clock.instant()));
        return new ActualizarPoblacionResultado(lote.id(), lote.poblacionActual(), actualizado.poblacionActual(), false);
    }
}
