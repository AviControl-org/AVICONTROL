package com.unimag.avicontrol.infrastructure.in.rest.mantenimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unimag.avicontrol.application.port.in.mantenimiento.AlertaMantenimientoUseCase;
import com.unimag.avicontrol.application.port.in.mantenimiento.AlertaMantenimientoUseCase.Comando;
import com.unimag.avicontrol.application.port.in.mantenimiento.AlertaMantenimientoUseCase.ResultadoAtencion;
import com.unimag.avicontrol.domain.exception.ConflictoException;
import com.unimag.avicontrol.domain.exception.RecursoNoEncontradoException;
import com.unimag.avicontrol.domain.model.compartido.Pagina;
import com.unimag.avicontrol.domain.model.galpon.EstadoGalpon;
import com.unimag.avicontrol.domain.model.mantenimiento.AlertaMantenimiento;
import com.unimag.avicontrol.domain.model.mantenimiento.EstadoAlertaMantenimiento;
import com.unimag.avicontrol.infrastructure.config.CacheControlConfig;
import com.unimag.avicontrol.infrastructure.config.JacksonConfig;
import com.unimag.avicontrol.infrastructure.in.rest.compartido.GlobalExceptionHandler;
import com.unimag.avicontrol.infrastructure.in.rest.controller.mantenimiento.AlertaMantenimientoController;
import com.unimag.avicontrol.soporte.FixtureJson;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Contrato de /api/v1/alertas-mantenimiento (US8): compara las respuestas con los fixtures compartidos. */
@WebMvcTest(AlertaMantenimientoController.class)
@Import({GlobalExceptionHandler.class, JacksonConfig.class, CacheControlConfig.class})
class AlertaMantenimientoControllerTest {

    private static final String RUTA = "/api/v1/alertas-mantenimiento";
    private static final UUID GALPON_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID ALERTA_ID = UUID.fromString("7a4f9e10-2b3c-4d5e-8f60-1a2b3c4d5e6f");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AlertaMantenimientoUseCase alertas;

    /** 2026-10-05 10:20 en America/Bogota. */
    private static AlertaMantenimiento pendiente() {
        return new AlertaMantenimiento(ALERTA_ID, GALPON_ID, "Reparar sistema de ventilación",
                Instant.parse("2026-10-05T15:20:00Z"), "tecnico-01", EstadoAlertaMantenimiento.PENDIENTE,
                "Alta", "Ventilación");
    }

    private static String cuerpo(String galponId, String descripcion, String tecnicoId) {
        return "{\"galponId\": " + galponId + ", \"descripcion\": " + descripcion
                + ", \"severidad\": \"Alta\", \"tipo\": \"Ventilación\", \"tecnicoId\": " + tecnicoId + "}";
    }

    private static final String GALPON = "\"" + GALPON_ID + "\"";

    // ---------------------------------------------------------------- POST

    @Test
    void generarDevuelve201ConLocationYElFixture() throws Exception {
        when(alertas.generar(new Comando(GALPON_ID, "Reparar sistema de ventilación", "Alta", "Ventilación",
                "tecnico-01"))).thenReturn(pendiente());

        MvcResult resultado = mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON)
                        .content(FixtureJson.texto("alerta-mantenimiento-request.json")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", RUTA + "/" + ALERTA_ID))
                .andReturn();

        FixtureJson.assertCoincide("alerta-mantenimiento-response.json", resultado);
    }

