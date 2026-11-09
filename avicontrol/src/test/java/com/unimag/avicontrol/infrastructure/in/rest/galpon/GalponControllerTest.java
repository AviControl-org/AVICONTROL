package com.unimag.avicontrol.infrastructure.in.rest.galpon;

import org.junit.jupiter.params.provider.CsvSource;
import com.unimag.avicontrol.domain.model.galpon.OrigenCambioEstado;
import com.unimag.avicontrol.domain.model.galpon.HistorialEstado;
import com.unimag.avicontrol.application.port.in.galpon.ActualizarEstadoUseCase.TransicionesDisponibles;
import com.unimag.avicontrol.application.port.in.galpon.ActualizarEstadoUseCase;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unimag.avicontrol.application.port.in.galpon.ConsultarGalponUseCase;
import com.unimag.avicontrol.application.port.in.galpon.EditarGalponUseCase;
import com.unimag.avicontrol.domain.exception.ValidacionNegocioException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.OptimisticLockingFailureException;
import com.unimag.avicontrol.application.port.in.galpon.ConsultarGalponUseCase.DetalleGalpon;
import com.unimag.avicontrol.application.port.in.galpon.ConsultarGalponUseCase.HistorialLotes;
import com.unimag.avicontrol.application.port.in.galpon.ConsultarGalponUseCase.Incidencia;
import com.unimag.avicontrol.application.port.in.galpon.ConsultarGalponUseCase.ResultadoListado;
import com.unimag.avicontrol.application.port.in.galpon.RegistrarGalponUseCase;
import com.unimag.avicontrol.application.port.in.galpon.RegistrarGalponUseCase.Comando;
import com.unimag.avicontrol.domain.exception.ConflictoException;
import com.unimag.avicontrol.domain.exception.RecursoNoEncontradoException;
import com.unimag.avicontrol.domain.model.compartido.Pagina;
import com.unimag.avicontrol.domain.model.compartido.ZonaNegocio;
import com.unimag.avicontrol.domain.model.galpon.ConsultaGalpones;
import com.unimag.avicontrol.domain.model.galpon.ConsultaGalpones.CampoOrden;
import com.unimag.avicontrol.domain.model.galpon.EstadoGalpon;
import com.unimag.avicontrol.domain.model.galpon.Galpon;
import com.unimag.avicontrol.domain.model.lote.Lote;
import com.unimag.avicontrol.infrastructure.config.CacheControlConfig;
import com.unimag.avicontrol.infrastructure.config.JacksonConfig;
import com.unimag.avicontrol.infrastructure.in.rest.controller.galpon.GalponController;
import com.unimag.avicontrol.infrastructure.in.rest.compartido.GlobalExceptionHandler;
import com.unimag.avicontrol.soporte.FixtureJson;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Contrato de POST /api/v1/galpones (US1): compara las respuestas con los fixtures compartidos. */
@WebMvcTest(GalponController.class)
@Import({GlobalExceptionHandler.class, JacksonConfig.class, CacheControlConfig.class})
class GalponControllerTest {

    private static final String RUTA = "/api/v1/galpones";
    private static final UUID ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private RegistrarGalponUseCase registrarGalpon;

    @MockitoBean
    private ConsultarGalponUseCase consultarGalpon;

    @MockitoBean
    private ActualizarEstadoUseCase actualizarEstado;

    @MockitoBean
    private EditarGalponUseCase editarGalpon;

    /** 2026-10-05 10:00 en America/Bogota: "hoy" para las consultas con edad de lote. */
    @TestConfiguration
    static class RelojFijo {

        @Bean
        Clock reloj() {
            return Clock.fixed(Instant.parse("2026-10-05T15:00:00Z"), ZonaNegocio.BOGOTA);
        }
    }

