package com.fabrica.app.catalogodeproductos;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.fabrica.app.catalogodeproductos.ExportacionProductosRepository.FilaProducto;
import org.springframework.stereotype.Component;

/** Genera un XLSX mediante entradas ZIP sucesivas, sin retener las filas en memoria. */
@Component
public class ExcelProductosWriter {

    private static final String[] COLUMNAS = {
            "id", "codigo", "nombre_comercial", "descripcion", "categoria", "subcategoria",
            "precio_base", "moneda", "stock", "imagen", "estado"
    };

    /**
     * El proveedor entrega cada fila al consumidor mientras el libro está abierto. Puede delegar
     * directamente en {@link ExportacionProductosRepository#recorrer} para mantener vivo el cursor.
     * No cierra el flujo de destino: quien lo proporciona conserva su propiedad.
     */
    public void escribir(OutputStream destino, Consumer<Consumer<FilaProducto>> proveedor) throws IOException {
        Objects.requireNonNull(destino, "Se necesita un destino para el archivo Excel.");
        Objects.requireNonNull(proveedor, "Se necesita un proveedor de productos.");
        ZipOutputStream zip = new ZipOutputStream(destino, StandardCharsets.UTF_8);
        entrada(zip, "[Content_Types].xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                </Types>
                """);
        entrada(zip, "_rels/.rels", """
                <?xml version="1.0" encoding="UTF-8"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                </Relationships>
                """);
        entrada(zip, "xl/workbook.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <sheets><sheet name="Productos" sheetId="1" r:id="rId1"/></sheets>
                </workbook>
                """);
        entrada(zip, "xl/_rels/workbook.xml.rels", """
                <?xml version="1.0" encoding="UTF-8"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                </Relationships>
                """);
        zip.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
        BufferedWriter xml = new BufferedWriter(new OutputStreamWriter(zip, StandardCharsets.UTF_8));
        xml.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
        xml.write("<row r=\"1\">");
        for (int i = 0; i < COLUMNAS.length; i++) {
            texto(xml, 1, i, COLUMNAS[i]);
        }
        xml.write("</row>");
        int[] numeroFila = { 1 };
        try {
            proveedor.accept(fila -> {
                try {
                    escribirFila(xml, ++numeroFila[0], Objects.requireNonNull(fila, "La fila no puede ser nula."));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
        xml.write("</sheetData></worksheet>");
        xml.flush();
        zip.closeEntry();
        zip.finish();
        zip.flush();
    }

    private static void entrada(ZipOutputStream zip, String nombre, String contenido) throws IOException {
        zip.putNextEntry(new ZipEntry(nombre));
        zip.write(contenido.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static void escribirFila(BufferedWriter xml, int fila, FilaProducto producto) throws IOException {
        xml.write("<row r=\"" + fila + "\">");
        numero(xml, fila, 0, producto.id());
        texto(xml, fila, 1, producto.codigo());
        texto(xml, fila, 2, producto.nombreComercial());
        texto(xml, fila, 3, producto.descripcion());
        texto(xml, fila, 4, producto.categoria());
        texto(xml, fila, 5, producto.subcategoria());
        decimal(xml, fila, 6, producto.precioBase());
        texto(xml, fila, 7, producto.moneda());
        numero(xml, fila, 8, producto.stock());
        texto(xml, fila, 9, producto.imagen());
        texto(xml, fila, 10, producto.estado());
        xml.write("</row>");
    }

    private static String referencia(int columna, int fila) {
        return String.valueOf((char) ('A' + columna)) + fila;
    }

    private static void numero(BufferedWriter xml, int fila, int columna, Long valor) throws IOException {
        if (valor != null) {
            xml.write("<c r=\"" + referencia(columna, fila) + "\" t=\"n\"><v>" + valor + "</v></c>");
        }
    }

    private static void decimal(BufferedWriter xml, int fila, int columna, BigDecimal valor) throws IOException {
        if (valor != null) {
            xml.write("<c r=\"" + referencia(columna, fila) + "\" t=\"n\"><v>"
                    + valor.toPlainString() + "</v></c>");
        }
    }

    private static void texto(BufferedWriter xml, int fila, int columna, String valor) throws IOException {
        if (valor == null) {
            return;
        }
        // inlineStr impide que Excel interprete incluso =, +, - y @ como fórmulas.
        xml.write("<c r=\"" + referencia(columna, fila) + "\" t=\"inlineStr\"><is><t xml:space=\"preserve\">");
        valor.codePoints().forEach(cp -> {
            try {
                if (cp == '&') {
                    xml.write("&amp;");
                } else if (cp == '<') {
                    xml.write("&lt;");
                } else if (cp == '>') {
                    xml.write("&gt;");
                } else if (cp == '\r') {
                    xml.write("&#13;");
                } else if (cp == '\n' || cp == '\t' || cp >= 0x20 && cp <= 0xD7FF
                        || cp >= 0xE000 && cp <= 0xFFFD || cp >= 0x10000 && cp <= 0x10FFFF) {
                    xml.write(Character.toChars(cp));
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        xml.write("</t></is></c>");
    }
}
