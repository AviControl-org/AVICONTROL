package com.unimag.avicontrol.infrastructure.out.persistence.repository.lote;

import com.unimag.avicontrol.infrastructure.out.persistence.entity.lote.LoteEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoteJpaRepository extends JpaRepository<LoteEntity, UUID> {

    Optional<LoteEntity> findFirstByGalponIdAndDesvinculadoEnIsNull(UUID galponId);

    @Query("select count(l) > 0 from LoteEntity l where lower(l.nombre) = :clave")
    boolean existsByClave(@Param("clave") String clave);

    boolean existsByGalponId(UUID galponId);

    List<LoteEntity> findByGalponIdOrderByFechaIngresoDescIdDesc(UUID galponId);
}
