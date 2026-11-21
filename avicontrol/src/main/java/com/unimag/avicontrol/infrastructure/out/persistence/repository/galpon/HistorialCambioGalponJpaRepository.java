package com.unimag.avicontrol.infrastructure.out.persistence.repository.galpon;

import com.unimag.avicontrol.infrastructure.out.persistence.entity.galpon.HistorialCambioGalponEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HistorialCambioGalponJpaRepository extends JpaRepository<HistorialCambioGalponEntity, UUID> {
}
