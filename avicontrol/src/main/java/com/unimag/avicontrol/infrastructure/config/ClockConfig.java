package com.unimag.avicontrol.infrastructure.config;

import com.unimag.avicontrol.domain.model.compartido.ZonaNegocio;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Reloj único de la aplicación; las pruebas lo reemplazan por uno fijo. */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.system(ZonaNegocio.BOGOTA);
    }
}
