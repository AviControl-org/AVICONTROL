package com.unimag.avicontrol.infrastructure.out.persistence.repository.galpon;

import com.unimag.avicontrol.infrastructure.out.persistence.entity.galpon.GalponEntity;
import com.unimag.avicontrol.domain.model.galpon.EstadoGalpon;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GalponJpaRepository extends JpaRepository<GalponEntity, UUID> {

    /** `SELECT ... FOR UPDATE`: serializa los cambios de estado concurrentes del mismo galpón. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from GalponEntity g where g.id = :id")
    Optional<GalponEntity> findByIdForUpdate(@Param("id") UUID id);

    @Query("select count(g) > 0 from GalponEntity g where lower(g.nombre) = :clave")
    boolean existsByClave(@Param("clave") String clave);

    @Query("select count(g) > 0 from GalponEntity g where lower(g.nombre) = :clave and g.id <> :id")
    boolean existsByClaveAndIdNot(@Param("clave") String clave, @Param("id") UUID id);

    /** {@code patron} ya viene en minúsculas, con los comodines escapados y rodeado de {@code %}. */
    @Query("""
            select g from GalponEntity g
            where g.estado in :estados and lower(g.nombre) like :patron escape '\\'
            """)
    Page<GalponEntity> buscar(@Param("estados") Collection<EstadoGalpon> estados, @Param("patron") String patron,
                              Pageable pageable);
}
