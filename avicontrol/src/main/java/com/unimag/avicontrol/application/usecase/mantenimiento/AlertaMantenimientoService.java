package com.unimag.avicontrol.application.usecase.mantenimiento;

import com.unimag.avicontrol.application.port.in.mantenimiento.AlertaMantenimientoUseCase;
import com.unimag.avicontrol.application.usecase.galpon.GalponEstadoService;
import com.unimag.avicontrol.domain.exception.ConflictoException;
import com.unimag.avicontrol.domain.exception.RecursoNoEncontradoException;
import com.unimag.avicontrol.domain.exception.ValidacionNegocioException;
import com.unimag.avicontrol.domain.model.compartido.Pagina;
import com.unimag.avicontrol.domain.model.galpon.EstadoGalpon;
import com.unimag.avicontrol.domain.model.galpon.Galpon;
import com.unimag.avicontrol.domain.model.galpon.HistorialEstado;
import com.unimag.avicontrol.domain.model.galpon.OrigenCambioEstado;
import com.unimag.avicontrol.domain.model.mantenimiento.AlertaMantenimiento;
import com.unimag.avicontrol.domain.model.mantenimiento.EstadoAlertaMantenimiento;
import com.unimag.avicontrol.domain.port.out.galpon.GalponRepository;
import com.unimag.avicontrol.domain.port.out.mantenimiento.AlertaMantenimientoRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AlertaMantenimientoService implements AlertaMantenimientoUseCase {

    private static final int DESCRIPCION_MAXIMA = 500;

    private final AlertaMantenimientoRepository alertas;
    private final GalponRepository galpones;
    private final GalponEstadoService estados;
    private final Clock clock;

    public AlertaMantenimientoService(AlertaMantenimientoRepository alertas, GalponRepository galpones,
                                      GalponEstadoService estados, Clock clock) {
        this.alertas = alertas;
        this.galpones = galpones;
        this.estados = estados;
        this.clock = clock;
    }

    /**
     * Orden de validación: descripción (400) -> técnico (400) -> galpón existe (404) -> galpón DISPONIBLE (409)
     * -> sin alerta pendiente (409). La alerta no cambia el estado del galpón.
     */
    @Override
    @Transactional
    public AlertaMantenimiento generar(Comando comando) {
        String descripcion = comando.descripcion() == null ? "" : comando.descripcion().strip();
        if (descripcion.isEmpty() || descripcion.length() > DESCRIPCION_MAXIMA) {
            throw new ValidacionNegocioException("DESCRIPCION_INVALIDA",
                    "La descripción es obligatoria y tiene máximo 500 caracteres.");
        }
        if (comando.tecnicoId() == null || comando.tecnicoId().isBlank()) {
            throw new ValidacionNegocioException("TECNICO_REQUERIDO", "El técnico es obligatorio.");
        }
        Galpon galpon = galpones.findById(comando.galponId()).orElseThrow(() ->
                new RecursoNoEncontradoException("GALPON_NO_ENCONTRADO", "El galpón seleccionado no existe."));
        if (galpon.estado() != EstadoGalpon.DISPONIBLE) {
            throw new ConflictoException("GALPON_NO_DISPONIBLE",
                    "Solo se pueden generar alertas para galpones disponibles.");
        }
        if (alertas.existsPendienteByGalponId(galpon.id())) {
            throw new ConflictoException("ALERTA_ACTIVA_EXISTENTE", "Ya existe una alerta activa para ese galpón.");
        }
        return alertas.save(AlertaMantenimiento.nueva(galpon.id(), descripcion, comando.tecnicoId().strip(),
                vacioANulo(comando.severidad()), vacioANulo(comando.tipo()), clock.instant()));
    }

    /**
     * Si el galpón ya no está DISPONIBLE, GalponEstadoService rechaza el cambio, la transacción se revierte y la
     * alerta sigue PENDIENTE (P-21).
     */
    @Override
    @Transactional
    public ResultadoAtencion atender(UUID alertaId) {
        AlertaMantenimiento alerta = alertas.findById(alertaId).orElseThrow(() ->
                new RecursoNoEncontradoException("ALERTA_NO_ENCONTRADA", "La alerta no existe."));
        if (alerta.estado() != EstadoAlertaMantenimiento.PENDIENTE) {
            throw new ConflictoException("ALERTA_NO_PENDIENTE", "La alerta ya no está pendiente.");
        }
        HistorialEstado cambio;
        try {
            cambio = estados.solicitarCambio(alerta.galponId(), EstadoGalpon.DISPONIBLE, EstadoGalpon.MANTENIMIENTO,
                    OrigenCambioEstado.ADMINISTRADOR);
        } catch (ConflictoException e) {
            if ("ESTADO_ESPERADO_CAMBIADO".equals(e.codigo())) {
                throw new ConflictoException("ESTADO_ESPERADO_CAMBIADO",
                        "El galpón ya no está disponible. Actualiza la información e inténtalo de nuevo.");
            }
            throw e;
        }
        AlertaMantenimiento atendida = alertas.save(alerta.atendida());
        return new ResultadoAtencion(atendida, alerta.galponId(), cambio.estadoNuevo());
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<AlertaMantenimiento> listar(EstadoAlertaMantenimiento estado, UUID galponId, int pagina,
                                              int tamanio) {
        return alertas.search(estado, galponId, pagina, tamanio);
    }

    private static String vacioANulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.strip();
    }
}
