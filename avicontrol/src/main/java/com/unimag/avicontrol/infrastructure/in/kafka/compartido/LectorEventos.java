package com.unimag.avicontrol.infrastructure.in.kafka.compartido;

import com.unimag.avicontrol.domain.exception.ValidacionNegocioException;
import com.unimag.avicontrol.infrastructure.in.kafka.dto.compartido.EventoEnvelope;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lee el sobre del Módulo 2 desde el JSON crudo. Un mensaje ilegible, de versión distinta de 1 o de otro
 * `eventType` se rechaza con su código; los campos desconocidos se ignoran.
 */
@Component
public class LectorEventos {

    private static final int VERSION_SOPORTADA = 1;

    private final JsonMapper mapper;

    public LectorEventos(JsonMapper mapper) {
        this.mapper = mapper;
    }

    public <T> EventoEnvelope<T> leer(String json, TypeReference<EventoEnvelope<T>> tipo, String eventTypeEsperado) {
        EventoEnvelope<T> evento;
        try {
            evento = mapper.readValue(json, tipo);
        } catch (JacksonException e) {
            throw new ValidacionNegocioException("MENSAJE_ILEGIBLE",
                    "El mensaje no es un JSON válido o tiene campos con tipos incorrectos.");
        }
        if (evento == null || evento.payload() == null) {
            throw new ValidacionNegocioException("ALERTA_INCOMPLETA", "Falta un campo obligatorio de la alerta.");
        }
        if (evento.eventVersion() == null || evento.eventVersion() != VERSION_SOPORTADA) {
            throw new ValidacionNegocioException("VERSION_NO_SOPORTADA", "La versión del evento no es soportada.");
        }
        if (!eventTypeEsperado.equals(evento.eventType())) {
            throw new ValidacionNegocioException("TIPO_EVENTO_NO_SOPORTADO",
                    "El tipo de evento no corresponde a este tópico.");
        }
        return evento;
    }
}
