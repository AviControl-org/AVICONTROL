package com.unimag.avicontrol.infrastructure.in.kafka.sanitario;

import com.unimag.avicontrol.application.port.in.sanitario.RecibirVaciadoSanitarioUseCase;
import com.unimag.avicontrol.domain.exception.NegocioException;
import com.unimag.avicontrol.infrastructure.in.kafka.compartido.LectorEventos;
import com.unimag.avicontrol.infrastructure.in.kafka.dto.compartido.EventoEnvelope;
import com.unimag.avicontrol.infrastructure.in.kafka.dto.sanitario.VaciadoSanitarioMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;

/** Un rechazo de negocio se registra y no se reintenta; un error técnico se propaga para que Kafka lo reintente. */
@Component
public class VaciadoSanitarioConsumer {

    static final String TOPIC = "avicontrol.vaciado-sanitario";
    static final String EVENT_TYPE = "VACIADO_SANITARIO_SOLICITADO";
    private static final TypeReference<EventoEnvelope<VaciadoSanitarioMessage>> TIPO = new TypeReference<>() {
    };
    private static final Logger log = LoggerFactory.getLogger(VaciadoSanitarioConsumer.class);

    private final LectorEventos lector;
    private final RecibirVaciadoSanitarioUseCase useCase;

    public VaciadoSanitarioConsumer(LectorEventos lector, RecibirVaciadoSanitarioUseCase useCase) {
        this.lector = lector;
        this.useCase = useCase;
    }

    @KafkaListener(topics = TOPIC)
    public void consumir(String json) {
        VaciadoSanitarioMessage m = null;
        try {
            m = lector.leer(json, TIPO, EVENT_TYPE).payload();
            useCase.recibir(new RecibirVaciadoSanitarioUseCase.Comando(m.alertaId(), m.galponId(), m.loteId(),
                    m.fechaHoraEvento()));
        } catch (NegocioException e) {
            log.warn("rechazada alertaId={} galponId={} codigo={} mensaje=\"{}\"", m == null ? null : m.alertaId(),
                    m == null ? null : m.galponId(), e.codigo(), e.getMessage());
        }
    }
}
