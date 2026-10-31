package com.fabrica.app.catalogodeproductos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

/** Comprueba los bytes entregados por el endpoint con los valores efectivamente persistidos. */
@SpringBootTest
@AutoConfigureMockMvc
class ExportacionCsvFormatoIntegracionTest {

    private static final String RUTA = "/api/productos/exportaciones/csv";
    private static final String CABECERA =
            "codigo,nombre_comercial,categoria,subcategoria,precio_base,moneda,stock,estado\r\n";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void limpiar_catalogo() {
        jdbc.update("delete from productos");
    }

    @Test
    void hu_124_c1_sin_filtros_descarga_csv_utf_8_con_cabecera_y_todos_los_productos() throws Exception {
        registrar(101L, "C,1", "Café \"especial\"\nGrande", "Bebidas, frías", "Té\r\nverde",
                new BigDecimal("9999999999999.99"), 2147483647L, "ACTIVO");
        registrar(102L, "C-2", "Tetera", "Cocina", "Infusiones",
                new BigDecimal("0.01"), 0L, "INACTIVO");

        MvcResult resultado = descargar(null);

        assertThat(resultado.getResponse().getContentType()).startsWith("text/csv");
        assertThat(resultado.getResponse().getContentAsByteArray()).isEqualTo((CABECERA
                + "\"C,1\",\"Café \"\"especial\"\"\nGrande\",\"Bebidas, frías\",\"Té\r\nverde\","
                + "9999999999999.99,EUR,2147483647,ACTIVO\r\n"
                + "C-2,Tetera,Cocina,Infusiones,0.01,EUR,0,INACTIVO\r\n")
                .getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void hu_124_c2_precio_y_stock_de_la_descarga_coinciden_con_los_valores_registrados() throws Exception {
        registrar(103L, "P-103", "Molino", "Hogar", "Cocina",
                new BigDecimal("9999999999999.99"), 2147483647L, "ACTIVO");
        registrar(104L, "P-104", "Vaso", "Hogar", "Cocina",
                new BigDecimal("0.01"), 0L, "ACTIVO");

        String csv = new String(descargar(null).getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);

        for (long id : new long[] {103L, 104L}) {
            String codigo = jdbc.queryForObject("select codigo from productos where id = ?", String.class, id);
            BigDecimal precio = jdbc.queryForObject("select precio_base from productos where id = ?",
                    BigDecimal.class, id);
            Long stock = jdbc.queryForObject("select stock from productos where id = ?", Long.class, id);
            String nombre = id == 103L ? "Molino" : "Vaso";
            assertThat(csv).contains(codigo + "," + nombre + ",Hogar,Cocina,"
                    + precio.toPlainString() + ",EUR," + stock + ",ACTIVO\r\n");
        }
    }

    @Test
    void sin_coincidencias_descarga_un_csv_valido_con_solo_la_cabecera() throws Exception {
        registrar(105L, "P-105", "Taza", "Hogar", "Cocina",
                new BigDecimal("1.00"), 1L, "ACTIVO");

        MvcResult resultado = descargar("No existe");

        assertThat(resultado.getResponse().getContentAsByteArray())
                .isEqualTo(CABECERA.getBytes(StandardCharsets.UTF_8));
    }

    private MvcResult descargar(String categoria) throws Exception {
        String cuerpoToken = mockMvc.perform(get("/api/dev/token")
                        .param("usuario", "gestora").param("roles", "GESTOR_PRODUCTOS"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(cuerpoToken, "$.token");
        var peticion = get(RUTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (categoria != null) {
            peticion.param("categoria", categoria);
        }
        MvcResult inicio = mockMvc.perform(peticion).andExpect(request().asyncStarted()).andReturn();
        return mockMvc.perform(asyncDispatch(inicio)).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"productos.csv\""))
                .andReturn();
    }

    private void registrar(Long id, String codigo, String nombre, String categoria, String subcategoria,
            BigDecimal precio, Long stock, String estado) {
        jdbc.update("insert into productos (id, codigo, nombre_comercial, descripcion, categoria, "
                        + "subcategoria, precio_base, moneda, stock, imagen, estado) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, codigo, nombre, "Producto del catálogo", categoria, subcategoria,
                precio, "EUR", stock, "imagen.png", estado);
    }
}