    @Test
    void generarSinSeveridadNiTipoLosOmiteDeLaRespuesta() throws Exception {
        AlertaMantenimiento sinOpcionales = new AlertaMantenimiento(ALERTA_ID, GALPON_ID, "Revisar bebederos",
                Instant.parse("2026-10-05T15:20:00Z"), "tecnico-01", EstadoAlertaMantenimiento.PENDIENTE, null, null);
        when(alertas.generar(new Comando(GALPON_ID, "Revisar bebederos", null, null, "tecnico-01")))
                .thenReturn(sinOpcionales);

        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"galponId\": " + GALPON + ", \"descripcion\": \"Revisar bebederos\","
                                + " \"tecnicoId\": \"tecnico-01\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.severidad").doesNotExist())
                .andExpect(jsonPath("$.tipo").doesNotExist());
    }

    @Test
    void generarRecortaLosEspaciosDeDescripcionYTecnico() throws Exception {
        when(alertas.generar(new Comando(GALPON_ID, "Reparar", "Alta", "Ventilación", "tecnico-01")))
                .thenReturn(pendiente());

        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(GALPON, "\"  Reparar  \"", "\"  tecnico-01 \"")))
                .andExpect(status().isCreated());

        verify(alertas).generar(new Comando(GALPON_ID, "Reparar", "Alta", "Ventilación", "tecnico-01"));
    }

    @Test
    void descripcionVaciaDevuelve400DescripcionInvalida() throws Exception {
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(GALPON, "\"\"", "\"tecnico-01\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DESCRIPCION_INVALIDA"))
                .andExpect(jsonPath("$.detail").value("La descripción es obligatoria y tiene máximo 500 caracteres."))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("descripcion"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("OBLIGATORIO"));

        verifyNoInteractions(alertas);
    }

    @Test
    void descripcionSoloConEspaciosDevuelve400DescripcionInvalida() throws Exception {
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(GALPON, "\"   \"", "\"tecnico-01\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DESCRIPCION_INVALIDA"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("OBLIGATORIO"));

        verifyNoInteractions(alertas);
    }

    @Test
    void descripcionDe501CaracteresDevuelve400DescripcionInvalida() throws Exception {
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(GALPON, "\"" + "a".repeat(501) + "\"", "\"tecnico-01\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DESCRIPCION_INVALIDA"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("descripcion"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("LONGITUD_INVALIDA"));

        verifyNoInteractions(alertas);
    }

    @Test
    void descripcionDe500CaracteresSeAcepta() throws Exception {
        String larga = "a".repeat(500);
        when(alertas.generar(new Comando(GALPON_ID, larga, "Alta", "Ventilación", "tecnico-01")))
                .thenReturn(pendiente());

        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(GALPON, "\"" + larga + "\"", "\"tecnico-01\"")))
                .andExpect(status().isCreated());
    }

    @Test
    void sinTecnicoDevuelve400TecnicoRequerido() throws Exception {
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(GALPON, "\"Reparar\"", "\"\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TECNICO_REQUERIDO"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("tecnicoId"));

        verifyNoInteractions(alertas);
    }

    @Test
    void sinGalponDevuelve400GalponRequerido() throws Exception {
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("null", "\"Reparar\"", "\"tecnico-01\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GALPON_REQUERIDO"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("galponId"));

        verifyNoInteractions(alertas);
    }

    @Test
    void dosCamposInvalidosADaLaVezDevuelven400ValidacionFallida() throws Exception {
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(GALPON, "\"\"", "\"\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDACION_FALLIDA"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(2))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("descripcion"))
                .andExpect(jsonPath("$.fieldErrors[1].field").value("tecnicoId"));

        verifyNoInteractions(alertas);
    }

    @Test
    void galponQueNoEsUuidDevuelve400() throws Exception {
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("\"abc\"", "\"Reparar\"", "\"tecnico-01\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("galponId"));

        verifyNoInteractions(alertas);
    }

    @Test
    void cuerpoQueNoEsJsonDevuelve400CuerpoInvalido() throws Exception {
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content("{\"galponId\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CUERPO_INVALIDO"));

        verifyNoInteractions(alertas);
    }

    @Test
    void galponInexistenteDevuelve404() throws Exception {
        when(alertas.generar(new Comando(GALPON_ID, "Reparar sistema de ventilación", "Alta", "Ventilación",
                "tecnico-01"))).thenThrow(
                new RecursoNoEncontradoException("GALPON_NO_ENCONTRADO", "El galpón seleccionado no existe."));

        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON)
                        .content(FixtureJson.texto("alerta-mantenimiento-request.json")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GALPON_NO_ENCONTRADO"))
                .andExpect(jsonPath("$.detail").value("El galpón seleccionado no existe."));
    }

    @Test
    void galponNoDisponibleDevuelve409() throws Exception {
        when(alertas.generar(new Comando(GALPON_ID, "Reparar sistema de ventilación", "Alta", "Ventilación",
                "tecnico-01"))).thenThrow(new ConflictoException("GALPON_NO_DISPONIBLE",
                "Solo se pueden generar alertas para galpones disponibles."));

        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON)
                        .content(FixtureJson.texto("alerta-mantenimiento-request.json")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GALPON_NO_DISPONIBLE"))
                .andExpect(jsonPath("$.detail").value("Solo se pueden generar alertas para galpones disponibles."));
    }

    @Test
    void alertaActivaExistenteDevuelve409ConElFixture() throws Exception {
        when(alertas.generar(new Comando(GALPON_ID, "Reparar sistema de ventilación", "Alta", "Ventilación",
                "tecnico-01"))).thenThrow(
                new ConflictoException("ALERTA_ACTIVA_EXISTENTE", "Ya existe una alerta activa para ese galpón."));

        MvcResult resultado = mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON)
                        .content(FixtureJson.texto("alerta-mantenimiento-request.json")))
                .andExpect(status().isConflict())
                .andReturn();

        FixtureJson.assertCoincide("error-alerta-activa-existente.json", resultado);
    }

    // ----------------------------------------------------------------- GET

    @Test
    void listarSinParametrosPideLasPendientesDeLaPrimeraPaginaYDevuelveElFixture() throws Exception {
        when(alertas.listar(EstadoAlertaMantenimiento.PENDIENTE, null, 1, 20))
                .thenReturn(new Pagina<>(List.of(pendiente()), 1, 20, 1));

        MvcResult resultado = mvc.perform(get(RUTA))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();

        FixtureJson.assertCoincide("alerta-mantenimiento-lista-response.json", resultado);
    }

    @Test
    void listarAtendidasDeUnGalponPasaLosFiltrosAlPuerto() throws Exception {
        AlertaMantenimiento atendida = pendiente().atendida();
        when(alertas.listar(EstadoAlertaMantenimiento.ATENDIDA, GALPON_ID, 2, 5))
                .thenReturn(new Pagina<>(List.of(atendida), 2, 5, 6));

        mvc.perform(get(RUTA).param("estado", "ATENDIDA").param("galponId", GALPON_ID.toString())
                        .param("page", "2").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].estado").value("ATENDIDA"))
                .andExpect(jsonPath("$.pagina").value(2))
                .andExpect(jsonPath("$.tamanio").value(5))
                .andExpect(jsonPath("$.totalElementos").value(6))
                .andExpect(jsonPath("$.totalPaginas").value(2));
    }

    @Test
    void listarSinAlertasDevuelve200ConContenidoVacio() throws Exception {
        when(alertas.listar(EstadoAlertaMantenimiento.PENDIENTE, GALPON_ID, 1, 20))
                .thenReturn(new Pagina<>(List.of(), 1, 20, 0));

        mvc.perform(get(RUTA).param("estado", "PENDIENTE").param("galponId", GALPON_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido.length()").value(0))
                .andExpect(jsonPath("$.totalElementos").value(0))
                .andExpect(jsonPath("$.totalPaginas").value(0));
    }

    @Test
    void estadoFueraDelCatalogoDevuelve400ParametroInvalido() throws Exception {
        mvc.perform(get(RUTA).param("estado", "ZZZ"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PARAMETRO_INVALIDO"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("estado"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("VALOR_NO_PERMITIDO"))
                .andExpect(jsonPath("$.fieldErrors[0].message")
                        .value("Debe ser uno de: PENDIENTE, ATENDIDA, ELIMINADA"));

        verifyNoInteractions(alertas);
    }

    @Test
    void sizeFueraDeRangoDevuelve400() throws Exception {
        mvc.perform(get(RUTA).param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PARAMETRO_INVALIDO"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("size"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("FUERA_DE_RANGO"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("Debe estar entre 1 y 100"));

        verifyNoInteractions(alertas);
    }

    @Test
    void pageMenorQueUnoDevuelve400() throws Exception {
        mvc.perform(get(RUTA).param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("page"));

        verifyNoInteractions(alertas);
    }

    @Test
    void galponIdQueNoEsUuidEnLaConsultaDevuelve400() throws Exception {
        mvc.perform(get(RUTA).param("galponId", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PARAMETRO_INVALIDO"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("galponId"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("FORMATO_INVALIDO"));

        verifyNoInteractions(alertas);
    }

    // --------------------------------------------------------------- PATCH

    @Test
    void atenderDevuelve200ConElFixture() throws Exception {
        when(alertas.atender(ALERTA_ID)).thenReturn(
                new ResultadoAtencion(pendiente().atendida(), GALPON_ID, EstadoGalpon.MANTENIMIENTO));

        MvcResult resultado = mvc.perform(patch(RUTA + "/" + ALERTA_ID).contentType(MediaType.APPLICATION_JSON)
                        .content(FixtureJson.texto("alerta-atender-request.json")))
                .andExpect(status().isOk())
                .andReturn();

        FixtureJson.assertCoincide("alerta-atender-response.json", resultado);
    }

    @Test
    void atenderUnaAlertaInexistenteDevuelve404() throws Exception {
        when(alertas.atender(ALERTA_ID)).thenThrow(
                new RecursoNoEncontradoException("ALERTA_NO_ENCONTRADA", "La alerta no existe."));

        mvc.perform(patch(RUTA + "/" + ALERTA_ID).contentType(MediaType.APPLICATION_JSON)
                        .content(FixtureJson.texto("alerta-atender-request.json")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ALERTA_NO_ENCONTRADA"))
                .andExpect(jsonPath("$.detail").value("La alerta no existe."))
                .andExpect(jsonPath("$.instance").value(RUTA + "/" + ALERTA_ID));
    }

    @Test
    void atenderUnaAlertaYaAtendidaDevuelve409() throws Exception {
        when(alertas.atender(ALERTA_ID)).thenThrow(
                new ConflictoException("ALERTA_NO_PENDIENTE", "La alerta ya no está pendiente."));

        mvc.perform(patch(RUTA + "/" + ALERTA_ID).contentType(MediaType.APPLICATION_JSON)
                        .content(FixtureJson.texto("alerta-atender-request.json")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALERTA_NO_PENDIENTE"))
                .andExpect(jsonPath("$.detail").value("La alerta ya no está pendiente."));
    }

    @Test
    void atenderConElGalponYaNoDisponibleDevuelve409EstadoEsperadoCambiado() throws Exception {
        when(alertas.atender(ALERTA_ID)).thenThrow(new ConflictoException("ESTADO_ESPERADO_CAMBIADO",
                "El galpón ya no está disponible. Actualiza la información e inténtalo de nuevo."));

        mvc.perform(patch(RUTA + "/" + ALERTA_ID).contentType(MediaType.APPLICATION_JSON)
                        .content(FixtureJson.texto("alerta-atender-request.json")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ESTADO_ESPERADO_CAMBIADO"))
                .andExpect(jsonPath("$.detail")
                        .value("El galpón ya no está disponible. Actualiza la información e inténtalo de nuevo."));
    }

    @Test
    void atenderConUnEstadoDistintoDeAtendidaDevuelve400EstadoInvalido() throws Exception {
        mvc.perform(patch(RUTA + "/" + ALERTA_ID).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\": \"ELIMINADA\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ESTADO_INVALIDO"))
                .andExpect(jsonPath("$.detail").value("Solo se puede cambiar el estado de la alerta a ATENDIDA."))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("estado"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("FORMATO_INVALIDO"));

        verifyNoInteractions(alertas);
    }

    @Test
    void atenderSinEstadoDevuelve400EstadoInvalido() throws Exception {
        mvc.perform(patch(RUTA + "/" + ALERTA_ID).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ESTADO_INVALIDO"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("OBLIGATORIO"));

        verifyNoInteractions(alertas);
    }

    @Test
    void atenderConUnaAlertaIdQueNoEsUuidDevuelve400() throws Exception {
        mvc.perform(patch(RUTA + "/abc").contentType(MediaType.APPLICATION_JSON)
                        .content(FixtureJson.texto("alerta-atender-request.json")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PARAMETRO_INVALIDO"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("alertaId"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("FORMATO_INVALIDO"));

        verifyNoInteractions(alertas);
    }

    @Test
    void atenderConUnCuerpoQueNoEsJsonDevuelve400CuerpoInvalido() throws Exception {
        mvc.perform(patch(RUTA + "/" + ALERTA_ID).contentType(MediaType.APPLICATION_JSON).content("{\"estado\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CUERPO_INVALIDO"));

        verifyNoInteractions(alertas);
    }

    // ------------------------------------- base de datos no disponible (T084)

    private static final String CAUSA_TECNICA = "Connection to localhost:5432 refused";

    @Test
    void siLaBaseDeDatosFallaAlGenerarResponde500GenericoSinMostrarLaCausa() throws Exception {
        when(alertas.generar(new Comando(GALPON_ID, "Reparar sistema de ventilación", "Alta", "Ventilación",
                "tecnico-01"))).thenThrow(new DataAccessResourceFailureException(CAUSA_TECNICA));

        MvcResult resultado = mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON)
                        .content(FixtureJson.texto("alerta-mantenimiento-request.json")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("ERROR_INTERNO"))
                .andExpect(jsonPath("$.detail").value("No fue posible completar la operación. Intenta de nuevo."))
                .andReturn();

        assertThat(resultado.getResponse().getContentAsString()).doesNotContain("5432", "refused");
    }

    @Test
    void siLaBaseDeDatosFallaAlAtenderResponde500Generico() throws Exception {
        when(alertas.atender(ALERTA_ID)).thenThrow(new DataAccessResourceFailureException(CAUSA_TECNICA));

        mvc.perform(patch(RUTA + "/" + ALERTA_ID).contentType(MediaType.APPLICATION_JSON)
                        .content(FixtureJson.texto("alerta-atender-request.json")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("ERROR_INTERNO"));
    }

    @Test
    void siLaBaseDeDatosFallaAlListarResponde500ErrorConsulta() throws Exception {
        when(alertas.listar(EstadoAlertaMantenimiento.PENDIENTE, null, 1, 20))
                .thenThrow(new DataAccessResourceFailureException(CAUSA_TECNICA));

        MvcResult resultado = mvc.perform(get(RUTA))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("ERROR_CONSULTA"))
                .andExpect(jsonPath("$.detail").value("No fue posible obtener los datos. Puedes reintentar la consulta."))
                .andReturn();

        assertThat(resultado.getResponse().getContentAsString()).doesNotContain("5432", "refused");
    }
}
