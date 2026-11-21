package com.unimag.avicontrol.infrastructure.in.kafka.mortalidad;

import com.unimag.avicontrol.application.port.in.mortalidad.RecibirMortalidadUseCase;
import com.unimag.avicontrol.domain.exception.NegocioException;
import com.unimag.avicontrol.domain.exception.ValidacionNegocioException;
import com.unimag.avicontrol.infrastructure.in.kafka.compartido.LectorEventos;
import com.unimag.avicontrol.infrastructure.in.kafka.dto.compartido.EventoEnvelope;
import com.unimag.avicontrol.infrastructure.in.kafka.dto.mortalidad.MortalidadMessage;
import java.math.BigInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;

/**
 * Adaptador de entrada Kafka: lee el sobre, arma el comando y delega. No contiene reglas de negocio.
 * Un rechazo de negocio se registra y se confirma sin reintento; un error técnico se propaga para que el
 * manejador de errores del contenedor lo reintente (P-05).
 */
@Component
public class MortalidadConsumer {

    static final String TOPIC = "avicontrol.mortalidad";
    static final String EVENT_TYPE = "MORTALIDAD_REGISTRADA";
    private static final TypeReference<EventoEnvelope<MortalidadMessage>> TIPO = new TypeReference<>() {
    };
    private static final Logger log = LoggerFactory.getLogger(MortalidadConsumer.class);

    private final LectorEventos lector;
    private final RecibirMortalidadUseCase useCase;

    public MortalidadConsumer(LectorEventos lector, RecibirMortalidadUseCase useCase) {
        this.lector = lector;
        this.useCase = useCase;
    }

    @KafkaListener(topics = TOPIC)
    public void consumir(String json) {
        MortalidadMessage mensaje = null;
        try {
            mensaje = lector.leer(json, TIPO, EVENT_TYPE).payload();
            useCase.recibir(new RecibirMortalidadUseCase.Comando(mensaje.alertaId(), mensaje.galponId(),
                    mensaje.loteId(), mensaje.fechaHoraEvento(), comoEntero(mensaje.cantidadMuertos())));
        } catch (NegocioException e) {
            log.warn("rechazada alertaId={} galponId={} codigo={} mensaje=\"{}\"",
                    mensaje == null ? null : mensaje.alertaId(), mensaje == null ? null : mensaje.galponId(),
                    e.codigo(), e.getMessage());
        }
    }

    /** Solo un entero JSON es válido: 25 sí; 25.0, 25.5 o texto, no (FR-008). */
    private static Long comoEntero(Number numero) {
        if (numero == null) {
            return null;
        }
        if (numero instanceof Integer || numero instanceof Long || numero instanceof Short || numero instanceof Byte) {
            return numero.longValue();
        }
        if (numero instanceof BigInteger grande && grande.bitLength() < Long.SIZE) {
            return grande.longValue();
        }
        throw new ValidacionNegocioException("CANTIDAD_INVALIDA", "La cantidad de muertos debe ser un entero positivo.");
    }
}
