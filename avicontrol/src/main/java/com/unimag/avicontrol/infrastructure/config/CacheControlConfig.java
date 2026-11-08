package com.unimag.avicontrol.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Toda respuesta GET de la API lleva {@code Cache-Control: no-store}: lo que se muestra es la foto del momento. */
@Configuration
public class CacheControlConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new org.springframework.web.servlet.HandlerInterceptor() {
            @Override
            public boolean preHandle(jakarta.servlet.http.HttpServletRequest request,
                                     jakarta.servlet.http.HttpServletResponse response, Object handler) {
                if (HttpMethod.GET.matches(request.getMethod())) {
                    response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
                }
                return true;
            }
        }).addPathPatterns("/api/**");
    }
}
