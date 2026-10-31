package com.fabrica.app.catalogodeproductos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Verifica el contrato de la descarga contra productos registrados y tokens reales de desarrollo. */
@SpringBootTest
@AutoConfigureMockMvc
class ExportacionCsvIntegrationTest {
    private static final String RUTA = "/api/productos/exportaciones/csv";
    private static final String CABECERA =
            "codigo,nombre_comercial,categoria,subcategoria,precio_base,moneda,stock,estado\r\n";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void preparar_catalogo() {
        jdbc.update("delete from productos");
        insertar(701L, "CAF,701", "Café \"especial\"\nGrande", "Bebidas", "Café, molido",
                new BigDecimal("9999999999999.99"), 2147483647L, "ACTIVO");
        insertar(702L, "TE-702", "Té", "Bebidas", "Infusiones",
                new BigDecimal("0.01"), 0L, "INACTIVO");
        insertar(703L, "TAZA-703", "Taza", "Hogar", "Cocina",
                new BigDecimal("19.20"), 42L, "ACTIVO");
    }

    @Test
    void hu_124_c1_sin_filtros_descarga_productos_con_cabecera_y_escapado_utf_8() throws Exception {
        MvcResult respuesta = descargar(gestor());
        assertThat(respuesta.getResponse().getContentAsByteArray()).isEqualTo((CABECERA
                + "\"CAF,701\",\"Café \"\"especial\"\"\nGrande\",Bebidas,\"Café, molido\","
                + "9999999999999.99,EUR,2147483647,ACTIVO\r\n"
                + "TE-702,Té,Bebidas,Infusiones,0.01,EUR,0,INACTIVO\r\n"
                + "TAZA-703,Taza,Hogar,Cocina,19.20,EUR,42,ACTIVO\r\n")
                .getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void hu_124_c2_precio_base_y_stock_son_los_valores_persistidos_sin_conversion() throws Exception {
        String csv = new String(descargar(gestor()).getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        for (long id : new long[] {701L, 702L, 703L}) {
            String codigo = jdbc.queryForObject("select codigo from productos where id = ?", String.class, id);
            BigDecimal precio = jdbc.queryForObject("select precio_base from productos where id = ?",
                    BigDecimal.class, id);
            Long stock = jdbc.queryForObject("select stock from productos where id = ?", Long.class, id);
            String fila = id == 701L ? "\"CAF,701\",\"Café \"\"especial\"\"\nGrande\",Bebidas,\"Café, molido\","
                    : id == 702L ? "TE-702,Té,Bebidas,Infusiones," : "TAZA-703,Taza,Hogar,Cocina,";
            assertThat(codigo).isNotBlank();
            assertThat(csv).contains(fila + precio.toPlainString() + ",EUR," + stock + ","
                    + (id == 702L ? "INACTIVO" : "ACTIVO") + "\r\n");
        }
    }

    @Test
    void filtros_combinados_se_aplican_y_una_consulta_sin_resultados_descarga_solo_cabecera() throws Exception {
        String csv = descargar(gestor().param("categoria", "Bebidas")
                .param("subcategoria", "Infusiones").param("estado", "INACTIVO"))
                .getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv).isEqualTo(CABECERA + "TE-702,Té,Bebidas,Infusiones,0.01,EUR,0,INACTIVO\r\n");
        assertThat(descargar(gestor().param("categoria", "Hogar").param("estado", "INACTIVO"))
                .getResponse().getContentAsByteArray()).isEqualTo(CABECERA.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void filtros_vacios_excesivos_y_estado_desconocido_devuelven_400_sin_descarga() throws Exception {
        for (String campo : new String[] {"categoria", "subcategoria", "estado"}) {
            comprobarProblema(gestor().param(campo, ""), 400, campo);
            comprobarProblema(gestor().param(campo, " "), 400, campo);
            comprobarProblema(gestor().param(campo, "x".repeat(campo.equals("estado") ? 21 : 121)),
                    400, campo);
        }
        comprobarProblema(gestor().param("estado", "BORRADO"), 400, "estado");
    }

    @Test
    void peticion_anonima_y_token_invalido_devuelven_401_problem_detail_sin_archivo() throws Exception {
        comprobarProblema(get(RUTA), 401, null);
        comprobarProblema(get(RUTA).header(HttpHeaders.AUTHORIZATION, "Bearer invalido"), 401, null);
    }

    @Test
    void rol_sin_permiso_no_recibe_archivo_ni_puede_aplicar_filtros() throws Exception {
        String lector = token("LECTOR");
        comprobarProblema(get(RUTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + lector), 403, "permiso");
        comprobarProblema(get(RUTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + lector)
                .param("categoria", "Bebidas").param("estado", "ACTIVO"), 403, "permiso");
    }

    private MvcResult descargar(MockHttpServletRequestBuilder peticion) throws Exception {
        MvcResult inicio = mockMvc.perform(peticion).andExpect(request().asyncStarted()).andReturn();
        return mockMvc.perform(asyncDispatch(inicio))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"productos.csv\""))
                .andReturn();
    }

    private void comprobarProblema(MockHttpServletRequestBuilder peticion, int codigo, String detalle)
            throws Exception {
        MvcResult resultado = mockMvc.perform(peticion)
                .andExpect(status().is(codigo))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.title").isString())
                .andExpect(jsonPath("$.status").value(codigo))
                .andExpect(jsonPath("$.detail").isString())
                .andReturn();
        assertThat(resultado.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION)).isNull();
        assertThat(resultado.getResponse().getContentAsString()).doesNotContain(CABECERA.trim());
        if (detalle != null) {
            assertThat(JsonPath.<String>read(resultado.getResponse().getContentAsString(), "$.detail"))
                    .contains(detalle);
        }
    }

    private MockHttpServletRequestBuilder gestor() throws Exception {
        return get(RUTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + token("GESTOR_PRODUCTOS"));
    }

    private String token(String rol) throws Exception {
        String respuesta = mockMvc.perform(get("/api/dev/token").param("usuario", "verificacion-csv")
                .param("roles", rol)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(respuesta, "$.token");
    }

    private void insertar(Long id, String codigo, String nombre, String categoria, String subcategoria,
            BigDecimal precio, Long stock, String estado) {
        jdbc.update("insert into productos (id, codigo, nombre_comercial, descripcion, categoria, "
                        + "subcategoria, precio_base, moneda, stock, imagen, estado) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, codigo, nombre, "Producto del catálogo", categoria, subcategoria,
                precio, "EUR", stock, "foto.png", estado);
    }
}
