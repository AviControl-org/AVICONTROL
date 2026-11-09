package com.unimag.avicontrol.infrastructure.out.persistence.repository.sanitario;

import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.time.Instant;
import com.unimag.avicontrol.infrastructure.out.persistence.entity.sanitario.AlertaVaciadoSanitarioEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertaVaciadoSanitarioJpaRepository extends JpaRepository<AlertaVaciadoSanitarioEntity, UUID> {

    /** Se compara con la última alerta aceptada del galpón: un galpón puede haber pasado por varios vaciados. */
    @Query(value = """
            SELECT g.id
            FROM galpon g
            WHERE g.estado = 'VACIADO_SANITARIO'
              AND (SELECT max(a.fecha_hora_recepcion)
                   FROM alerta_vaciado_sanitario a
                   WHERE a.galpon_id = g.id) <= :limite
            """, nativeQuery = true)
    List<UUID> findGalponIdsConVaciadoCumplido(@Param("limite") Instant limite);
}
