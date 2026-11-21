package com.unimag.avicontrol.infrastructure.in.kafka.sanitario;

import com.unimag.avicontrol.application.port.in.sanitario.RecibirAlertaSanitariaUseCase;
import com.unimag.avicontrol.domain.exception.NegocioException;
import com.unimag.avicontrol.infrastructure.in.kafka.compartido.LectorEventos;
import com.unimag.avicontrol.infrastructure.in.kafka.dto.compartido.EventoEnvelope;
import com.unimag.avicontrol.infrastructure.in.kafka.dto.sanitario.AlertaSanitariaMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;

@Component
public class AlertaSanitariaConsumer {

    static final String TOPIC = "avicontrol.alerta-sanitaria";
    static final String EVENT_TYPE = "ALERTA_SANITARIA_EMITIDA";
    private static final TypeReference<EventoEnvelope<AlertaSanitariaMessage>> TIPO = new TypeReference<>() {
    };
    private static final String RECHAZO = "rechazada alertaId={} galponId={} loteId={} codigo={} mensaje=\"{}\"";
    private static final Logger log = LoggerFactory.getLogger(AlertaSanitariaConsumer.class);

    private final LectorEventos lector;
    private final RecibirAlertaSanitariaUseCase useCase;

    public AlertaSanitariaConsumer(LectorEventos lector, RecibirAlertaSanitariaUseCase useCase) {
        this.lector = lector;
        this.useCase = useCase;
    }

    @KafkaListener(topics = TOPIC)
    public void consumir(String json) {
        AlertaSanitariaMessage m = null;
        try {
            m = lector.leer(json, TIPO, EVENT_TYPE).payload();
            useCase.recibir(new RecibirAlertaSanitariaUseCase.Comando(m.alertaId(), m.accion(), m.galponId(),
                    m.loteId(), m.fechaHora(), m.tipoEnfermedad(), m.descripcion(), m.gravedad()));
        } catch (NegocioException e) {
            registrarRechazo(m, e);
        }
    }

    /** Una reentrega de AISLAMIENTO sobre un galpón ya aislado es ACCION_INCOMPATIBLE: esperado, nivel INFO. */
    private void registrarRechazo(AlertaSanitariaMessage m, NegocioException e) {
        Object alertaId = m == null ? null : m.alertaId();
        Object galponId = m == null ? null : m.galponId();
        Object loteId = m == null ? null : m.loteId();
        if ("ACCION_INCOMPATIBLE".equals(e.codigo())) {
            log.info(RECHAZO, alertaId, galponId, loteId, e.codigo(), e.getMessage());
        } else {
            log.warn(RECHAZO, alertaId, galponId, loteId, e.codigo(), e.getMessage());
        }
    }
}
