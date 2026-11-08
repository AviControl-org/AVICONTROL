package com.unimag.avicontrol.application.usecase.galpon;

import com.unimag.avicontrol.application.port.in.galpon.RegistrarGalponUseCase;
import com.unimag.avicontrol.domain.exception.ConflictoException;
import com.unimag.avicontrol.domain.exception.ValidacionNegocioException;
import com.unimag.avicontrol.domain.model.compartido.NombreNormalizado;
import com.unimag.avicontrol.domain.model.galpon.Galpon;
import com.unimag.avicontrol.domain.port.out.galpon.GalponRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso del galpón. Esta clase crece con cada historia: por ahora solo registra (US1); consultar, actualizar
 * estado y editar se agregan con sus planes, junto con sus dependencias.
 */
@Service
public class GalponService implements RegistrarGalponUseCase {

    private final GalponRepository galpones;

    public GalponService(GalponRepository galpones) {
        this.galpones = galpones;
    }

    @Override
    @Transactional
    public Galpon registrar(RegistrarGalponUseCase.Comando comando) {
        NombreNormalizado nombre = NombreNormalizado.de(comando.nombre());
        if (comando.capacidad() <= 0) {
            throw new ValidacionNegocioException("CAPACIDAD_INVALIDA",
                    "La capacidad debe ser un número entero mayor que cero.");
        }
        if (galpones.existsByNombreClave(nombre.clave())) {
            throw ConflictoException.nombreDuplicado("Ya existe un galpón con ese nombre.");
        }
        // Si dos registros llegan a la vez, el índice único lower(nombre) lo rechaza y el adaptador
        // de persistencia lo traduce a este mismo NOMBRE_DUPLICADO.
        return galpones.save(Galpon.nuevo(nombre, comando.capacidad()));
    }
}
