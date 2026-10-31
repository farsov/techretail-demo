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

@SpringBootTest
@AutoConfigureMockMvc
class ExportacionCsvControllerIntegracionTest {

    private static final String RUTA = "/api/productos/exportaciones/csv";
    private static final String CABECERA =
            "codigo,nombre_comercial,categoria,subcategoria,precio_base,moneda,stock,estado\r\n";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void preparar_productos() {
        jdbc.update("delete from productos");
        registrar(401L, "C-401", "Cafetera", new BigDecimal("9999999999999.99"), 2147483647L, "ACTIVO");
        registrar(402L, "C-402", "Tetera", new BigDecimal("0.01"), 0L, "INACTIVO");
    }

    @Test
    void hu_124_c1_sin_filtros_obtiene_csv_con_productos_del_catalogo() throws Exception {
        String csv = descargar(peticionGestor());
        assertThat(csv).startsWith(CABECERA);
        assertThat(csv).contains("C-401,Cafetera,Electrodomésticos,Cocina,");
        assertThat(csv).contains("C-402,Tetera,Electrodomésticos,Cocina,");
        assertThat(csv.split("\r\n")).hasSize(3);
    }

    @Test
    void hu_124_c2_los_precios_base_y_existencias_coinciden_con_los_registrados() throws Exception {
        String csv = descargar(peticionGestor());
        for (long id : new long[] {401L, 402L}) {
            String codigo = jdbc.queryForObject("select codigo from productos where id = ?", String.class, id);
            BigDecimal precio = jdbc.queryForObject("select precio_base from productos where id = ?",
                    BigDecimal.class, id);
            Long stock = jdbc.queryForObject("select stock from productos where id = ?", Long.class, id);
            assertThat(csv).contains(codigo + "," + (id == 401L ? "Cafetera" : "Tetera")
                    + ",Electrodomésticos,Cocina," + precio.toPlainString() + ",EUR," + stock + ",");
        }
    }

    @Test
    void filtros_combinados_restringen_resultados_y_sin_coincidencias_entregan_solo_cabecera()
            throws Exception {
        assertThat(descargar(peticionGestor().param("categoria", "Electrodomésticos")
                .param("subcategoria", "Cocina").param("estado", "ACTIVO")))
                .contains("C-401,").doesNotContain("C-402,");
        assertThat(descargar(peticionGestor().param("estado", "ACTIVO")
                .param("categoria", "Sin productos"))).isEqualTo(CABECERA);
    }

    @Test
    void filtros_invalidos_reciben_400_problem_detail_y_no_reciben_archivo() throws Exception {
        comprobarFiltroInvalido("categoria", " ");
        comprobarFiltroInvalido("categoria", "a".repeat(121));
        comprobarFiltroInvalido("subcategoria", "");
        comprobarFiltroInvalido("subcategoria", "s".repeat(121));
        comprobarFiltroInvalido("estado", "a".repeat(21));
        comprobarFiltroInvalido("estado", "DESCONOCIDO");
    }

    @Test
    void sin_autenticacion_recibe_401_y_sin_permiso_o_con_filtro_no_autorizado_recibe_403()
            throws Exception {
        comprobarProblema(get(RUTA), 401);
        comprobarProblema(get(RUTA).header(HttpHeaders.AUTHORIZATION, "Bearer token-invalido"), 401);
        comprobarProblema(get(RUTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + token("LECTOR")), 403);
        comprobarProblema(get(RUTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + token("LECTOR"))
                .param("estado", "ACTIVO"), 403);
    }

    private void comprobarFiltroInvalido(String campo, String valor) throws Exception {
        MvcResult resultado = comprobarProblema(peticionGestor().param(campo, valor), 400);
        assertThat(JsonPath.<String>read(resultado.getResponse().getContentAsString(), "$.detail"))
                .contains(campo);
    }

    private MvcResult comprobarProblema(MockHttpServletRequestBuilder peticion, int codigo) throws Exception {
        MvcResult resultado = mockMvc.perform(peticion)
                .andExpect(status().is(codigo))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.title").isString())
                .andExpect(jsonPath("$.status").value(codigo))
                .andExpect(jsonPath("$.detail").isString())
                .andReturn();
        assertThat(resultado.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION)).isNull();
        return resultado;
    }

    private String descargar(MockHttpServletRequestBuilder peticion) throws Exception {
        MvcResult inicio = mockMvc.perform(peticion).andExpect(request().asyncStarted()).andReturn();
        MvcResult fin = mockMvc.perform(asyncDispatch(inicio))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"productos.csv\""))
                .andReturn();
        return new String(fin.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    private MockHttpServletRequestBuilder peticionGestor() throws Exception {
        return get(RUTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + token("GESTOR_PRODUCTOS"));
    }

    private String token(String roles) throws Exception {
        String cuerpo = mockMvc.perform(get("/api/dev/token").param("usuario", "gestora")
                .param("roles", roles)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(cuerpo, "$.token");
    }

    private void registrar(Long id, String codigo, String nombre, BigDecimal precio, Long stock, String estado) {
        jdbc.update("insert into productos (id, codigo, nombre_comercial, descripcion, categoria, "
                        + "subcategoria, precio_base, moneda, stock, imagen, estado) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, codigo, nombre, "Producto de cocina", "Electrodomésticos", "Cocina",
                precio, "EUR", stock, "imagen.png", estado);
    }
}
