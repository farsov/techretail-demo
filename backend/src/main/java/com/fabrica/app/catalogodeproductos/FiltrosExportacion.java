package com.fabrica.app.catalogodeproductos;

import jakarta.validation.constraints.Size;

/** Filtros opcionales de la exportación; null significa que no se aplica el filtro. */
public record FiltrosExportacion(
        @Size(max = 64) String codigo,
        @Size(max = 255) String nombreComercial,
        @Size(max = 120) String categoria,
        @Size(max = 120) String subcategoria,
        @Size(max = 20) String estado) {

    public FiltrosExportacion {
        validar("codigo", codigo, 64);
        validar("nombre_comercial", nombreComercial, 255);
        validar("categoria", categoria, 120);
        validar("subcategoria", subcategoria, 120);
        validar("estado", estado, 20);
    }

    private static void validar(String nombre, String valor, int longitudMaxima) {
        if (valor != null && (valor.isBlank() || valor.length() > longitudMaxima)) {
            throw new IllegalArgumentException("El filtro " + nombre + " no tiene un valor válido.");
        }
    }
}
