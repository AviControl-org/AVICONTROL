package com.unimag.avicontrol.infrastructure.in.rest.galpon;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unimag.avicontrol.application.port.in.galpon.RegistrarGalponUseCase;
import com.unimag.avicontrol.application.port.in.galpon.RegistrarGalponUseCase.Comando;
import com.unimag.avicontrol.domain.exception.ConflictoException;
import com.unimag.avicontrol.domain.model.galpon.EstadoGalpon;
import com.unimag.avicontrol.domain.model.galpon.Galpon;
import com.unimag.avicontrol.infrastructure.config.JacksonConfig;
import com.unimag.avicontrol.infrastructure.in.rest.controller.galpon.GalponController;
import com.unimag.avicontrol.infrastructure.in.rest.compartido.GlobalExceptionHandler;
import com.unimag.avicontrol.soporte.FixtureJson;
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

/** Contrato de POST /api/v1/galpones (US1): compara las respuestas con los fixtures compartidos. */
@WebMvcTest(GalponController.class)
@Import({GlobalExceptionHandler.class, JacksonConfig.class})
class GalponControllerTest {

    private static final String RUTA = "/api/v1/galpones";
    private static final UUID ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private RegistrarGalponUseCase registrarGalpon;

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
}
