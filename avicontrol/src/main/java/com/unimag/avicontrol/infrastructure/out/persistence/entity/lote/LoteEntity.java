package com.unimag.avicontrol.infrastructure.out.persistence.entity.lote;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "lote")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LoteEntity {

    @Id
    private UUID id;

    private UUID galponId;

    private String nombre;

    private LocalDate fechaIngreso;

    private long poblacionInicial;

    private long poblacionActual;

    private long costoTotal;

    private Instant desvinculadoEn;

    @Version
    private Long version;
}
