package com.fabrica.app.catalogodeproductos;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/** Inspecciona el XLSX real sin añadir una dependencia de lectura de hojas de cálculo. */
final class ExcelProductosTestUtil {

    private static final String NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";

    private ExcelProductosTestUtil() {
    }

    static Map<String, byte[]> partes(byte[] archivo) throws IOException {
        Map<String, byte[]> resultado = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archivo))) {
            ZipEntry entrada;
            while ((entrada = zip.getNextEntry()) != null) {
                resultado.put(entrada.getName(), zip.readAllBytes());
                zip.closeEntry();
            }
        }
        return resultado;
    }

    static Document xml(byte[] contenido) throws Exception {
        DocumentBuilderFactory fabrica = DocumentBuilderFactory.newInstance();
        fabrica.setNamespaceAware(true);
        fabrica.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        fabrica.setFeature("http://xml.org/sax/features/external-general-entities", false);
        fabrica.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        fabrica.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        fabrica.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        return fabrica.newDocumentBuilder().parse(new ByteArrayInputStream(contenido));
    }

    static Document hoja(byte[] archivo) throws Exception {
        byte[] contenido = partes(archivo).get("xl/worksheets/sheet1.xml");
        if (contenido == null) {
            throw new SAXException("Falta la hoja de productos en el XLSX.");
        }
        return xml(contenido);
    }

    static NodeList filas(Document hoja) {
        return hoja.getElementsByTagNameNS(NS, "row");
    }

    static Element celda(Document hoja, String referencia) {
        NodeList celdas = hoja.getElementsByTagNameNS(NS, "c");
        for (int i = 0; i < celdas.getLength(); i++) {
            Element celda = (Element) celdas.item(i);
            if (referencia.equals(celda.getAttribute("r"))) {
                return celda;
            }
        }
        throw new AssertionError("Falta la celda " + referencia);
    }

    static String valor(Element celda) {
        String etiqueta = "inlineStr".equals(celda.getAttribute("t")) ? "t" : "v";
        NodeList contenidos = celda.getElementsByTagNameNS(NS, etiqueta);
        if (contenidos.getLength() != 1) {
            throw new AssertionError("La celda " + celda.getAttribute("r") + " no tiene un valor único.");
        }
        return contenidos.item(0).getTextContent();
    }
}
