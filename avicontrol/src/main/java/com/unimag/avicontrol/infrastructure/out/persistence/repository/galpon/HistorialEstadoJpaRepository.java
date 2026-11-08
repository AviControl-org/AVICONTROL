package com.unimag.avicontrol.infrastructure.out.persistence.repository.galpon;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import com.unimag.avicontrol.infrastructure.out.persistence.entity.galpon.HistorialEstadoEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HistorialEstadoJpaRepository extends JpaRepository<HistorialEstadoEntity, UUID> {

    Page<HistorialEstadoEntity> findByGalponId(UUID galponId, Pageable pageable);
}
