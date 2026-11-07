package com.unimag.avicontrol.domain.exception;

/** Error asociado a un campo o parámetro; el manejador lo expone como `{field, code, message}` en `fieldErrors`. */
public record ErrorDeCampo(String campo, String codigo, String mensaje) {
}
