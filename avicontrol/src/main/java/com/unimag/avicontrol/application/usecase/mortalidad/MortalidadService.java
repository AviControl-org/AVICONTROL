package com.unimag.avicontrol.application.usecase.mortalidad;

import com.unimag.avicontrol.application.port.in.lote.ActualizarPoblacionCommand;
import com.unimag.avicontrol.application.port.in.lote.ActualizarPoblacionUseCase;
import com.unimag.avicontrol.application.port.in.mortalidad.RecibirMortalidadUseCase;
import com.unimag.avicontrol.domain.exception.ConflictoException;
import com.unimag.avicontrol.domain.exception.RecursoNoEncontradoException;
import com.unimag.avicontrol.domain.exception.ValidacionNegocioException;
import com.unimag.avicontrol.domain.model.galpon.EstadoGalpon;
import com.unimag.avicontrol.domain.model.galpon.Galpon;
import com.unimag.avicontrol.domain.model.lote.Lote;
import com.unimag.avicontrol.domain.policy.compartido.ReglasFecha;
import com.unimag.avicontrol.domain.port.out.galpon.GalponRepository;
import com.unimag.avicontrol.domain.port.out.lote.LoteRepository;
import com.unimag.avicontrol.domain.port.out.sanitario.RegistroProcesamientoRepository;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MortalidadService implements RecibirMortalidadUseCase {

    private static final Logger log = LoggerFactory.getLogger(MortalidadService.class);

    private final GalponRepository galpones;
    private final LoteRepository lotes;
    private final RegistroProcesamientoRepository registros;
    private final ActualizarPoblacionUseCase poblacion;
    private final Clock clock;

    public MortalidadService(GalponRepository galpones, LoteRepository lotes, RegistroProcesamientoRepository registros,
                             ActualizarPoblacionUseCase poblacion, Clock clock) {
        this.galpones = galpones;
        this.lotes = lotes;
        this.registros = registros;
        this.poblacion = poblacion;
        this.clock = clock;
    }

    /**
     * Orden de validación: campos obligatorios -> idempotencia -> galpón -> lote y su relación con el galpón ->
     * lote activo -> galpón PRODUCTIVO -> cantidad -> fecha. El primer fallo corta el proceso.
     */
    @Override
    @Transactional
    public void recibir(Comando comando) {
        if (comando.alertaId() == null || comando.galponId() == null || comando.loteId() == null
                || comando.fechaHoraEvento() == null || comando.cantidadMuertos() == null) {
            throw new ValidacionNegocioException("ALERTA_INCOMPLETA", "Falta un campo obligatorio de la alerta.");
        }
        Instant momento = comando.fechaHoraEvento().toInstant();

        var previo = registros.findById(comando.alertaId());
        if (previo.isPresent()) {
            if (previo.get().mismosDatos(comando.galponId(), comando.loteId(), comando.cantidadMuertos(), momento)) {
                log.info("ignorada alertaId={} motivo=YA_PROCESADA", comando.alertaId());
                return;
            }
            throw new ConflictoException("ALERTA_INCONSISTENTE", "El alertaId ya fue procesado con datos distintos.");
        }

        Galpon galpon = galpones.findById(comando.galponId()).orElseThrow(() ->
                new RecursoNoEncontradoException("GALPON_NO_ENCONTRADO", "El galpón de la alerta no existe."));
        Lote lote = lotes.findById(comando.loteId()).orElseThrow(() ->
                new RecursoNoEncontradoException("LOTE_NO_ENCONTRADO", "El lote de la alerta no existe."));
        if (!lote.galponId().equals(galpon.id())) {
            throw new ValidacionNegocioException("LOTE_NO_PERTENECE_AL_GALPON",
                    "El lote no corresponde al galpón de la alerta.");
        }
        if (!lote.activo()) {
            throw new ValidacionNegocioException("LOTE_NO_ACTIVO", "El lote no es el lote activo del galpón.");
        }
        if (galpon.estado() != EstadoGalpon.PRODUCTIVO) {
            throw new ValidacionNegocioException("GALPON_NO_PRODUCTIVO", "El galpón no está en estado PRODUCTIVO.");
        }
        if (comando.cantidadMuertos() <= 0) {
            throw new ValidacionNegocioException("CANTIDAD_INVALIDA",
                    "La cantidad de muertos debe ser un entero positivo.");
        }
        if (ReglasFecha.esFutura(comando.fechaHoraEvento(), clock)
                || !ReglasFecha.esDeHoy(comando.fechaHoraEvento(), clock)) {
            throw new ValidacionNegocioException("FECHA_INVALIDA",
                    "La fecha del evento es futura o corresponde a otro día.");
        }

        // Misma transacción: el descuento y el registro de idempotencia se confirman juntos (FR-011, FR-017).
        poblacion.actualizar(new ActualizarPoblacionCommand(comando.alertaId(), comando.loteId(),
                comando.galponId(), comando.cantidadMuertos(), momento));
    }
}
