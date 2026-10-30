package com.fabrica.app.catalogodeproductos;

import static com.fabrica.app.catalogodeproductos.ExcelProductosTestUtil.celda;
import static com.fabrica.app.catalogodeproductos.ExcelProductosTestUtil.filas;
import static com.fabrica.app.catalogodeproductos.ExcelProductosTestUtil.hoja;
import static com.fabrica.app.catalogodeproductos.ExcelProductosTestUtil.partes;
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
import java.util.List;

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
import org.w3c.dom.Document;

/** Verificación de punta a punta HTTP -> H2 -> cursor -> XLSX, incluidos los errores del contrato. */
@SpringBootTest
@AutoConfigureMockMvc
class ExportacionProductosIntegrationTest {

    private static final String RUTA = "/api/productos/exportaciones/excel";
    private static final String TIPO_EXCEL = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final List<String> COLUMNAS = List.of("id", "codigo", "nombre_comercial", "descripcion",
            "categoria", "subcategoria", "precio_base", "moneda", "stock", "imagen", "estado");

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void preparar_productos_persistidos() {
        jdbc.update("delete from productos");
        insertar(901L, "CAFE-901", "Cafetera", "=SUM(A1:A2)", "Cocina", "Café",
                "123.45", 12345678901L, "ACTIVO");
        insertar(902L, "TE-902", "Tetera", "Acero inoxidable", "Cocina", "Té",
                "0.01", 0L, "INACTIVO");
        insertar(903L, "MESA-903", "Mesa", "Mesa auxiliar", "Hogar", "Muebles",
                "999.99", 14L, "ACTIVO");
    }

    @Test
    void hu_123_c1_sin_filtros_descarga_todos_los_productos_con_las_once_columnas() throws Exception {
        byte[] libro = descargar(peticionConRol("GESTOR_PRODUCTOS"));
        assertThat(libro).startsWith((byte) 'P', (byte) 'K');
        assertThat(partes(libro)).containsKeys("[Content_Types].xml", "xl/workbook.xml",
                "xl/worksheets/sheet1.xml");
        Document hoja = hoja(libro);
        assertThat(filas(hoja).getLength()).isEqualTo(4);
        for (int i = 0; i < COLUMNAS.size(); i++) {
            assertThat(valor(celda(hoja, (char) ('A' + i) + "1"))).isEqualTo(COLUMNAS.get(i));
        }
        assertThat(valor(celda(hoja, "B2"))).isEqualTo("CAFE-901");
        assertThat(valor(celda(hoja, "B3"))).isEqualTo("TE-902");
        assertThat(valor(celda(hoja, "K3"))).isEqualTo("INACTIVO");
        assertThat(valor(celda(hoja, "B4"))).isEqualTo("MESA-903");
        assertThat(valor(celda(hoja, "D2"))).isEqualTo("=SUM(A1:A2)");
        assertThat(celda(hoja, "D2").getAttribute("t")).isEqualTo("inlineStr");
        assertThat(hoja.getElementsByTagNameNS(
                "http://schemas.openxmlformats.org/spreadsheetml/2006/main", "f").getLength()).isZero();
    }

