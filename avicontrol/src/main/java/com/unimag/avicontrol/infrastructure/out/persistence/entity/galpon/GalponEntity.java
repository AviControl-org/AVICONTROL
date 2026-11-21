package com.unimag.avicontrol.infrastructure.out.persistence.entity.galpon;

import com.unimag.avicontrol.domain.model.galpon.EstadoGalpon;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "galpon")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GalponEntity {

    @Id
    private UUID id;

    private String nombre;

    private long capacidad;

    @Enumerated(EnumType.STRING)
    private EstadoGalpon estado;

    @Version
    private Long version;
}
