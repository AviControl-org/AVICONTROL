package com.unimag.avicontrol.domain.model.compartido;

import com.unimag.avicontrol.domain.exception.ValidacionNegocioException;
import java.util.Locale;
import java.util.regex.Pattern;

public record NombreNormalizado(String valor) {

    private static final int MAXIMO = 100;
    private static final Pattern PERMITIDO = Pattern.compile("^[\\p{L}\\p{N} _-]+$");
    private static final String MENSAJE =
            "El nombre es obligatorio, tiene máximo 100 caracteres y solo admite letras, números, espacios, guiones y guiones bajos.";

    public static NombreNormalizado de(String texto) {
        String recortado = texto == null ? "" : texto.strip();
        if (recortado.isEmpty() || recortado.length() > MAXIMO || !PERMITIDO.matcher(recortado).matches()) {
            throw new ValidacionNegocioException("NOMBRE_INVALIDO", MENSAJE);
        }
        return new NombreNormalizado(recortado);
    }

    public String clave() {
        return valor.toLowerCase(Locale.ROOT);
    }
}
