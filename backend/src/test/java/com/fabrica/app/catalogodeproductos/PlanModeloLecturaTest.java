package com.fabrica.app.catalogodeproductos;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/** Comprueba las decisiones de diseño de la tarea de planificación, no la API todavía no implementada. */
class PlanModeloLecturaTest {

    private String plan() throws IOException {
        Path ruta = Path.of("docs/catalogo-de-productos/PLAN.md");
        if (!Files.exists(ruta)) {
            ruta = Path.of("../docs/catalogo-de-productos/PLAN.md");
        }
        return Files.readString(ruta);
    }

    @Test
    void documenta_la_fuente_y_los_permisos_pendientes_sin_inventar_una_politica() throws IOException {
        assertThat(plan())
                .contains("No existe todavía en los archivos disponibles un modelo `productos`")
                .contains("no se presupone el nombre de ningún rol ni el criterio de visibilidad")
                .contains("identificar la fuente de datos y la política de acceso vigentes")
                .contains("Usuario.ATRIBUTO")
                .contains("permiso de exportación en **cada** solicitud")
                .contains("predicado de visibilidad del usuario con todos los filtros, incluido `estado`");
    }

    @Test
    void documenta_lectura_coherente_y_acotada_sin_alterar_precio_ni_stock() throws IOException {
        assertThat(plan())
                .contains("proyección/DTO de exportación inmutable")
                .contains("sin mezclar versiones anteriores y posteriores")
                .contains("`precio_base` con su precisión almacenada")
                .contains("`stock` con su valor registrado")
                .contains("No leer ni exportar `historial_cambios_producto`")
                .contains("lotes o mediante cursor/stream de JPA")
                .contains("sin filtros se usa igualmente el predicado de visibilidad");
    }
}
