package com.unimag.avicontrol.infrastructure.in.rest.lote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unimag.avicontrol.application.port.in.lote.ListarLotesUseCase;
import com.unimag.avicontrol.application.port.in.lote.RegistrarLoteUseCase;
import com.unimag.avicontrol.application.port.in.lote.RegistrarLoteUseCase.Comando;
import com.unimag.avicontrol.domain.exception.ConflictoException;
import com.unimag.avicontrol.domain.exception.RecursoNoEncontradoException;
import com.unimag.avicontrol.domain.exception.ValidacionNegocioException;
import com.unimag.avicontrol.domain.model.compartido.Pagina;
import com.unimag.avicontrol.domain.model.lote.Lote;
import com.unimag.avicontrol.infrastructure.config.CacheControlConfig;
import com.unimag.avicontrol.infrastructure.config.JacksonConfig;
import com.unimag.avicontrol.infrastructure.in.rest.compartido.GlobalExceptionHandler;
import com.unimag.avicontrol.infrastructure.in.rest.controller.lote.LoteController;
import com.unimag.avicontrol.soporte.FixtureJson;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Contrato de POST y GET /api/v1/lotes (US2): compara las respuestas con los fixtures compartidos. */
@WebMvcTest(LoteController.class)
@Import({GlobalExceptionHandler.class, JacksonConfig.class, CacheControlConfig.class})
class LoteControllerTest {

    private static final String RUTA = "/api/v1/lotes";
    private static final UUID GALPON_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID LOTE_ID = UUID.fromString("2fc03a21-84c4-4c83-bb15-6607bb75cb90");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private RegistrarLoteUseCase registrarLote;

    @MockitoBean
    private ListarLotesUseCase listarLotes;

