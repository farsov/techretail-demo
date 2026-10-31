package com.fabrica.app.catalogodeproductos;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Consumer;

/** Escribe las ocho columnas del catálogo en CSV UTF-8 sin cerrar el flujo del solicitante. */
public class ProductoCsvWriter {

    private static final String CABECERA =
            "codigo,nombre_comercial,categoria,subcategoria,precio_base,moneda,stock,estado\r\n";

    /** Mantiene abierto el proveedor (y su cursor) hasta consumir la última fila. */
    public void escribir(OutputStream destino, Consumer<Consumer<ProductoExportacionFila>> proveedor)
            throws IOException {
        Objects.requireNonNull(destino, "Se necesita un destino para el archivo CSV.");
        Objects.requireNonNull(proveedor, "Se necesita un proveedor de productos.");
        BufferedWriter csv = new BufferedWriter(new OutputStreamWriter(destino, StandardCharsets.UTF_8));
        csv.write(CABECERA);
        try {
            proveedor.accept(fila -> {
                try {
                    escribirFila(csv, Objects.requireNonNull(fila, "La fila no puede ser nula."));
                } catch (IOException error) {
                    throw new UncheckedIOException(error);
                }
            });
            csv.flush();
        } catch (UncheckedIOException error) {
            throw error.getCause();
        }
    }

    private static void escribirFila(BufferedWriter csv, ProductoExportacionFila fila) throws IOException {
        csv.write(escapar(fila.codigo()));
        csv.write(',');
        csv.write(escapar(fila.nombreComercial()));
        csv.write(',');
        csv.write(escapar(fila.categoria()));
        csv.write(',');
        csv.write(escapar(fila.subcategoria()));
        csv.write(',');
        csv.write(fila.precioBase() == null ? "" : fila.precioBase().toPlainString());
        csv.write(',');
        csv.write(escapar(fila.moneda()));
        csv.write(',');
        csv.write(fila.stock() == null ? "" : fila.stock().toString());
        csv.write(',');
        csv.write(escapar(fila.estado()));
        csv.write("\r\n");
    }

    private static String escapar(String valor) {
        if (valor == null) {
            return "";
        }
        if (valor.indexOf(',') >= 0 || valor.indexOf('"') >= 0 || valor.indexOf('\r') >= 0
                || valor.indexOf('\n') >= 0) {
            return '"' + valor.replace("\"", "\"\"") + '"';
        }
        return valor;
    }
}