    private MvcResult enviar(String json) throws Exception {
        return mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(json)).andReturn();
    }

    @Test
    void registroExitosoDevuelve201ConLocationYElGalponDisponible() throws Exception {
        when(registrarGalpon.registrar(any())).thenReturn(
                new Galpon(ID, "Galpón Norte", 1500, EstadoGalpon.DISPONIBLE, 0L));

        MvcResult resultado = mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON)
                        .content(FixtureJson.texto("galpon-registrar-request.json")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", RUTA + "/" + ID))
                .andReturn();

        FixtureJson.assertCoincide("galpon-registrar-response.json", resultado);
        verify(registrarGalpon).registrar(new Comando("Galpón Norte", 1500));
    }

    @Test
    void siLaBaseDeDatosFallaAlRegistrarDevuelve500ErrorInternoSinMostrarLaCausa() throws Exception {
        when(registrarGalpon.registrar(any()))
                .thenThrow(new DataAccessResourceFailureException("Connection to localhost:5432 refused"));

        MvcResult resultado = enviar(FixtureJson.texto("galpon-registrar-request.json"));

        org.assertj.core.api.Assertions.assertThat(resultado.getResponse().getStatus()).isEqualTo(500);
        org.assertj.core.api.Assertions.assertThat(resultado.getResponse().getContentAsString())
                .contains("\"code\":\"ERROR_INTERNO\"", "No fue posible completar la operación. Intenta de nuevo.")
                .doesNotContain("5432", "refused");
    }

    @Test
    void nombreDuplicadoDevuelve409ConElFixtureDeError() throws Exception {
        when(registrarGalpon.registrar(any()))
                .thenThrow(ConflictoException.nombreDuplicado("Ya existe un galpón con ese nombre."));

        MvcResult resultado = enviar("{\"nombre\": \"galpón norte\", \"capacidad\": 800}");

        org.assertj.core.api.Assertions.assertThat(resultado.getResponse().getStatus()).isEqualTo(409);
        FixtureJson.assertCoincide("error-nombre-duplicado.json", resultado);
    }

    @ParameterizedTest(name = "capacidad {0} devuelve 400 CAPACIDAD_INVALIDA")
    @ValueSource(strings = {"0", "-5", "10.5", "\"abc\""})
    void capacidadQueNoEsEnteroPositivoDevuelve400ConElFixtureDeError(String capacidad) throws Exception {
        MvcResult resultado = enviar("{\"nombre\": \"Galpón Sur\", \"capacidad\": " + capacidad + "}");

        org.assertj.core.api.Assertions.assertThat(resultado.getResponse().getStatus()).isEqualTo(400);
        FixtureJson.assertCoincide("error-capacidad-invalida.json", resultado);
        verifyNoInteractions(registrarGalpon);
    }

    @Test
    void capacidadAusenteDevuelve400ConCampoObligatorio() throws Exception {
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content("{\"nombre\": \"Galpón Sur\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CAPACIDAD_INVALIDA"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("capacidad"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("OBLIGATORIO"));

        verifyNoInteractions(registrarGalpon);
    }

    @ParameterizedTest(name = "body {0} devuelve 400 NOMBRE_INVALIDO")
    @ValueSource(strings = {
            "{\"nombre\": \"\", \"capacidad\": 1500}",
            "{\"nombre\": \"   \", \"capacidad\": 1500}",
            "{\"capacidad\": 1500}"})
    void nombreVacioEnBlancoOAusenteDevuelve400ConCampoObligatorio(String body) throws Exception {
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOMBRE_INVALIDO"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("nombre"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("OBLIGATORIO"));

        verifyNoInteractions(registrarGalpon);
    }

    @Test
    void nombreDeMasDeCienCaracteresDevuelve400() throws Exception {
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"" + "A".repeat(101) + "\", \"capacidad\": 1500}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOMBRE_INVALIDO"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("LONGITUD_INVALIDA"));
    }

    @Test
    void nombreConCaracteresNoPermitidosDevuelve400() throws Exception {
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"Galpón #1\", \"capacidad\": 1500}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOMBRE_INVALIDO"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("FORMATO_INVALIDO"));
    }

    @Test
    void variosCamposInvalidosDevuelven400ValidacionFallida() throws Exception {
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"\", \"capacidad\": -5}"))
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

    // =================================================================== GET (US3)

    private static final UUID NORTE = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID SUR = UUID.fromString("6ba7b810-9dad-41d1-80b4-00c04fd430c8");
    private static final UUID INEXISTENTE = UUID.fromString("d9b2d63d-a233-4123-847a-3f2c0c1e6b55");

    private static List<Galpon> cuatroGalpones() {
        return List.of(
                new Galpon(UUID.fromString("f47ac10b-58cc-4372-a567-0e02b2c3d479"), "Galpón Este", 800,
                        EstadoGalpon.EN_COSECHA, 0L),
                new Galpon(NORTE, "Galpón Norte", 1500, EstadoGalpon.PRODUCTIVO, 0L),
                new Galpon(SUR, "Galpón Sur", 2000, EstadoGalpon.DISPONIBLE, 0L),
                new Galpon(UUID.fromString("c5e8f3a2-6b17-4d09-9f2e-8a1b3c4d5e6f"), "Galpón Oeste", 1800,
                        EstadoGalpon.PRODUCTIVO, 0L));
    }

    private static Lote lote(String id, String nombre, LocalDate ingreso, long inicial, long actual, long costo,
                             Instant desvinculadoEn) {
        return new Lote(UUID.fromString(id), NORTE, nombre, ingreso, inicial, actual, costo, desvinculadoEn, 0L);
    }

    private static Galpon galponNorte() {
        return new Galpon(NORTE, "Galpón Norte", 1500, EstadoGalpon.PRODUCTIVO, 0L);
    }

    // ------------------------------------------------------- GET /galpones

    @Test
    void listadoSinParametrosUsaOrdenPorNombreAscendentePaginaUnoYDevuelveElFixture() throws Exception {
        when(consultarGalpon.listar(any())).thenReturn(
                new ResultadoListado(new Pagina<>(cuatroGalpones(), 1, 10, 4), false));

        MvcResult resultado = mvc.perform(get(RUTA + "?page=1"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();

        FixtureJson.assertCoincide("galpon-lista-response.json", resultado);
        verify(consultarGalpon).listar(new ConsultaGalpones(null, null, CampoOrden.NOMBRE, true, 1, 10));
    }

    @Test
    void listadoTraduceNombreEstadoOrdenYPaginaALaConsulta() throws Exception {
        when(consultarGalpon.listar(any())).thenReturn(
                new ResultadoListado(new Pagina<>(List.of(), 2, 10, 0), false));

        mvc.perform(get(RUTA).param("nombre", "  norte  ").param("estado", "PRODUCTIVO")
                        .param("orden", "capacidad,desc").param("page", "2"))
                .andExpect(status().isOk());

        verify(consultarGalpon).listar(
                new ConsultaGalpones("norte", EstadoGalpon.PRODUCTIVO, CampoOrden.CAPACIDAD, false, 2, 10));
    }

    @Test
    void nombreEnBlancoYEstadoVacioSonSinFiltro() throws Exception {
        when(consultarGalpon.listar(any())).thenReturn(
                new ResultadoListado(new Pagina<>(List.of(), 1, 10, 0), false));

        mvc.perform(get(RUTA).param("nombre", "   ").param("estado", "")).andExpect(status().isOk());

        verify(consultarGalpon).listar(new ConsultaGalpones(null, null, CampoOrden.NOMBRE, true, 1, 10));
    }

    @Test
    void listadoSinCoincidenciasDevuelveTodosConMensajeSegunElFixture() throws Exception {
        when(consultarGalpon.listar(any())).thenReturn(
                new ResultadoListado(new Pagina<>(cuatroGalpones(), 1, 10, 4), true));

        MvcResult resultado = mvc.perform(get(RUTA + "?nombre=xyz&page=1"))
                .andExpect(status().isOk())
                .andReturn();

        FixtureJson.assertCoincide("galpon-lista-sin-coincidencias-response.json", resultado);
    }

    @Test
    void listadoSinGalponesDevuelvePaginaVaciaSinMensaje() throws Exception {
        when(consultarGalpon.listar(any())).thenReturn(
                new ResultadoListado(new Pagina<>(List.of(), 1, 10, 0), false));

        mvc.perform(get(RUTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido").isEmpty())
                .andExpect(jsonPath("$.totalElementos").value(0))
                .andExpect(jsonPath("$.totalPaginas").value(0))
                .andExpect(jsonPath("$.sinCoincidencias").value(false))
                .andExpect(jsonPath("$.mensaje").doesNotExist());
    }

    @ParameterizedTest(name = "orden={0} devuelve 400 FORMATO_INVALIDO")
    @ValueSource(strings = {"precio,asc", "nombre,arriba", "nombre,asc,extra", "capacidad;desc"})
    void ordenConCampoODireccionNoPermitidosDevuelve400(String orden) throws Exception {
        mvc.perform(get(RUTA).param("orden", orden))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PARAMETRO_INVALIDO"))
                .andExpect(jsonPath("$.instance").value(RUTA))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("orden"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("FORMATO_INVALIDO"))
                .andExpect(jsonPath("$.fieldErrors[0].message")
                        .value("Usa campo,direccion: nombre, capacidad o estado; asc o desc"));

        verifyNoInteractions(consultarGalpon);
    }

    @Test
    void estadoFueraDeLosSeisValoresDevuelve400() throws Exception {
        mvc.perform(get(RUTA + "?estado=ACTIVO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PARAMETRO_INVALIDO"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("estado"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("VALOR_NO_PERMITIDO"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("Debe ser uno de: DISPONIBLE, VACIADO_SANITARIO, "
                        + "PRODUCTIVO, EN_COSECHA, MANTENIMIENTO, AISLAMIENTO"));

        verifyNoInteractions(consultarGalpon);
    }

    @Test
    void paginaMenorQueUnoDevuelve400() throws Exception {
        mvc.perform(get(RUTA + "?page=0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PARAMETRO_INVALIDO"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("page"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("MINIMO_1"));

        verifyNoInteractions(consultarGalpon);
    }

    @Test
    void errorTecnicoAlListarDevuelve500ErrorConsulta() throws Exception {
        when(consultarGalpon.listar(any())).thenThrow(new IllegalStateException("fallo simulado"));

        mvc.perform(get(RUTA))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("ERROR_CONSULTA"))
                .andExpect(jsonPath("$.detail").value("No fue posible obtener los datos. Puedes reintentar la consulta."));
    }

    // ------------------------------------------------ GET /galpones/{galponId}

    @Test
    void detalleConLoteActivoCalculaLaEdadYCoincideConElFixture() throws Exception {
        Lote activo = lote("2fc03a21-84c4-4c83-bb15-6607bb75cb90", "Lote A1", LocalDate.of(2026, 9, 15),
                1000, 975, 5_000_000, null);
        when(consultarGalpon.detalle(NORTE)).thenReturn(new DetalleGalpon(galponNorte(), activo, false, List.of()));

        MvcResult resultado = mvc.perform(get(RUTA + "/" + NORTE))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();

        FixtureJson.assertCoincide("galpon-detalle-response.json", resultado);
    }

    @Test
    void detalleDeGalponSinLotesOmiteLoteActivoYDaElMensaje() throws Exception {
        Galpon sur = new Galpon(SUR, "Galpón Sur", 2000, EstadoGalpon.DISPONIBLE, 0L);
        when(consultarGalpon.detalle(SUR)).thenReturn(new DetalleGalpon(sur, null, true, List.of()));

        MvcResult resultado = mvc.perform(get(RUTA + "/" + SUR)).andExpect(status().isOk()).andReturn();

        FixtureJson.assertCoincide("galpon-detalle-sin-lote-response.json", resultado);
    }

    @Test
    void loteConNombreVacioSeOmiteYSeInformaEnIncidencias() throws Exception {
        Lote activo = lote("2fc03a21-84c4-4c83-bb15-6607bb75cb90", null, LocalDate.of(2026, 9, 15),
                1000, 975, 5_000_000, null);
        when(consultarGalpon.detalle(NORTE)).thenReturn(new DetalleGalpon(galponNorte(), activo, false,
                List.of(new Incidencia("CAMPO_NO_DISPONIBLE", "El nombre del lote no está disponible."))));

        mvc.perform(get(RUTA + "/" + NORTE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loteActivo.nombre").doesNotExist())
                .andExpect(jsonPath("$.loteActivo.edadDias").value(21))
                .andExpect(jsonPath("$.incidencias[0].codigo").value("CAMPO_NO_DISPONIBLE"))
                .andExpect(jsonPath("$.incidencias[0].mensaje").value("El nombre del lote no está disponible."));
    }

    @Test
    void loteSinFechaDeIngresoOmiteTambienLaEdad() throws Exception {
        Lote activo = lote("2fc03a21-84c4-4c83-bb15-6607bb75cb90", "Lote A1", null, 1000, 975, 5_000_000, null);
        when(consultarGalpon.detalle(NORTE)).thenReturn(new DetalleGalpon(galponNorte(), activo, false,
                List.of(new Incidencia("CAMPO_NO_DISPONIBLE", "La fecha de ingreso del lote no está disponible."))));

        mvc.perform(get(RUTA + "/" + NORTE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loteActivo.fechaIngreso").doesNotExist())
                .andExpect(jsonPath("$.loteActivo.edadDias").doesNotExist());
    }

    @Test
    void detalleDeGalponInexistenteDevuelve404ConElFixture() throws Exception {
        when(consultarGalpon.detalle(INEXISTENTE)).thenThrow(
                new RecursoNoEncontradoException("GALPON_NO_ENCONTRADO", "El galpón consultado no existe."));

        MvcResult resultado = mvc.perform(get(RUTA + "/" + INEXISTENTE)).andReturn();

        org.assertj.core.api.Assertions.assertThat(resultado.getResponse().getStatus()).isEqualTo(404);
        FixtureJson.assertCoincide("error-galpon-no-encontrado.json", resultado);
    }

    @Test
    void detalleConGalponIdQueNoEsUuidDevuelve400() throws Exception {
        mvc.perform(get(RUTA + "/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PARAMETRO_INVALIDO"))
                .andExpect(jsonPath("$.instance").value(RUTA + "/abc"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("galponId"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("FORMATO_INVALIDO"));

        verifyNoInteractions(consultarGalpon);
    }

    @Test
    void errorTecnicoAlConsultarElDetalleDevuelve500ErrorConsulta() throws Exception {
        when(consultarGalpon.detalle(NORTE)).thenThrow(new IllegalStateException("fallo simulado"));

        mvc.perform(get(RUTA + "/" + NORTE))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("ERROR_CONSULTA"));
    }

    // ------------------------------------------- GET /galpones/{galponId}/lotes

    @Test
    void historialConLoteActivoYAnterioresMideLaEdadAlDiaDeSalidaYCoincideConElFixture() throws Exception {
        Lote activo = lote("2fc03a21-84c4-4c83-bb15-6607bb75cb90", "Lote B2", LocalDate.of(2026, 9, 25),
                1200, 1180, 6_000_000, null);
        // Salió el 2026-08-21 (hora de Bogotá): ingreso 2026-07-01 + 51 días = edad 52.
        Lote anterior = lote("9a1f4c70-2d3b-4a55-8e61-0b7f3c9d2e18", "Lote A1", LocalDate.of(2026, 7, 1),
                1000, 940, 5_000_000, Instant.parse("2026-08-21T15:00:00Z"));
        when(consultarGalpon.historialLotes(NORTE))
                .thenReturn(new HistorialLotes(NORTE, activo, List.of(anterior), List.of()));

        MvcResult resultado = mvc.perform(get(RUTA + "/" + NORTE + "/lotes"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();

        FixtureJson.assertCoincide("galpon-historial-lotes-response.json", resultado);
    }

    @Test
    void historialDeGalponQueNuncaTuvoLotesTraeListaVaciaYSinLoteActivo() throws Exception {
        when(consultarGalpon.historialLotes(SUR))
                .thenReturn(new HistorialLotes(SUR, null, List.of(), List.of()));

        mvc.perform(get(RUTA + "/" + SUR + "/lotes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.galponId").value(SUR.toString()))
                .andExpect(jsonPath("$.loteActivo").doesNotExist())
                .andExpect(jsonPath("$.lotesAnteriores").isEmpty())
                .andExpect(jsonPath("$.incidencias").doesNotExist());
    }

    @Test
    void historialDeGalponInexistenteDevuelve404() throws Exception {
        when(consultarGalpon.historialLotes(INEXISTENTE)).thenThrow(
                new RecursoNoEncontradoException("GALPON_NO_ENCONTRADO", "El galpón consultado no existe."));

        mvc.perform(get(RUTA + "/" + INEXISTENTE + "/lotes"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GALPON_NO_ENCONTRADO"))
                .andExpect(jsonPath("$.instance").value(RUTA + "/" + INEXISTENTE + "/lotes"));
    }

    @Test
    void historialConGalponIdQueNoEsUuidDevuelve400() throws Exception {
        mvc.perform(get(RUTA + "/abc/lotes"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PARAMETRO_INVALIDO"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("galponId"));

        verifyNoInteractions(consultarGalpon);
    }

    // ----------------------------------------------------------------- US4: actualizar estado

    private static final String RUTA_ESTADO = RUTA + "/" + ID + "/estado";
    private static final UUID OTRO = UUID.fromString("d9b2d63d-a233-4123-847a-3f2c0c1e6b55");
    private static final String MENSAJE_ESTADO =
            "El estado indicado no es válido. Usa uno de: DISPONIBLE, VACIADO_SANITARIO, PRODUCTIVO, EN_COSECHA, MANTENIMIENTO, AISLAMIENTO.";

    private MvcResult patchEstado(String json) throws Exception {
        return mvc.perform(patch(RUTA_ESTADO).contentType(MediaType.APPLICATION_JSON).content(json)).andReturn();
    }

    private static HistorialEstado cambio(EstadoGalpon anterior, EstadoGalpon nuevo, String instante) {
        return new HistorialEstado(UUID.randomUUID(), ID, anterior, nuevo, Instant.parse(instante),
                OrigenCambioEstado.ADMINISTRADOR);
    }

    @Test
    void transicionesDelAdministradorDevuelve200ConElFixtureYSinCache() throws Exception {
        when(actualizarEstado.transiciones(ID)).thenReturn(
                new TransicionesDisponibles(ID, EstadoGalpon.PRODUCTIVO, List.of(EstadoGalpon.EN_COSECHA)));

        MvcResult resultado = mvc.perform(get(RUTA + "/" + ID + "/transiciones"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();

        FixtureJson.assertCoincide("galpon-transiciones-response.json", resultado);
    }

    @Test
    void transicionesDeUnGalponDisponibleDevuelveListaVacia() throws Exception {
        when(actualizarEstado.transiciones(ID)).thenReturn(
                new TransicionesDisponibles(ID, EstadoGalpon.DISPONIBLE, List.of()));

        mvc.perform(get(RUTA + "/" + ID + "/transiciones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoActual").value("DISPONIBLE"))
                .andExpect(jsonPath("$.destinosPermitidos").isEmpty());
    }

    @Test
    void transicionesDeUnGalponInexistenteDevuelve404() throws Exception {
        when(actualizarEstado.transiciones(OTRO)).thenThrow(
                new RecursoNoEncontradoException("GALPON_NO_ENCONTRADO", "El galpón no existe."));

        mvc.perform(get(RUTA + "/" + OTRO + "/transiciones"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GALPON_NO_ENCONTRADO"))
                .andExpect(jsonPath("$.instance").value(RUTA + "/" + OTRO + "/transiciones"));
    }

    @Test
    void transicionesConGalponIdQueNoEsUuidDevuelve400() throws Exception {
        mvc.perform(get(RUTA + "/abc/transiciones"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PARAMETRO_INVALIDO"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("galponId"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("FORMATO_INVALIDO"));
    }

    @Test
    void cambioDeEstadoExitosoDevuelve200ConElFixtureYLlamaAlCasoDeUso() throws Exception {
        when(actualizarEstado.actualizarEstado(ID, EstadoGalpon.PRODUCTIVO, EstadoGalpon.EN_COSECHA)).thenReturn(
                cambio(EstadoGalpon.PRODUCTIVO, EstadoGalpon.EN_COSECHA, "2026-10-05T14:15:00Z"));

        MvcResult resultado = patchEstado(FixtureJson.texto("galpon-estado-request.json"));

        org.assertj.core.api.Assertions.assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
        FixtureJson.assertCoincide("galpon-estado-response.json", resultado);
        verify(actualizarEstado).actualizarEstado(ID, EstadoGalpon.PRODUCTIVO, EstadoGalpon.EN_COSECHA);
    }

    @Test
    void estadoEsperadoQueYaCambioDevuelve409ConElFixtureDeError() throws Exception {
        when(actualizarEstado.actualizarEstado(any(), any(), any())).thenThrow(new ConflictoException(
                "ESTADO_ESPERADO_CAMBIADO", "El estado del galpón cambió. Actualiza la información e inténtalo de nuevo."));

        MvcResult resultado = patchEstado(FixtureJson.texto("galpon-estado-request.json"));

        org.assertj.core.api.Assertions.assertThat(resultado.getResponse().getStatus()).isEqualTo(409);
        FixtureJson.assertCoincide("error-estado-esperado-cambiado.json", resultado);
    }

    @Test
    void transicionFueraDeLaMatrizDevuelve409TransicionNoPermitida() throws Exception {
        when(actualizarEstado.actualizarEstado(any(), any(), any())).thenThrow(new ConflictoException(
                "TRANSICION_NO_PERMITIDA", "No se puede pasar de ese estado al estado solicitado."));

        MvcResult resultado = patchEstado("{\"estadoEsperado\": \"AISLAMIENTO\", \"estadoDestino\": \"VACIADO_SANITARIO\"}");

        org.assertj.core.api.Assertions.assertThat(resultado.getResponse().getStatus()).isEqualTo(409);
        org.assertj.core.api.Assertions.assertThat(resultado.getResponse().getContentAsString())
                .contains("\"code\":\"TRANSICION_NO_PERMITIDA\"", "\"title\":\"Transición no permitida\"");
    }

    @Test
    void transicionQueElAdministradorNoPuedePedirDevuelve409OrigenNoAutorizado() throws Exception {
        when(actualizarEstado.actualizarEstado(any(), any(), any())).thenThrow(new ConflictoException(
                "ORIGEN_NO_AUTORIZADO", "El administrador no puede solicitar esa transición por este servicio."));

        MvcResult resultado = patchEstado("{\"estadoEsperado\": \"DISPONIBLE\", \"estadoDestino\": \"PRODUCTIVO\"}");

        org.assertj.core.api.Assertions.assertThat(resultado.getResponse().getStatus()).isEqualTo(409);
        org.assertj.core.api.Assertions.assertThat(resultado.getResponse().getContentAsString())
                .contains("\"code\":\"ORIGEN_NO_AUTORIZADO\"");
    }

    @Test
    void cambioDeUnGalponInexistenteDevuelve404() throws Exception {
        when(actualizarEstado.actualizarEstado(any(), any(), any())).thenThrow(
                new RecursoNoEncontradoException("GALPON_NO_ENCONTRADO", "El galpón no existe."));

        mvc.perform(patch(RUTA + "/" + OTRO + "/estado").contentType(MediaType.APPLICATION_JSON)
                        .content(FixtureJson.texto("galpon-estado-request.json")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GALPON_NO_ENCONTRADO"))
                .andExpect(jsonPath("$.instance").value(RUTA + "/" + OTRO + "/estado"));
    }

    @Test
    void estadoFueraDeLosSeisValoresDevuelve400EstadoInvalido() throws Exception {
        MvcResult resultado = patchEstado("{\"estadoEsperado\": \"PRODUCTIVO\", \"estadoDestino\": \"ACTIVO\"}");

        org.assertj.core.api.Assertions.assertThat(resultado.getResponse().getStatus()).isEqualTo(400);
        String cuerpo = resultado.getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(cuerpo)
                .contains("\"code\":\"ESTADO_INVALIDO\"", MENSAJE_ESTADO, "\"field\":\"estadoDestino\"",
                        "\"code\":\"VALOR_NO_PERMITIDO\"");
        verifyNoInteractions(actualizarEstado);
    }

    @Test
    void estadoAusenteDevuelve400EstadoInvalidoConCampoObligatorio() throws Exception {
        MvcResult resultado = patchEstado("{\"estadoEsperado\": \"PRODUCTIVO\"}");

        org.assertj.core.api.Assertions.assertThat(resultado.getResponse().getStatus()).isEqualTo(400);
        org.assertj.core.api.Assertions.assertThat(resultado.getResponse().getContentAsString())
                .contains("\"code\":\"ESTADO_INVALIDO\"", "\"field\":\"estadoDestino\"", "\"code\":\"OBLIGATORIO\"");
        verifyNoInteractions(actualizarEstado);
    }

    /** Diferencia conocida con el escenario 15 del plan: Jackson corta en el primer enum inválido. */
    @Test
    void losDosEstadosInvalidosADaLaVezSeAtribuyenAlPrimerCampoInvalido() throws Exception {
        mvc.perform(patch(RUTA_ESTADO).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estadoEsperado\": \"ACTIVO\", \"estadoDestino\": \"INACTIVO\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ESTADO_INVALIDO"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(1))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("estadoEsperado"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("VALOR_NO_PERMITIDO"));

        verifyNoInteractions(actualizarEstado);
    }

    @Test
    void bodyQueNoEsJsonValidoDevuelve400CuerpoInvalido() throws Exception {
        mvc.perform(patch(RUTA_ESTADO).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estadoEsperado\": \"PRODUCTIVO\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CUERPO_INVALIDO"));

        verifyNoInteractions(actualizarEstado);
    }

    @Test
    void cambioConGalponIdQueNoEsUuidDevuelve400() throws Exception {
        mvc.perform(patch(RUTA + "/abc/estado").contentType(MediaType.APPLICATION_JSON)
                        .content(FixtureJson.texto("galpon-estado-request.json")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PARAMETRO_INVALIDO"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("galponId"));

        verifyNoInteractions(actualizarEstado);
    }

    @Test
    void historialDeEstadosDevuelve200ConElFixtureYLosValoresPorDefecto() throws Exception {
        HistorialEstado reciente = new HistorialEstado(UUID.fromString("7c3e9a14-5d2b-4e8f-b6a1-3f0d8c2e9b57"), ID,
                EstadoGalpon.PRODUCTIVO, EstadoGalpon.EN_COSECHA, Instant.parse("2026-10-05T14:15:00Z"),
                OrigenCambioEstado.ADMINISTRADOR);
        HistorialEstado antiguo = new HistorialEstado(UUID.fromString("b84f1d60-92a7-4c35-8e0b-6a5d3c7f1e29"), ID,
                EstadoGalpon.DISPONIBLE, EstadoGalpon.PRODUCTIVO, Instant.parse("2026-09-15T13:00:00Z"),
                OrigenCambioEstado.REGISTRO_LOTE);
        when(actualizarEstado.historial(ID, 1, 20)).thenReturn(new Pagina<>(List.of(reciente, antiguo), 1, 20, 2));

        MvcResult resultado = mvc.perform(get(RUTA + "/" + ID + "/historial-estados"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();

        FixtureJson.assertCoincide("galpon-historial-estados-response.json", resultado);
    }

    @Test
    void historialDeEstadosPasaPageYSizeAlCasoDeUso() throws Exception {
        when(actualizarEstado.historial(ID, 2, 5)).thenReturn(new Pagina<>(List.of(), 2, 5, 6));

        mvc.perform(get(RUTA + "/" + ID + "/historial-estados").param("page", "2").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagina").value(2))
                .andExpect(jsonPath("$.tamanio").value(5))
                .andExpect(jsonPath("$.totalPaginas").value(2));

        verify(actualizarEstado).historial(ID, 2, 5);
    }

    @ParameterizedTest(name = "{0}={1} devuelve 400 PARAMETRO_INVALIDO")
    @CsvSource({"page,0", "page,-1", "size,0", "size,101", "size,-5"})
    void historialDeEstadosConPageOSizeFueraDeRangoDevuelve400(String parametro, String valor) throws Exception {
        mvc.perform(get(RUTA + "/" + ID + "/historial-estados").param(parametro, valor))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PARAMETRO_INVALIDO"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value(parametro));

        verifyNoInteractions(actualizarEstado);
    }

    @Test
    void historialDeEstadosDeUnGalponInexistenteDevuelve404() throws Exception {
        when(actualizarEstado.historial(OTRO, 1, 20)).thenThrow(
                new RecursoNoEncontradoException("GALPON_NO_ENCONTRADO", "El galpón no existe."));

        mvc.perform(get(RUTA + "/" + OTRO + "/historial-estados"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GALPON_NO_ENCONTRADO"));
    }

    @Test
    void historialDeEstadosConGalponIdQueNoEsUuidDevuelve400() throws Exception {
        mvc.perform(get(RUTA + "/abc/historial-estados"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("galponId"));
    }

    // ------------------------------------------------- PATCH /galpones/{id} (US9)

    private static final String RUTA_EDITAR = RUTA + "/" + ID;

    private MvcResult editar(String json) throws Exception {
        return mvc.perform(patch(RUTA_EDITAR).contentType(MediaType.APPLICATION_JSON).content(json)).andReturn();
    }

    @Test
    void editarElNombreDevuelve200ConElGalponEditado() throws Exception {
        when(editarGalpon.editar(ID, new EditarGalponUseCase.Comando("Galpón Norte", null)))
                .thenReturn(new Galpon(ID, "Galpón Norte", 1500, EstadoGalpon.DISPONIBLE, 1L));

        mvc.perform(patch(RUTA_EDITAR).contentType(MediaType.APPLICATION_JSON)
                        .content(FixtureJson.texto("galpon-editar-nombre-request.json")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.nombre").value("Galpón Norte"))
                .andExpect(jsonPath("$.capacidad").value(1500))
                .andExpect(jsonPath("$.estado").value("DISPONIBLE"));
    }

    @Test
    void editarLaCapacidadDevuelve200ConElFixture() throws Exception {
        when(editarGalpon.editar(ID, new EditarGalponUseCase.Comando(null, 1500L)))
                .thenReturn(new Galpon(ID, "Galpón Norte", 1500, EstadoGalpon.MANTENIMIENTO, 1L));

        MvcResult resultado = mvc.perform(patch(RUTA_EDITAR).contentType(MediaType.APPLICATION_JSON)
                        .content(FixtureJson.texto("galpon-editar-capacidad-request.json")))
                .andExpect(status().isOk())
                .andReturn();

        FixtureJson.assertCoincide("galpon-editar-response.json", resultado);
    }

    @Test
    void editarNombreYCapacidadJuntosPasaAmbosCamposAlPuerto() throws Exception {
        when(editarGalpon.editar(ID, new EditarGalponUseCase.Comando("Galpón Norte B", 1800L)))
                .thenReturn(new Galpon(ID, "Galpón Norte B", 1800, EstadoGalpon.MANTENIMIENTO, 1L));

        mvc.perform(patch(RUTA_EDITAR).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"Galpón Norte B\", \"capacidad\": 1800}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Galpón Norte B"))
                .andExpect(jsonPath("$.capacidad").value(1800));
    }

    @Test
    void editarRecortaLosEspaciosDelNombre() throws Exception {
        when(editarGalpon.editar(ID, new EditarGalponUseCase.Comando("Galpón Norte", null)))
                .thenReturn(new Galpon(ID, "Galpón Norte", 1500, EstadoGalpon.DISPONIBLE, 1L));

        mvc.perform(patch(RUTA_EDITAR).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"  Galpón Norte  \"}"))
                .andExpect(status().isOk());

        verify(editarGalpon).editar(ID, new EditarGalponUseCase.Comando("Galpón Norte", null));
    }

    @Test
    void editarCapacidadFueraDeMantenimientoDevuelve409ConElFixture() throws Exception {
        when(editarGalpon.editar(ID, new EditarGalponUseCase.Comando(null, 1500L))).thenThrow(
                new ConflictoException("CAPACIDAD_NO_EDITABLE",
                        "La capacidad solo se puede modificar cuando el galpón está en mantenimiento."));

        MvcResult resultado = editar(FixtureJson.texto("galpon-editar-capacidad-request.json"));

        org.assertj.core.api.Assertions.assertThat(resultado.getResponse().getStatus()).isEqualTo(409);
        FixtureJson.assertCoincide("error-capacidad-no-editable.json", resultado);
    }

    @Test
    void editarConElNombreDeOtroGalponDevuelve409NombreDuplicado() throws Exception {
        when(editarGalpon.editar(ID, new EditarGalponUseCase.Comando("Galpón B", null)))
                .thenThrow(ConflictoException.nombreDuplicado("Ya existe un galpón con ese nombre."));

        mvc.perform(patch(RUTA_EDITAR).contentType(MediaType.APPLICATION_JSON).content("{\"nombre\": \"Galpón B\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOMBRE_DUPLICADO"))
                .andExpect(jsonPath("$.instance").value(RUTA_EDITAR))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("nombre"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("DUPLICADO"));
    }

    @ParameterizedTest(name = "nombre \"{0}\" devuelve 400 NOMBRE_INVALIDO")
    @CsvSource(delimiter = '|', value = {"''|LONGITUD_INVALIDA", "'   '|LONGITUD_INVALIDA",
            "Galpón #1|FORMATO_INVALIDO"})
    void editarConUnNombreInvalidoDevuelve400(String nombre, String codigoCampo) throws Exception {
        mvc.perform(patch(RUTA_EDITAR).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"" + nombre.replace("'", "") + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOMBRE_INVALIDO"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("nombre"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value(codigoCampo));

        verifyNoInteractions(editarGalpon);
    }

    @Test
    void editarConUnNombreDeMasDe100CaracteresDevuelve400() throws Exception {
        mvc.perform(patch(RUTA_EDITAR).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"" + "A".repeat(101) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOMBRE_INVALIDO"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("LONGITUD_INVALIDA"));

        verifyNoInteractions(editarGalpon);
    }

    @ParameterizedTest(name = "capacidad {0} devuelve 400 CAPACIDAD_INVALIDA")
    @ValueSource(strings = {"0", "-5", "10.5", "\"abc\""})
    void editarConUnaCapacidadQueNoEsEnteroPositivoDevuelve400(String capacidad) throws Exception {
        mvc.perform(patch(RUTA_EDITAR).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"capacidad\": " + capacidad + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CAPACIDAD_INVALIDA"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("capacidad"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("ENTERO_POSITIVO"));

        verifyNoInteractions(editarGalpon);
    }

    @ParameterizedTest(name = "body {0} devuelve 400 SIN_CAMBIOS")
    @ValueSource(strings = {"{}", "{\"nombre\": null, \"capacidad\": null}"})
    void editarSinNingunCampoConValorDevuelve400SinCambios(String body) throws Exception {
        when(editarGalpon.editar(ID, new EditarGalponUseCase.Comando(null, null))).thenThrow(
                new ValidacionNegocioException("SIN_CAMBIOS",
                        "Indica al menos un campo para modificar: nombre o capacidad."));

        mvc.perform(patch(RUTA_EDITAR).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SIN_CAMBIOS"))
                .andExpect(jsonPath("$.detail").value("Indica al menos un campo para modificar: nombre o capacidad."));
    }

    @Test
    void editarUnGalponInexistenteDevuelve404() throws Exception {
        UUID otro = UUID.fromString("d9b2d63d-a233-4123-847a-3f2c0c1e6b55");
        when(editarGalpon.editar(otro, new EditarGalponUseCase.Comando("Galpón Norte", null))).thenThrow(
                new RecursoNoEncontradoException("GALPON_NO_ENCONTRADO", "El galpón no existe."));

        mvc.perform(patch(RUTA + "/" + otro).contentType(MediaType.APPLICATION_JSON)
                        .content(FixtureJson.texto("galpon-editar-nombre-request.json")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GALPON_NO_ENCONTRADO"))
                .andExpect(jsonPath("$.instance").value(RUTA + "/" + otro));
    }

    @Test
    void editarConGalponIdQueNoEsUuidDevuelve400() throws Exception {
        mvc.perform(patch(RUTA + "/abc").contentType(MediaType.APPLICATION_JSON)
                        .content(FixtureJson.texto("galpon-editar-nombre-request.json")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PARAMETRO_INVALIDO"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("galponId"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("FORMATO_INVALIDO"));

        verifyNoInteractions(editarGalpon);
    }

    @Test
    void editarConDosCamposInvalidosADaLaVezDevuelve400ValidacionFallida() throws Exception {
        mvc.perform(patch(RUTA_EDITAR).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"Galpón #1\", \"capacidad\": 0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDACION_FALLIDA"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(2));

        verifyNoInteractions(editarGalpon);
    }

    @Test
    void editarConUnCuerpoQueNoEsJsonDevuelve400CuerpoInvalido() throws Exception {
        mvc.perform(patch(RUTA_EDITAR).contentType(MediaType.APPLICATION_JSON).content("{\"nombre\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CUERPO_INVALIDO"));

        verifyNoInteractions(editarGalpon);
    }

    @Test
    void editarConUnaModificacionSimultaneaDevuelve409ConflictoDeConcurrencia() throws Exception {
        when(editarGalpon.editar(ID, new EditarGalponUseCase.Comando("Galpón Norte", null)))
                .thenThrow(new OptimisticLockingFailureException("version"));

        mvc.perform(patch(RUTA_EDITAR).contentType(MediaType.APPLICATION_JSON)
                        .content(FixtureJson.texto("galpon-editar-nombre-request.json")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICTO_CONCURRENCIA"))
                .andExpect(jsonPath("$.detail").value(
                        "Otro usuario modificó el registro al mismo tiempo. Actualiza la información e inténtalo de nuevo."));
    }
}
