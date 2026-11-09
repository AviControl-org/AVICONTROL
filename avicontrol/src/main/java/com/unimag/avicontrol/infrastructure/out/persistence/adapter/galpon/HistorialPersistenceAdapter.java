package com.unimag.avicontrol.infrastructure.out.persistence.adapter.galpon;

import org.springframework.data.domain.Sort;
import org.springframework.data.domain.PageRequest;
import java.util.UUID;
import com.unimag.avicontrol.domain.model.compartido.Pagina;
import com.unimag.avicontrol.domain.model.galpon.HistorialCambioGalpon;
import com.unimag.avicontrol.domain.model.galpon.HistorialEstado;
import com.unimag.avicontrol.domain.port.out.galpon.HistorialCambioGalponRepository;
import com.unimag.avicontrol.domain.port.out.galpon.HistorialEstadoRepository;
import com.unimag.avicontrol.infrastructure.out.persistence.mapper.galpon.HistorialPersistenceMapper;
import com.unimag.avicontrol.infrastructure.out.persistence.repository.galpon.HistorialCambioGalponJpaRepository;
import com.unimag.avicontrol.infrastructure.out.persistence.repository.galpon.HistorialEstadoJpaRepository;
import org.springframework.stereotype.Component;

/** Un solo adaptador para los dos historiales: ambos son solo de inserción y consulta. */
@Component
public class HistorialPersistenceAdapter implements HistorialEstadoRepository, HistorialCambioGalponRepository {

    private final HistorialEstadoJpaRepository estados;
    private final HistorialCambioGalponJpaRepository cambios;

    public HistorialPersistenceAdapter(HistorialEstadoJpaRepository estados,
                                       HistorialCambioGalponJpaRepository cambios) {
        this.estados = estados;
        this.cambios = cambios;
    }

    @Override
    public HistorialEstado save(HistorialEstado historial) {
        return HistorialPersistenceMapper.aDominio(estados.save(HistorialPersistenceMapper.aEntidad(historial)));
    }

    @Override
    public HistorialCambioGalpon save(HistorialCambioGalpon cambio) {
        return HistorialPersistenceMapper.aDominio(cambios.save(HistorialPersistenceMapper.aEntidad(cambio)));
    }

    @Override
    public Pagina<HistorialEstado> findByGalponId(UUID galponId, int pagina, int tamanio) {
        var peticion = PageRequest.of(pagina - 1, tamanio,
                Sort.by(Sort.Order.desc("fechaHora"), Sort.Order.desc("id")));
        var resultado = estados.findByGalponId(galponId, peticion);
        return new Pagina<>(resultado.getContent().stream().map(HistorialPersistenceMapper::aDominio).toList(),
                pagina, tamanio, resultado.getTotalElements());
    }
}
