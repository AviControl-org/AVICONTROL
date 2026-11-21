package com.unimag.avicontrol.soporte;

import com.unimag.avicontrol.domain.model.compartido.ZonaNegocio;
import com.unimag.avicontrol.infrastructure.out.persistence.adapter.galpon.GalponPersistenceAdapter;
import com.unimag.avicontrol.infrastructure.out.persistence.adapter.galpon.HistorialPersistenceAdapter;
import com.unimag.avicontrol.infrastructure.out.persistence.adapter.lote.LotePersistenceAdapter;
import com.unimag.avicontrol.infrastructure.out.persistence.adapter.mantenimiento.AlertaMantenimientoPersistenceAdapter;
import com.unimag.avicontrol.infrastructure.out.persistence.adapter.sanitario.AlertaVaciadoPersistenceAdapter;
import com.unimag.avicontrol.infrastructure.out.persistence.adapter.sanitario.RegistroProcesamientoPersistenceAdapter;
import java.time.Clock;
import java.time.Instant;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base de las pruebas de persistencia (T022, T038, T070): PostgreSQL real en Testcontainers, esquema creado por
 * Flyway (V1) y validado por Hibernate, los adaptadores de salida disponibles y un {@code Clock} fijo.
 *
 * <p>Solo para pruebas de persistencia: verifican índices y consultas propias de PostgreSQL. Requiere Docker en
 * ejecución. El contenedor es único para toda la ejecución de pruebas (se arranca una vez y lo detiene Testcontainers
 * al terminar). Cada prueba corre en una transacción que se revierte; una prueba que necesite confirmar de verdad
 * (por ejemplo, de concurrencia) anota su clase o método con
 * {@code @Transactional(propagation = Propagation.NOT_SUPPORTED)} y limpia sus datos.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({PersistenciaTestBase.RelojFijo.class,
        GalponPersistenceAdapter.class,
        HistorialPersistenceAdapter.class,
        LotePersistenceAdapter.class,
        AlertaMantenimientoPersistenceAdapter.class,
        AlertaVaciadoPersistenceAdapter.class,
        RegistroProcesamientoPersistenceAdapter.class})
public abstract class PersistenciaTestBase {

    /** Mismo motor y versión que docker-compose.yml. */
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    /** Instante que devuelve el {@code Clock} de las pruebas de persistencia. */
    protected static final Instant AHORA = Instant.parse("2026-11-05T15:00:00Z");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void conectarBaseDeDatos(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registro.add("spring.datasource.username", POSTGRES::getUsername);
        registro.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @TestConfiguration
    static class RelojFijo {

        @Bean
        @Primary
        Clock reloj() {
            return Clock.fixed(AHORA, ZonaNegocio.BOGOTA);
        }
    }
}
