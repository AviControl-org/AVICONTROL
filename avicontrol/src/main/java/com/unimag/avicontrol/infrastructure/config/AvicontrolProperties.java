package com.unimag.avicontrol.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Propiedades `avicontrol.*` de la política de reintentos de los consumers (application.properties). */
@ConfigurationProperties(prefix = "avicontrol")
public record AvicontrolProperties(long reintentoIntervaloMs, long reintentosMaximos) {
}
