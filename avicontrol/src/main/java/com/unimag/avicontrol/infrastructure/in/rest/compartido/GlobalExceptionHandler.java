package com.unimag.avicontrol.infrastructure.in.rest.compartido;

import com.unimag.avicontrol.domain.exception.ConflictoException;
import com.unimag.avicontrol.domain.exception.ErrorDeCampo;
import com.unimag.avicontrol.domain.exception.NegocioException;
import com.unimag.avicontrol.domain.exception.RecursoNoEncontradoException;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.databind.exc.MismatchedInputException;

/**
 * Traduce toda excepción a un {@code ProblemDetail} (RFC 9457, {@code application/problem+json}) con los campos
 * {@code type}, {@code title}, {@code status}, {@code detail}, {@code instance}, {@code code} y, cuando aplica,
 * {@code fieldErrors}. Formato en arquitectura.md, «Formato de error».
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String BASE_TYPE = "https://avicontrol.unimag.edu.co/errors/";

    // ---------------------------------------------------------------- negocio

    @ExceptionHandler(NegocioException.class)
    public ResponseEntity<Object> negocio(NegocioException ex, WebRequest request) {
        HttpStatus status = ex instanceof RecursoNoEncontradoException ? HttpStatus.NOT_FOUND
                : ex instanceof ConflictoException ? HttpStatus.CONFLICT
                : HttpStatus.BAD_REQUEST;
        return respuesta(status, ex.codigo(), ex.getMessage(), ex.errores(), new HttpHeaders(), request);
    }

    /** Choque del {@code @Version}: otra operación modificó el mismo registro entre la lectura y la escritura. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Object> concurrencia(OptimisticLockingFailureException ex, WebRequest request) {
        return respuesta(HttpStatus.CONFLICT, "CONFLICTO_CONCURRENCIA", null, List.of(), new HttpHeaders(), request);
    }

    /** Error técnico: el detalle es genérico y la causa solo va al log. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> tecnico(Exception ex, WebRequest request) {
        log.error("Error no controlado en {}", rutaDe(request), ex);
        return errorTecnico(request, new HttpHeaders());
    }

    // ------------------------------------------------------------- validación

    /** Falla {@code @Valid} sobre el body: un campo da su código específico, varios dan {@code VALIDACION_FALLIDA}. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, ErrorDeCampo> porCampo = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            ErrorDeCampo nuevo = errorDeCampo(fe.getField(), codigoDeAnotacion(fe.getCode()));
            porCampo.merge(fe.getField(), nuevo, (a, b) -> prioridad(a.codigo()) <= prioridad(b.codigo()) ? a : b);
        }
        return validacion(new ArrayList<>(porCampo.values()), headers, request);
    }

    /** JSON mal formado o ilegible: {@code CUERPO_INVALIDO}; un valor de tipo erróneo en un campo se atribuye al campo. */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        for (Throwable causa = ex; causa != null; causa = causa.getCause() == causa ? null : causa.getCause()) {
            if (causa instanceof MismatchedInputException mismatch && !mismatch.getPath().isEmpty()) {
                String campo = mismatch.getPath().get(mismatch.getPath().size() - 1).getPropertyName();
                if (campo != null) {
                    return validacion(List.of(errorDeCampo(campo, codigoPorTipo(mismatch.getTargetType()))),
                            headers, request);
                }
            }
        }
        return respuesta(HttpStatus.BAD_REQUEST, "CUERPO_INVALIDO", null, List.of(), headers, request);
    }

    /** UUID o número mal formado en la ruta o en un query param. */
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        String nombre = ex instanceof MethodArgumentTypeMismatchException m ? m.getName() : ex.getPropertyName();
        return parametroInvalido(errorDeCampo(nombre == null ? "parametro" : nombre, "FORMATO_INVALIDO"),
                headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(MissingServletRequestParameterException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return parametroInvalido(errorDeCampo(ex.getParameterName(), "OBLIGATORIO"), headers, request);
    }

    /** Ruta, método o tipo de contenido que Spring MVC no soporta: se conserva su estado HTTP. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        if (statusCode.is5xxServerError()) {
            log.error("Error del framework en {}", rutaDe(request), ex);
            return errorTecnico(request, headers);
        }
        return respuesta(statusCode, "SOLICITUD_NO_SOPORTADA", null, List.of(), headers, request);
    }

    // ----------------------------------------------------------------- ayudas

    private ResponseEntity<Object> validacion(List<ErrorDeCampo> errores, HttpHeaders headers, WebRequest request) {
        CatalogoErrores.ReglaCampo regla = errores.size() == 1 ? CatalogoErrores.reglaDelCampo(errores.get(0).campo()) : null;
        if (regla == null) {
            return respuesta(HttpStatus.BAD_REQUEST, "VALIDACION_FALLIDA", null, errores, headers, request);
        }
        return respuesta(HttpStatus.BAD_REQUEST, regla.codigo(), regla.detalle(), errores, headers, request);
    }

    private ResponseEntity<Object> parametroInvalido(ErrorDeCampo error, HttpHeaders headers, WebRequest request) {
        return respuesta(HttpStatus.BAD_REQUEST, "PARAMETRO_INVALIDO", null, List.of(error), headers, request);
    }

    /** 500 en una consulta ({@code GET}) es {@code ERROR_CONSULTA}; en una escritura, {@code ERROR_INTERNO}. */
    private ResponseEntity<Object> errorTecnico(WebRequest request, HttpHeaders headers) {
        boolean consulta = request instanceof ServletWebRequest s && HttpMethod.GET.equals(s.getHttpMethod());
        return respuesta(HttpStatus.INTERNAL_SERVER_ERROR, consulta ? "ERROR_CONSULTA" : "ERROR_INTERNO", null,
                List.of(), headers, request);
    }

    private ResponseEntity<Object> respuesta(HttpStatusCode status, String code, String detalle,
            List<ErrorDeCampo> errores, HttpHeaders headers, WebRequest request) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status,
                detalle != null ? detalle : CatalogoErrores.detalle(code));
        problema.setType(URI.create(BASE_TYPE + code.toLowerCase(Locale.ROOT).replace('_', '-')));
        problema.setTitle(CatalogoErrores.titulo(code));
        String ruta = rutaDe(request);
        if (!ruta.isEmpty()) {
            problema.setInstance(URI.create(ruta));
        }
        problema.setProperty("code", code);
        if (!errores.isEmpty()) {
            problema.setProperty("fieldErrors", errores.stream().map(GlobalExceptionHandler::aMapa).toList());
        }
        return ResponseEntity.status(status).headers(headers).body(problema);
    }

    private static Map<String, String> aMapa(ErrorDeCampo e) {
        Map<String, String> campo = new LinkedHashMap<>();
        campo.put("field", e.campo());
        campo.put("code", e.codigo());
        campo.put("message", e.mensaje());
        return campo;
    }

    private static ErrorDeCampo errorDeCampo(String campo, String codigo) {
        return new ErrorDeCampo(campo, codigo, CatalogoErrores.mensajeCampo(codigo));
    }

    private static String rutaDe(WebRequest request) {
        return request instanceof ServletWebRequest s ? s.getRequest().getRequestURI() : "";
    }

    /** Anotación de Bean Validation → {@code fieldErrors.code}. */
    private static String codigoDeAnotacion(String anotacion) {
        if (anotacion == null) {
            return "VALOR_NO_PERMITIDO";
        }
        return switch (anotacion) {
            case "NotBlank", "NotNull", "NotEmpty" -> "OBLIGATORIO";
            case "Size", "Length" -> "LONGITUD_INVALIDA";
            case "Pattern", "Email" -> "FORMATO_INVALIDO";
            case "Positive", "PositiveOrZero" -> "ENTERO_POSITIVO";
            case "Min" -> "MINIMO_1";
            case "Max", "Range" -> "FUERA_DE_RANGO";
            default -> "VALOR_NO_PERMITIDO";
        };
    }

    /** Si un campo falla por varias reglas, se informa la más básica (obligatorio antes que longitud, etc.). */
    private static int prioridad(String codigo) {
        return switch (codigo) {
            case "OBLIGATORIO" -> 0;
            case "LONGITUD_INVALIDA" -> 1;
            case "FORMATO_INVALIDO" -> 2;
            default -> 3;
        };
    }

    /** Valor ilegible para el tipo del campo: números → entero positivo, enumeraciones → valor no permitido. */
    private static String codigoPorTipo(Class<?> tipo) {
        if (tipo == null) {
            return "FORMATO_INVALIDO";
        }
        if (tipo == long.class || tipo == int.class || Number.class.isAssignableFrom(tipo)) {
            return "ENTERO_POSITIVO";
        }
        return tipo.isEnum() ? "VALOR_NO_PERMITIDO" : "FORMATO_INVALIDO";
    }
}
