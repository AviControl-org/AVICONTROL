package com.unimag.avicontrol.infrastructure.local;

import com.unimag.avicontrol.domain.exception.ValidacionNegocioException;
import com.unimag.avicontrol.infrastructure.in.kafka.dto.compartido.EventoEnvelope;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Herramienta de demostración (solo perfil {@code local}): hace de Módulo 2. Recibe un {@code payload}, lo envuelve
 * en el sobre estándar y lo publica en el tópico correspondiente. No es una prueba de integración.
 */
@Profile("local")
@RestController
@RequestMapping("/local/simulador-modulo2")
public class SimuladorModulo2 {

    private final KafkaTemplate<String, String> kafka;
    private final JsonMapper mapper;
    private final Clock clock;

    public SimuladorModulo2(KafkaTemplate<String, String> kafka, JsonMapper mapper, Clock clock) {
        this.kafka = kafka;
        this.mapper = mapper;
        this.clock = clock;
    }

    @PostMapping("/mortalidad")
    public ResponseEntity<Map<String, String>> mortalidad(@RequestBody JsonNode payload) throws Exception {
        return publicar("avicontrol.mortalidad", "MORTALIDAD_REGISTRADA", payload);
    }

    @PostMapping("/alerta-sanitaria")
    public ResponseEntity<Map<String, String>> alertaSanitaria(@RequestBody JsonNode payload) throws Exception {
        return publicar("avicontrol.alerta-sanitaria", "ALERTA_SANITARIA_EMITIDA", payload);
    }

    @PostMapping("/vaciado-sanitario")
    public ResponseEntity<Map<String, String>> vaciadoSanitario(@RequestBody JsonNode payload) throws Exception {
        return publicar("avicontrol.vaciado-sanitario", "VACIADO_SANITARIO_SOLICITADO", payload);
    }

    private ResponseEntity<Map<String, String>> publicar(String topic, String eventType, JsonNode payload)
            throws Exception {
        if (!payload.isObject()) {
            throw new ValidacionNegocioException("CUERPO_INVALIDO", "El cuerpo debe ser un objeto JSON.");
        }
        UUID eventId = UUID.randomUUID();
        // La clave de partición es el galpón (acuerdo entre módulos); si la alerta solo trae lote, se usa el lote.
        String clave = texto(payload, "galponId", texto(payload, "loteId", eventId.toString()));
        OffsetDateTime ahora = OffsetDateTime.now(clock);
        EventoEnvelope<JsonNode> sobre = new EventoEnvelope<>(eventId, eventType, 1, ahora, ahora, "modulo-2", null,
                null, "Galpon", clave, payload);

        kafka.send(topic, clave, mapper.writeValueAsString(sobre)).get(10, TimeUnit.SECONDS);

        Map<String, String> respuesta = new LinkedHashMap<>();
        respuesta.put("topic", topic);
        respuesta.put("eventType", eventType);
        respuesta.put("eventId", eventId.toString());
        respuesta.put("clave", clave);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(respuesta);
    }

    private static String texto(JsonNode payload, String campo, String porDefecto) {
        JsonNode valor = payload.get(campo);
        return valor == null || valor.isNull() ? porDefecto : valor.toString().replace("\"", "");
    }
}
