package com.fabrica.app.catalogodeproductos;

import static com.fabrica.app.catalogodeproductos.ExcelProductosTestUtil.celda;
import static com.fabrica.app.catalogodeproductos.ExcelProductosTestUtil.filas;
import static com.fabrica.app.catalogodeproductos.ExcelProductosTestUtil.hoja;
import static com.fabrica.app.catalogodeproductos.ExcelProductosTestUtil.valor;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;

import com.fabrica.app.infra.auth.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;

/** Recorre productos persistidos en H2 y examina el XLSX producido a partir del cursor JPA. */
@SpringBootTest
@Transactional
class ExcelProductosWriterIntegracionTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ExportacionProductosRepository repositorio;

    @Autowired
    private ExcelProductosWriter escritor;

    private final Usuario usuario = new Usuario("equipo-a", List.of());
    private final ExportacionProductosRepository.Visibilidad visibilidad = (raiz, cb, solicitante) ->
            cb.like(raiz.get("codigo"), solicitante.sub() + "-%");

    @BeforeEach
    void preparar_catalogo() {
        jdbc.update("delete from productos");
        registrar(201L, "equipo-a-1", new BigDecimal("123.45"), 12345678901L, "ACTIVO");
        registrar(202L, "equipo-a-2", new BigDecimal("0.01"), 0L, "INACTIVO");
        registrar(203L, "equipo-b-1", new BigDecimal("999.99"), 88L, "ACTIVO");
    }

    @Test
    void hu_123_c1_sin_filtros_exporta_todos_los_productos_visibles_y_no_los_ajenos() throws Exception {
        Document hoja = hoja(exportar(new FiltrosExportacion(null, null, null, null, null)));
        assertThat(filas(hoja).getLength()).isEqualTo(3); // cabecera y dos productos visibles
        assertThat(valor(celda(hoja, "A1"))).isEqualTo("id");
        assertThat(valor(celda(hoja, "K1"))).isEqualTo("estado");
        assertThat(valor(celda(hoja, "B2"))).isEqualTo("equipo-a-1");
        assertThat(valor(celda(hoja, "B3"))).isEqualTo("equipo-a-2");
        assertThat(valor(celda(hoja, "K3"))).isEqualTo("INACTIVO");
    }

    @Test
    void hu_123_c2_el_excel_contiene_precios_y_existencias_registrados_para_cada_producto() throws Exception {
        Document hoja = hoja(exportar(new FiltrosExportacion(null, null, null, null, null)));
        for (int fila = 2; fila <= 3; fila++) {
            Long id = Long.valueOf(valor(celda(hoja, "A" + fila)));
            BigDecimal registrado = jdbc.queryForObject(
                    "select precio_base from productos where id = ?", BigDecimal.class, id);
            Long existencias = jdbc.queryForObject("select stock from productos where id = ?", Long.class, id);
            assertThat(new BigDecimal(valor(celda(hoja, "G" + fila)))).isEqualByComparingTo(registrado);
            assertThat(valor(celda(hoja, "G" + fila))).isEqualTo(registrado.toPlainString());
            assertThat(celda(hoja, "G" + fila).getAttribute("t")).isEqualTo("n");
            assertThat(valor(celda(hoja, "I" + fila))).isEqualTo(existencias.toString());
            assertThat(celda(hoja, "I" + fila).getAttribute("t")).isEqualTo("n");
        }
    }

    @Test
    void sin_resultados_exporta_solo_cabeceras_y_un_filtro_de_estado_no_elude_la_visibilidad() throws Exception {
        Document sinCoincidencias = hoja(exportar(new FiltrosExportacion("equipo-b-1", null,
                null, null, "ACTIVO")));
        assertThat(filas(sinCoincidencias).getLength()).isEqualTo(1);
        assertThat(valor(celda(sinCoincidencias, "A1"))).isEqualTo("id");
        assertThat(valor(celda(sinCoincidencias, "K1"))).isEqualTo("estado");

        Document filtrado = hoja(exportar(new FiltrosExportacion(null, null, null, null, "ACTIVO")));
        assertThat(filas(filtrado).getLength()).isEqualTo(2);
        assertThat(valor(celda(filtrado, "B2"))).isEqualTo("equipo-a-1");
    }

    private byte[] exportar(FiltrosExportacion filtros) throws Exception {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        escritor.escribir(salida, consumidor -> repositorio.recorrer(usuario, filtros, visibilidad, consumidor));
        return salida.toByteArray();
    }

    private void registrar(Long id, String codigo, BigDecimal precio, Long stock, String estado) {
        jdbc.update("insert into productos (id, codigo, nombre_comercial, descripcion, categoria, "
                        + "subcategoria, precio_base, moneda, stock, imagen, estado) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, codigo, "Producto " + codigo, "Descripción", "Categoría", "Subcategoría",
                precio, "EUR", stock, "imagen.png", estado);
    }
}
