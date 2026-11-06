package com.unimag.avicontrol.infrastructure.in.rest.compartido;

import java.util.Map;

/**
 * Textos fijos del contrato de errores: `title` de cada `code`, mensajes de `fieldErrors` y reglas de validación
 * por campo. Copiado de las tablas «Textos fijos de error» de los planes y de las convenciones de contrato.
 */
final class CatalogoErrores {

    /** `code` específico y `detail` cuando falla un único campo del body. */
    record ReglaCampo(String codigo, String detalle) {
    }

    private static final Map<String, String> TITULOS = Map.ofEntries(
            Map.entry("ALERTA_ACTIVA_EXISTENTE", "Alerta activa existente"),
            Map.entry("ALERTA_NO_ENCONTRADA", "Alerta no encontrada"),
            Map.entry("ALERTA_NO_PENDIENTE", "Alerta no pendiente"),
            Map.entry("CAPACIDAD_INVALIDA", "Capacidad inválida"),
            Map.entry("CAPACIDAD_NO_EDITABLE", "Capacidad no editable"),
            Map.entry("CONFLICTO_CONCURRENCIA", "Conflicto de concurrencia"),
            Map.entry("COSTO_TOTAL_INVALIDO", "Costo total inválido"),
            Map.entry("CUERPO_INVALIDO", "Cuerpo inválido"),
            Map.entry("DESCRIPCION_INVALIDA", "Descripción inválida"),
            Map.entry("ERROR_CONSULTA", "Error en la consulta"),
            Map.entry("ERROR_INTERNO", "Error interno"),
            Map.entry("ESTADO_ESPERADO_CAMBIADO", "Estado desactualizado"),
            Map.entry("ESTADO_INVALIDO", "Estado inválido"),
            Map.entry("FECHA_INGRESO_FUTURA", "Fecha de ingreso inválida"),
            Map.entry("FECHA_INGRESO_INVALIDA", "Fecha de ingreso inválida"),
            Map.entry("GALPON_NO_DISPONIBLE", "Galpón no disponible"),
            Map.entry("GALPON_NO_ENCONTRADO", "Galpón no encontrado"),
            Map.entry("GALPON_REQUERIDO", "Galpón requerido"),
            Map.entry("NOMBRE_DUPLICADO", "Nombre duplicado"),
            Map.entry("NOMBRE_INVALIDO", "Nombre inválido"),
            Map.entry("ORIGEN_NO_AUTORIZADO", "Origen no autorizado"),
            Map.entry("PARAMETRO_INVALIDO", "Parámetro inválido"),
            Map.entry("POBLACION_INICIAL_INVALIDA", "Población inicial inválida"),
            Map.entry("SIN_CAMBIOS", "Sin cambios"),
            Map.entry("SOLICITUD_NO_SOPORTADA", "Solicitud no soportada"),
            Map.entry("TECNICO_REQUERIDO", "Técnico requerido"),
            Map.entry("TRANSICION_NO_PERMITIDA", "Transición no permitida"),
            Map.entry("VALIDACION_FALLIDA", "Validación fallida"));

    /** `detail` de los errores que genera el propio manejador (los de negocio traen su mensaje). */
    private static final Map<String, String> DETALLES = Map.of(
            "CONFLICTO_CONCURRENCIA",
            "Otro usuario modificó el registro al mismo tiempo. Actualiza la información e inténtalo de nuevo.",
            "CUERPO_INVALIDO", "El cuerpo de la solicitud no es un JSON válido.",
            "ERROR_CONSULTA", "No fue posible obtener los datos. Puedes reintentar la consulta.",
            "ERROR_INTERNO", "No fue posible completar la operación. Intenta de nuevo.",
            "PARAMETRO_INVALIDO", "Revisa los parámetros de la solicitud.",
            "SOLICITUD_NO_SOPORTADA", "La solicitud no es soportada por este servicio.",
            "VALIDACION_FALLIDA", "Hay campos con datos inválidos. Revisa fieldErrors.");

