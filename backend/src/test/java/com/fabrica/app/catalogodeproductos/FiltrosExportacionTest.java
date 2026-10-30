package com.fabrica.app.catalogodeproductos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class FiltrosExportacionTest {

    @Test
    void permite_la_ausencia_de_filtros_y_los_valores_en_su_longitud_maxima() {
        assertThat(new FiltrosExportacion(null, null, null, null, null))
                .extracting(FiltrosExportacion::codigo, FiltrosExportacion::nombreComercial,
                        FiltrosExportacion::categoria, FiltrosExportacion::subcategoria,
                        FiltrosExportacion::estado)
                .containsExactly(null, null, null, null, null);
        FiltrosExportacion filtros = new FiltrosExportacion("C".repeat(64), "N".repeat(255),
                "A".repeat(120), "S".repeat(120), "E".repeat(20));
        assertThat(filtros.codigo()).hasSize(64);
        assertThat(filtros.nombreComercial()).hasSize(255);
        assertThat(filtros.categoria()).hasSize(120);
        assertThat(filtros.subcategoria()).hasSize(120);
        assertThat(filtros.estado()).hasSize(20);
    }

    @Test
    void rechaza_cada_filtro_que_excede_la_longitud_del_contrato() {
        comprobarInvalido("codigo", "C".repeat(65), null, null, null, null);
        comprobarInvalido("nombre_comercial", null, "N".repeat(256), null, null, null);
        comprobarInvalido("categoria", null, null, "A".repeat(121), null, null);
        comprobarInvalido("subcategoria", null, null, null, "S".repeat(121), null);
        comprobarInvalido("estado", null, null, null, null, "E".repeat(21));
    }

    @Test
    void rechaza_cada_filtro_vacio_o_compuesto_solo_de_espacios() {
        comprobarInvalido("codigo", " ", null, null, null, null);
        comprobarInvalido("nombre_comercial", null, "", null, null, null);
        comprobarInvalido("categoria", null, null, "\t", null, null);
        comprobarInvalido("subcategoria", null, null, null, " ", null);
        comprobarInvalido("estado", null, null, null, null, "\n");
    }

    private void comprobarInvalido(String campo, String codigo, String nombreComercial,
            String categoria, String subcategoria, String estado) {
        assertThatThrownBy(() -> new FiltrosExportacion(codigo, nombreComercial, categoria, subcategoria, estado))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(campo);
    }
}