    @Test
    void hu_123_c2_precios_y_existencias_se_conservan_como_numeros_de_cada_registro() throws Exception {
        Document hoja = hoja(descargar(peticionConRol("GESTOR_PRODUCTOS")));
        for (int numeroFila = 2; numeroFila <= 4; numeroFila++) {
            String fila = Integer.toString(numeroFila);
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
    void filtros_combinados_reducen_las_filas_y_el_estado_no_da_acceso_sin_permiso() throws Exception {
        Document hoja = hoja(descargar(peticionConRol("GESTOR_PRODUCTOS")
                .param("codigo", "CAFE-901").param("nombre_comercial", "Cafetera")
                .param("categoria", "Cocina").param("subcategoria", "Café")
                .param("estado", "ACTIVO")));
        assertThat(filas(hoja).getLength()).isEqualTo(2);
        assertThat(valor(celda(hoja, "B2"))).isEqualTo("CAFE-901");
        assertThat(valor(celda(hoja, "E2"))).isEqualTo("Cocina");
        assertThat(valor(celda(hoja, "F2"))).isEqualTo("Café");
        assertThat(valor(celda(hoja, "K2"))).isEqualTo("ACTIVO");

        Document porEstado = hoja(descargar(peticionConRol("GESTOR_PRODUCTOS").param("estado", "INACTIVO")));
        assertThat(filas(porEstado).getLength()).isEqualTo(2);
        assertThat(valor(celda(porEstado, "B2"))).isEqualTo("TE-902");
        mvc.perform(peticionConRol("LECTOR").param("estado", "ACTIVO"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void catalogo_sin_productos_y_filtros_sin_coincidencias_entregan_excel_con_solo_cabeceras() throws Exception {
        Document sinCoincidencias = hoja(descargar(peticionConRol("GESTOR_PRODUCTOS")
                .param("codigo", "NO-EXISTE")));
        comprobarSoloCabeceras(sinCoincidencias);

        jdbc.update("delete from productos");
        Document sinProductos = hoja(descargar(peticionConRol("GESTOR_PRODUCTOS")));
        comprobarSoloCabeceras(sinProductos);
    }

    @Test
    void filtros_invalidos_responden_400_con_problem_detail() throws Exception {
        for (String[] caso : List.of(new String[] {"codigo", "a".repeat(65)},
                new String[] {"nombre_comercial", "a".repeat(256)},
                new String[] {"categoria", "a".repeat(121)},
                new String[] {"subcategoria", "a".repeat(121)},
                new String[] {"estado", "a".repeat(21)},
                new String[] {"codigo", " "})) {
            mvc.perform(peticionConRol("GESTOR_PRODUCTOS").param(caso[0], caso[1]))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.type").isString())
                    .andExpect(jsonPath("$.title").isString())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString(caso[0])));
        }
    }

    @Test
    void ausencia_de_token_recibe_401_y_rol_sin_permiso_recibe_403_ambos_como_problem_detail() throws Exception {
        mvc.perform(get(RUTA))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").isString())
                .andExpect(jsonPath("$.title").isString())
                .andExpect(jsonPath("$.status").value(401));
        mvc.perform(peticionConRol("LECTOR"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").isString())
                .andExpect(jsonPath("$.title").isString())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("permiso")));
    }

    private void comprobarSoloCabeceras(Document documento) {
        assertThat(filas(documento).getLength()).isEqualTo(1);
        for (int i = 0; i < COLUMNAS.size(); i++) {
            assertThat(valor(celda(documento, (char) ('A' + i) + "1"))).isEqualTo(COLUMNAS.get(i));
        }
    }

    private byte[] descargar(MockHttpServletRequestBuilder peticion) throws Exception {
        MvcResult comienzo = mvc.perform(peticion).andExpect(request().asyncStarted()).andReturn();
        return mvc.perform(asyncDispatch(comienzo))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(TIPO_EXCEL))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"catalogo-productos.xlsx\""))
                .andReturn().getResponse().getContentAsByteArray();
    }

    private MockHttpServletRequestBuilder peticionConRol(String rol) throws Exception {
        String json = mvc.perform(get("/api/dev/token").param("usuario", "gestora")
                .param("roles", rol)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(json, "$.token");
        return get(RUTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private void insertar(Long id, String codigo, String nombre, String descripcion, String categoria,
            String subcategoria, String precio, Long stock, String estado) {
        jdbc.update("insert into productos (id, codigo, nombre_comercial, descripcion, categoria, "
                        + "subcategoria, precio_base, moneda, stock, imagen, estado) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, codigo, nombre, descripcion, categoria, subcategoria, new BigDecimal(precio), "EUR",
                stock, "foto.png", estado);
    }
}
