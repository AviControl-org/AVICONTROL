package com.unimag.avicontrol.infrastructure.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;

/**
 * Enteros estrictos: un decimal (10.5) en un campo entero es un error y no se trunca. Los campos nulos se omiten
 * con {@code spring.jackson.default-property-inclusion=non_null} (application.properties).
 */
@Configuration
public class JacksonConfig {

    @Bean
    public JsonMapperBuilderCustomizer enterosEstrictos() {
        return builder -> builder.disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
    }
}