    /** Mensaje de cada valor de `fieldErrors.code`. */
    private static final Map<String, String> MENSAJES_CAMPO = Map.of(
            "OBLIGATORIO", "Es obligatorio",
            "LONGITUD_INVALIDA", "Longitud fuera del rango permitido",
            "FORMATO_INVALIDO", "Formato no válido",
            "ENTERO_POSITIVO", "Debe ser un entero mayor que cero",
            "VALOR_NO_PERMITIDO", "Valor no permitido",
            "MINIMO_1", "Debe ser mayor o igual a 1",
            "FUERA_DE_RANGO", "Fuera del rango permitido",
            "DUPLICADO", "El nombre ya está en uso");

    private static final String ESTADOS_GALPON =
            "DISPONIBLE, VACIADO_SANITARIO, PRODUCTIVO, EN_COSECHA, MANTENIMIENTO, AISLAMIENTO";

    private static final Map<String, ReglaCampo> REGLAS_CAMPO = Map.ofEntries(
            Map.entry("nombre", new ReglaCampo("NOMBRE_INVALIDO",
                    "El nombre es obligatorio, tiene máximo 100 caracteres y solo admite letras, números, espacios, guiones y guiones bajos.")),
            Map.entry("capacidad", new ReglaCampo("CAPACIDAD_INVALIDA",
                    "La capacidad debe ser un número entero mayor que cero.")),
            Map.entry("descripcion", new ReglaCampo("DESCRIPCION_INVALIDA",
                    "La descripción es obligatoria y tiene máximo 500 caracteres.")),
            Map.entry("galponId", new ReglaCampo("GALPON_REQUERIDO", "Debes seleccionar un galpón.")),
            Map.entry("tecnicoId", new ReglaCampo("TECNICO_REQUERIDO",
                    "Debes indicar el técnico que genera la alerta.")),
            Map.entry("fechaIngreso", new ReglaCampo("FECHA_INGRESO_INVALIDA",
                    "La fecha de ingreso es obligatoria y debe tener el formato yyyy-MM-dd.")),
            Map.entry("poblacionInicial", new ReglaCampo("POBLACION_INICIAL_INVALIDA",
                    "La población inicial debe ser un número entero mayor que cero.")),
            Map.entry("costoTotal", new ReglaCampo("COSTO_TOTAL_INVALIDO",
                    "El costo total debe ser un número entero mayor que cero, en pesos colombianos.")),
            Map.entry("estadoEsperado", new ReglaCampo("ESTADO_INVALIDO",
                    "El estado indicado no es válido. Usa uno de: " + ESTADOS_GALPON + ".")),
            Map.entry("estadoDestino", new ReglaCampo("ESTADO_INVALIDO",
                    "El estado indicado no es válido. Usa uno de: " + ESTADOS_GALPON + ".")),
            Map.entry("estado", new ReglaCampo("ESTADO_INVALIDO",
                    "Solo se puede cambiar el estado de la alerta a ATENDIDA.")));

    private CatalogoErrores() {
    }

    /** Título fijo del `code`; si no está catalogado, se deriva del propio código. */
    static String titulo(String code) {
        String titulo = TITULOS.get(code);
        if (titulo != null) {
            return titulo;
        }
        String texto = code.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }

    /** `detail` de un error generado por el manejador. */
    static String detalle(String code) {
        return DETALLES.getOrDefault(code, DETALLES.get("ERROR_INTERNO"));
    }

    static String mensajeCampo(String codigoCampo) {
        return MENSAJES_CAMPO.getOrDefault(codigoCampo, "Valor no válido");
    }

    /** Regla de un campo del body; `null` si el campo no tiene un código específico. */
    static ReglaCampo reglaDelCampo(String campo) {
        return REGLAS_CAMPO.get(campo);
    }
}
