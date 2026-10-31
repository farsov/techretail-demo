package com.fabrica.app.catalogodeproductos;

import static com.fabrica.app.catalogodeproductos.ExcelProductosTestUtil.celda;
import static com.fabrica.app.catalogodeproductos.ExcelProductosTestUtil.filas;
import static com.fabrica.app.catalogodeproductos.ExcelProductosTestUtil.hoja;
import static com.fabrica.app.catalogodeproductos.ExcelProductosTestUtil.valor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.w3c.dom.Document;

/** Comprueba el contrato HTTP, el filtro de autenticación y el XLSX real desde productos persistidos. */
@SpringBootTest
@AutoConfigureMockMvc
class ExportacionProductosControllerIntegracionTest {

    private static final String RUTA = "/api/productos/exportaciones/excel";
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void preparar_catalogo() {
        jdbc.update("delete from productos");
        registrar(301L, "C-301", "Cafetera", new BigDecimal("123.45"), 12345678901L, "ACTIVO");
        registrar(302L, "C-302", "Tetera", new BigDecimal("0.01"), 0L, "INACTIVO");
    }

    @Test
    void hu_123_c1_sin_filtros_descarga_xlsx_con_todos_los_productos_y_encabezados() throws Exception {
        Document hoja = hoja(descargar(peticionGestor()));
        assertThat(filas(hoja).getLength()).isEqualTo(3);
        String[] cabeceras = {"id", "codigo", "nombre_comercial", "descripcion", "categoria",
                "subcategoria", "precio_base", "moneda", "stock", "imagen", "estado"};
        for (int i = 0; i < cabeceras.length; i++) {
            assertThat(valor(celda(hoja, (char) ('A' + i) + "1"))).isEqualTo(cabeceras[i]);
        }
        assertThat(valor(celda(hoja, "B2"))).isEqualTo("C-301");
        assertThat(valor(celda(hoja, "B3"))).isEqualTo("C-302");
    }

    @Test
    void hu_123_c2_precios_y_existencias_del_excel_coinciden_con_los_datos_persistidos() throws Exception {
        Document hoja = hoja(descargar(peticionGestor()));
        for (int fila = 2; fila <= 3; fila++) {
            Long id = Long.valueOf(valor(celda(hoja, "A" + fila)));
            BigDecimal precio = jdbc.queryForObject("select precio_base from productos where id = ?",
                    BigDecimal.class, id);
            Long stock = jdbc.queryForObject("select stock from productos where id = ?", Long.class, id);
            assertThat(celda(hoja, "G" + fila).getAttribute("t")).isEqualTo("n");
            assertThat(valor(celda(hoja, "G" + fila))).isEqualTo(precio.toPlainString());
            assertThat(celda(hoja, "I" + fila).getAttribute("t")).isEqualTo("n");
            assertThat(valor(celda(hoja, "I" + fila))).isEqualTo(stock.toString());
        }
    }

    @Test
    void los_filtros_se_combinan_y_sin_coincidencias_el_excel_solo_tiene_cabeceras() throws Exception {
        Document filtrada = hoja(descargar(peticionGestor().param("categoria", "Electrodomésticos")
                .param("estado", "ACTIVO").param("codigo", "C-301")
                .param("nombre_comercial", "Cafetera").param("subcategoria", "Cocina")));
        assertThat(filas(filtrada).getLength()).isEqualTo(2);
        assertThat(valor(celda(filtrada, "B2"))).isEqualTo("C-301");

        Document vacia = hoja(descargar(peticionGestor().param("estado", "ACTIVO")
                .param("codigo", "C-302")));
        assertThat(filas(vacia).getLength()).isEqualTo(1);
        assertThat(valor(celda(vacia, "A1"))).isEqualTo("id");
        assertThat(valor(celda(vacia, "K1"))).isEqualTo("estado");
    }

    @Test
    void un_filtro_vacio_o_que_supera_su_longitud_responde_400_con_problem_detail() throws Exception {
        comprobarFiltroInvalido("codigo", " ");
        comprobarFiltroInvalido("codigo", "a".repeat(65));
        comprobarFiltroInvalido("nombre_comercial", "a".repeat(256));
        comprobarFiltroInvalido("categoria", "a".repeat(121));
        comprobarFiltroInvalido("subcategoria", "a".repeat(121));
        comprobarFiltroInvalido("estado", "a".repeat(21));
    }

    @Test
    void sin_autenticacion_recibe_401_con_problem_detail() throws Exception {
        mockMvc.perform(get(RUTA)).andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").isString())
                .andExpect(jsonPath("$.title").isString())
                .andExpect(jsonPath("$.status").value(401));
        mockMvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, "Bearer token-falso"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").isString());
    }

    @Test
    void sin_permiso_de_exportacion_recibe_403_con_problem_detail() throws Exception {
        mockMvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + token("LECTOR"))
                        .param("estado", "ACTIVO"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").isString())
                .andExpect(jsonPath("$.title").isString())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("permiso")));
    }

    private void comprobarFiltroInvalido(String nombre, String valor) throws Exception {
        mockMvc.perform(peticionGestor().param(nombre, valor))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").isString())
                .andExpect(jsonPath("$.title").isString())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString(nombre)));
    }

    private byte[] descargar(MockHttpServletRequestBuilder peticion) throws Exception {
        MvcResult inicio = mockMvc.perform(peticion)
                .andExpect(request().asyncStarted())
                .andReturn();
        return mockMvc.perform(asyncDispatch(inicio))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.parseMediaType(XLSX)))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"catalogo-productos.xlsx\""))
                .andReturn().getResponse().getContentAsByteArray();
    }

    private MockHttpServletRequestBuilder peticionGestor() throws Exception {
        return get(RUTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + token("GESTOR_PRODUCTOS"));
    }

    private String token(String roles) throws Exception {
        String cuerpo = mockMvc.perform(get("/api/dev/token").param("usuario", "gestora")
                        .param("roles", roles))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
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