    private MvcResult enviar(String json) throws Exception {
        return mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(json)).andReturn();
    }

    /** Body válido al que se le cambia un solo campo; {@code null} en el valor omite el campo. */
    private static String conCampo(String campo, String valorJson) {
        String[][] campos = {
                {"galponId", "\"" + GALPON_ID + "\""},
                {"nombre", "\"Lote A1\""},
                {"fechaIngreso", "\"2026-10-05\""},
                {"poblacionInicial", "1000"},
                {"costoTotal", "5000000"}};
        StringBuilder json = new StringBuilder("{");
        for (String[] c : campos) {
            String valor = c[0].equals(campo) ? valorJson : c[1];
            if (valor != null) {
                json.append(json.length() > 1 ? "," : "").append('"').append(c[0]).append("\":").append(valor);
            }
        }
        return json.append('}').toString();
    }

    private static Lote loteActivo() {
        return new Lote(LOTE_ID, GALPON_ID, "Lote A1", LocalDate.of(2026, 10, 5), 1000, 1000, 5_000_000, null, 0L);
    }

    // ------------------------------------------------------------------ POST

    @Test
    void registroExitosoDevuelve201ConLocationYElLoteConPoblacionActualIgualALaInicial() throws Exception {
        when(registrarLote.registrar(any())).thenReturn(loteActivo());

        MvcResult resultado = mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON)
                        .content(FixtureJson.texto("lote-registrar-request.json")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", RUTA + "/" + LOTE_ID))
                .andReturn();

        FixtureJson.assertCoincide("lote-registrar-response.json", resultado);
        verify(registrarLote).registrar(new Comando(GALPON_ID, "Lote A1", LocalDate.of(2026, 10, 5), 1000, 5_000_000));
    }

    @Test
    void nombreDuplicadoDevuelve409() throws Exception {
        when(registrarLote.registrar(any()))
                .thenThrow(ConflictoException.nombreDuplicado("Ya existe un lote con ese nombre."));

        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(conCampo("nombre", "\"lote a1\"")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOMBRE_DUPLICADO"))
                .andExpect(jsonPath("$.detail").value("Ya existe un lote con ese nombre."))
                .andExpect(jsonPath("$.instance").value(RUTA))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("nombre"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("DUPLICADO"));
    }

    @Test
    void galponNoDisponibleDevuelve409ConElFixtureDeError() throws Exception {
        when(registrarLote.registrar(any())).thenThrow(new ConflictoException("GALPON_NO_DISPONIBLE",
                "El galpón seleccionado no está disponible para recibir un lote."));

        MvcResult resultado = enviar(FixtureJson.texto("lote-registrar-request.json"));

        assertThat(resultado.getResponse().getStatus()).isEqualTo(409);
        FixtureJson.assertCoincide("error-galpon-no-disponible.json", resultado);
    }

    @Test
    void poblacionQueExcedeLaCapacidadDevuelve409ConElFixtureDeError() throws Exception {
        when(registrarLote.registrar(any())).thenThrow(new ConflictoException("POBLACION_EXCEDE_CAPACIDAD",
                "La población inicial no puede superar la capacidad del galpón."));

        MvcResult resultado = enviar(FixtureJson.texto("lote-registrar-request.json"));

        assertThat(resultado.getResponse().getStatus()).isEqualTo(409);
        FixtureJson.assertCoincide("error-poblacion-excede-capacidad.json", resultado);
    }

    @Test
    void galponInexistenteDevuelve404() throws Exception {
        when(registrarLote.registrar(any())).thenThrow(
                new RecursoNoEncontradoException("GALPON_NO_ENCONTRADO", "El galpón seleccionado no existe."));

        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON)
                        .content(FixtureJson.texto("lote-registrar-request.json")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GALPON_NO_ENCONTRADO"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void fechaDeIngresoFuturaDevuelve400() throws Exception {
        when(registrarLote.registrar(any())).thenThrow(new ValidacionNegocioException("FECHA_INGRESO_FUTURA",
                "La fecha de ingreso no puede ser posterior a hoy."));

        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON)
                        .content(conCampo("fechaIngreso", "\"2999-01-01\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FECHA_INGRESO_FUTURA"))
                .andExpect(jsonPath("$.title").value("Fecha de ingreso inválida"));
    }

    @ParameterizedTest(name = "poblacionInicial {0} devuelve 400 POBLACION_INICIAL_INVALIDA")
    @ValueSource(strings = {"0", "-1", "10.5", "\"abc\""})
    void poblacionInicialQueNoEsEnteroPositivoDevuelve400(String valor) throws Exception {
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(conCampo("poblacionInicial", valor)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("POBLACION_INICIAL_INVALIDA"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("poblacionInicial"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("ENTERO_POSITIVO"));

        verifyNoInteractions(registrarLote);
    }

    @ParameterizedTest(name = "costoTotal {0} devuelve 400 COSTO_TOTAL_INVALIDO")
    @ValueSource(strings = {"0", "-1", "10.5", "\"abc\""})
    void costoTotalQueNoEsEnteroPositivoDevuelve400(String valor) throws Exception {
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(conCampo("costoTotal", valor)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COSTO_TOTAL_INVALIDO"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("costoTotal"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("ENTERO_POSITIVO"));

        verifyNoInteractions(registrarLote);
    }

    @Test
    void galponNoSeleccionadoDevuelve400() throws Exception {
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(conCampo("galponId", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GALPON_REQUERIDO"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("OBLIGATORIO"));

        verifyNoInteractions(registrarLote);
    }

    @ParameterizedTest(name = "nombre {0} devuelve 400 NOMBRE_INVALIDO")
    @ValueSource(strings = {"\"\"", "\"   \"", "\"Lote #1\"", "null"})
    void nombreVacioOConCaracteresNoPermitidosDevuelve400(String valor) throws Exception {
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(conCampo("nombre", valor)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOMBRE_INVALIDO"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("nombre"));

        verifyNoInteractions(registrarLote);
    }

    @Test
    void nombreDeMasDeCienCaracteresDevuelve400() throws Exception {
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON)
                        .content(conCampo("nombre", "\"" + "A".repeat(101) + "\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOMBRE_INVALIDO"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("LONGITUD_INVALIDA"));
    }

    @ParameterizedTest(name = "fechaIngreso {0} devuelve 400 FECHA_INGRESO_INVALIDA")
    @ValueSource(strings = {"\"05/10/2026\"", "\"ayer\"", "null"})
    void fechaAusenteOConFormatoDistintoDeAnioMesDiaDevuelve400(String valor) throws Exception {
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(conCampo("fechaIngreso", valor)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FECHA_INGRESO_INVALIDA"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("fechaIngreso"));

        verifyNoInteractions(registrarLote);
    }

    @Test
    void variosCamposInvalidosDevuelven400ValidacionFallida() throws Exception {
        String json = conCampo("poblacionInicial", "0").replace("5000000", "-1");

        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDACION_FALLIDA"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(2));
    }

    @Test
    void jsonMalFormadoDevuelve400CuerpoInvalido() throws Exception {
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content("{\"nombre\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CUERPO_INVALIDO"));
    }

    // ------------------------------------------------------------------- GET

    @Test
    void listadoDevuelve200ConElFixtureYSinCache() throws Exception {
        Lote anterior = new Lote(UUID.fromString("9b2e5c14-3f7a-4d1e-8a60-1c2d3e4f5a6b"),
                UUID.fromString("6ba7b810-9dad-41d1-80b4-00c04fd430c8"), "Lote Z9", LocalDate.of(2026, 8, 1),
                900, 870, 4_200_000, Instant.parse("2026-09-20T13:15:00Z"), 0L);
        when(listarLotes.listar(1, 10)).thenReturn(new Pagina<>(List.of(loteActivo(), anterior), 1, 10, 2));

        MvcResult resultado = mvc.perform(get(RUTA))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();

        FixtureJson.assertCoincide("lote-lista-response.json", resultado);
    }

    @Test
    void listadoSinLotesDevuelveUnaPaginaVacia() throws Exception {
        when(listarLotes.listar(1, 10)).thenReturn(new Pagina<>(List.of(), 1, 10, 0));

        mvc.perform(get(RUTA + "?page=1"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.contenido").isEmpty())
                .andExpect(jsonPath("$.totalElementos").value(0))
                .andExpect(jsonPath("$.totalPaginas").value(0));
    }

    @Test
    void listadoPideLaPaginaIndicada() throws Exception {
        when(listarLotes.listar(3, 10)).thenReturn(new Pagina<>(List.of(), 3, 10, 25));

        mvc.perform(get(RUTA + "?page=3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagina").value(3))
                .andExpect(jsonPath("$.totalPaginas").value(3));
    }

    @Test
    void paginaMenorQueUnoDevuelve400ParametroInvalido() throws Exception {
        mvc.perform(get(RUTA + "?page=0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PARAMETRO_INVALIDO"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("page"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("MINIMO_1"));

        verifyNoInteractions(listarLotes);
    }

    @Test
    void paginaNoNumericaDevuelve400ParametroInvalido() throws Exception {
        mvc.perform(get(RUTA + "?page=abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PARAMETRO_INVALIDO"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("page"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("FORMATO_INVALIDO"));

        verifyNoInteractions(listarLotes);
    }
}
