package com.fabrica.app.catalogodeproductos;

import static com.fabrica.app.catalogodeproductos.ExcelProductosTestUtil.celda;
import static com.fabrica.app.catalogodeproductos.ExcelProductosTestUtil.filas;
import static com.fabrica.app.catalogodeproductos.ExcelProductosTestUtil.hoja;
import static com.fabrica.app.catalogodeproductos.ExcelProductosTestUtil.partes;
import static com.fabrica.app.catalogodeproductos.ExcelProductosTestUtil.valor;
import static com.fabrica.app.catalogodeproductos.ExcelProductosTestUtil.xml;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.fabrica.app.catalogodeproductos.ExportacionProductosRepository.FilaProducto;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

class ExcelProductosWriterTest {

    private final ExcelProductosWriter escritor = new ExcelProductosWriter();

    @Test
    void hu_123_c1_sin_productos_produce_xlsx_valido_con_las_once_cabeceras_y_ningun_producto() throws Exception {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        escritor.escribir(salida, consumidor -> { });
        byte[] libro = salida.toByteArray();
        assertThat(libro).startsWith((byte) 'P', (byte) 'K');
        Map<String, byte[]> partes = partes(libro);
        assertThat(partes.keySet()).containsExactlyInAnyOrder("[Content_Types].xml", "_rels/.rels",
                "xl/workbook.xml", "xl/_rels/workbook.xml.rels", "xl/worksheets/sheet1.xml");
        for (byte[] parte : partes.values()) {
            Document documento = xml(parte);
            assertThat(documento.getDocumentElement()).isNotNull();
        }
        Document hoja = hoja(libro);
        assertThat(filas(hoja).getLength()).isEqualTo(1);
        assertThat(((Element) filas(hoja).item(0)).getAttribute("r")).isEqualTo("1");
        assertThat(((Element) filas(hoja).item(0)).getElementsByTagNameNS(
                "http://schemas.openxmlformats.org/spreadsheetml/2006/main", "c").getLength()).isEqualTo(11);
        String[] encabezados = { "id", "codigo", "nombre_comercial", "descripcion", "categoria",
                "subcategoria", "precio_base", "moneda", "stock", "imagen", "estado" };
        for (int i = 0; i < encabezados.length; i++) {
            Element celda = celda(hoja, (char) ('A' + i) + "1");
            assertThat(celda.getAttribute("t")).isEqualTo("inlineStr");
            assertThat(valor(celda)).isEqualTo(encabezados[i]);
        }
    }

    @Test
    void hu_123_c2_escribe_filas_con_precio_y_stock_exactos_como_numeros() throws Exception {
        FilaProducto primero = new FilaProducto(7L, "ABC", "Tienda", "Descripción", "Muebles",
                "Mesas", new BigDecimal("123.456700"), "EUR", 1234567890123L, "mesa.png", "ACTIVO");
        FilaProducto segundo = new FilaProducto(8L, "DEF", "Otra", "Otra descripción", "Hogar",
                "Lámparas", new BigDecimal("0.0100"), "USD", 0L, "luz.png", "INACTIVO");
        Document hoja = hoja(escribir(List.of(primero, segundo)));
        assertThat(filas(hoja).getLength()).isEqualTo(3);
        assertThat(valor(celda(hoja, "A2"))).isEqualTo("7");
        assertThat(valor(celda(hoja, "B2"))).isEqualTo("ABC");
        assertThat(valor(celda(hoja, "C2"))).isEqualTo("Tienda");
        assertThat(valor(celda(hoja, "D2"))).isEqualTo("Descripción");
        assertThat(valor(celda(hoja, "E2"))).isEqualTo("Muebles");
        assertThat(valor(celda(hoja, "F2"))).isEqualTo("Mesas");
        assertThat(valor(celda(hoja, "H2"))).isEqualTo("EUR");
        assertThat(valor(celda(hoja, "J2"))).isEqualTo("mesa.png");
        assertThat(valor(celda(hoja, "K2"))).isEqualTo("ACTIVO");
        assertThat(valor(celda(hoja, "G2"))).isEqualTo("123.456700");
        assertThat(valor(celda(hoja, "I2"))).isEqualTo("1234567890123");
        assertThat(valor(celda(hoja, "G3"))).isEqualTo("0.0100");
        assertThat(valor(celda(hoja, "I3"))).isEqualTo("0");
        for (String referencia : List.of("A2", "G2", "I2", "A3", "G3", "I3")) {
            assertThat(celda(hoja, referencia).getAttribute("t")).isEqualTo("n");
        }
        assertThat(valor(celda(hoja, "K3"))).isEqualTo("INACTIVO");
    }

    @Test
    void textos_parecidos_a_formulas_se_conservan_como_cadenas_y_escapan_xml() throws Exception {
        String[] literales = { "=SUM(A1:A2)", "+1+2", "-2+3", "@usuario", "<&> \"café\"" };
        Document hoja = hoja(escribir(List.of(new FilaProducto(1L, literales[0], literales[1],
                literales[2], literales[3], literales[4], new BigDecimal("2.00"), "EUR", 1L,
                "=HYPERLINK(\"http://ejemplo\")", "ACTIVO"))));
        for (int i = 0; i < literales.length; i++) {
            Element celda = celda(hoja, (char) ('B' + i) + "2");
            assertThat(celda.getAttribute("t")).isEqualTo("inlineStr");
            assertThat(valor(celda)).isEqualTo(literales[i]);
            assertThat(celda.getElementsByTagNameNS(
                    "http://schemas.openxmlformats.org/spreadsheetml/2006/main", "f").getLength()).isZero();
        }
        assertThat(celda(hoja, "J2").getAttribute("t")).isEqualTo("inlineStr");
        assertThat(valor(celda(hoja, "J2"))).startsWith("=HYPERLINK");
        assertThat(hoja.getElementsByTagNameNS(
                "http://schemas.openxmlformats.org/spreadsheetml/2006/main", "f").getLength()).isZero();
    }

    @Test
    void el_proveedor_entrega_filas_de_una_en_una_y_el_destino_sigue_utilizable() throws Exception {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        escritor.escribir(salida, consumidor -> {
            consumidor.accept(new FilaProducto(10L, "A", "Uno", null, null, null,
                    null, null, null, null, null));
            consumidor.accept(new FilaProducto(11L, "B", "Dos", null, null, null,
                    null, null, null, null, null));
        });
        Document hoja = hoja(salida.toByteArray());
        assertThat(filas(hoja).getLength()).isEqualTo(3);
        assertThat(valor(celda(hoja, "A3"))).isEqualTo("11");
        assertThat(valor(celda(hoja, "B3"))).isEqualTo("B");
        assertThat(((Element) filas(hoja).item(1)).getElementsByTagNameNS(
                "http://schemas.openxmlformats.org/spreadsheetml/2006/main", "c").getLength()).isEqualTo(3);
        salida.write('!'); // El escritor no es dueño del OutputStream del solicitante.
    }

    private byte[] escribir(List<FilaProducto> productos) throws Exception {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        escritor.escribir(salida, consumidor -> productos.forEach(consumidor));
        return salida.toByteArray();
    }
}
