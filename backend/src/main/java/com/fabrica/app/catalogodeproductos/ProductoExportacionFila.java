package com.fabrica.app.catalogodeproductos;

import java.math.BigDecimal;

/** Proyección inmutable de las ocho columnas exportables de un único producto. */
public record ProductoExportacionFila(
        String codigo,
        String nombreComercial,
        String categoria,
        String subcategoria,
        BigDecimal precioBase,
        String moneda,
        Long stock,
        String estado) {
}
