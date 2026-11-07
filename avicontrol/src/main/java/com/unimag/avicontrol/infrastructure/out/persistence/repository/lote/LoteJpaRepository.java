package com.unimag.avicontrol.infrastructure.out.persistence.repository.lote;

import com.unimag.avicontrol.infrastructure.out.persistence.entity.lote.LoteEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoteJpaRepository extends JpaRepository<LoteEntity, UUID> {

    Optional<LoteEntity> findFirstByGalponIdAndDesvinculadoEnIsNull(UUID galponId);
}
