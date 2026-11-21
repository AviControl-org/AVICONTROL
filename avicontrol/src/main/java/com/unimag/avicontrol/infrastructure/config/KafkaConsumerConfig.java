package com.unimag.avicontrol.infrastructure.config;

import com.unimag.avicontrol.domain.exception.NegocioException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Los consumers capturan los rechazos de negocio y los registran; lo que llega aquí es un error técnico
 * (base de datos, choque de versión del lote) y se reintenta con espera fija. Agotados los reintentos el registro
 * se descarta con un log de error; el dead-letter topic queda abierto en P-05.
 * El grupo (`modulo-1`) se define en application.properties.
 */
@Configuration
@EnableConfigurationProperties(AvicontrolProperties.class)
public class KafkaConsumerConfig {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(AvicontrolProperties propiedades) {
        DefaultErrorHandler manejador = new DefaultErrorHandler(
                new FixedBackOff(propiedades.reintentoIntervaloMs(), propiedades.reintentosMaximos()));
        manejador.addNotRetryableExceptions(NegocioException.class);
        return manejador;
    }
}
