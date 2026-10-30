package com.fabrica.app.infra.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
class AutenticacionTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TokenDev tokens;

    @Test
    void una_ruta_protegida_sin_token_o_con_un_token_falso_responde_401() throws Exception {
        mockMvc.perform(get("/api/protegida")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/protegida").header("Authorization", "Bearer a.b.c"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void con_el_token_de_desarrollo_la_peticion_pasa_la_autenticacion() throws Exception {
        String cuerpo = mockMvc.perform(get("/api/dev/token").param("usuario", "ana").param("roles", "empleado"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario").value("ana"))
                .andExpect(jsonPath("$.roles[0]").value("empleado"))
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(cuerpo, "$.token");
        // Pasa el filtro: como la ruta no existe, la respuesta es 404 y no 401.
        mockMvc.perform(get("/api/protegida").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void un_token_alterado_o_vencido_no_es_valido() {
        String[] partes = tokens.firmar(new Usuario("ana", List.of()), 3600).split("\\.");
        String otro = tokens.firmar(new Usuario("admin", List.of("admin")), 3600).split("\\.")[1];
        assertThat(tokens.verificar(partes[0] + "." + otro + "." + partes[2])).isEmpty();
        assertThat(tokens.verificar(tokens.firmar(new Usuario("ana", List.of()), -1))).isEmpty();
        assertThat(tokens.verificar(String.join(".", partes))).contains(new Usuario("ana", List.of()));
    }
}
