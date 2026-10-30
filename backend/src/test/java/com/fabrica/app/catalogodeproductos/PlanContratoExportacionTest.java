package com.fabrica.app.catalogodeproductos;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/** Integra el plan documental con el contrato que guiará las pruebas de la API cuando exista. */
class PlanContratoExportacionTest {

    private String documento(String nombre) throws IOException {
        Path ruta = Path.of("docs/catalogo-de-productos", nombre);
        if (!Files.exists(ruta)) {
            ruta = Path.of("../docs/catalogo-de-productos", nombre);
        }
        return Files.readString(ruta);
    }

    @Test
    void el_plan_conserva_el_contrato_de_exportacion_del_spec() throws IOException {
        String spec = documento("SPEC.md");
        String plan = documento("PLAN.md");
        assertThat(spec).contains("/api/productos/exportaciones/excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(plan).contains("GET /api/productos/exportaciones/excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "attachment; filename=\"catalogo-productos.xlsx\"");
        for (String filtro : new String[] {"codigo", "nombre_comercial", "categoria", "subcategoria", "estado"}) {
            assertThat(spec).contains("name: " + filtro);
            assertThat(plan).contains("`" + filtro + "`");
        }
        assertThat(plan).contains("64, 255, 120, 120 y 20", "400 y `ProblemDetail`",
                "401", "403");
    }

    @Test
    void el_plan_preve_las_verificaciones_de_los_dos_criterios_gherkin() throws IOException {
        String spec = documento("SPEC.md");
        String plan = documento("PLAN.md");
        assertThat(spec).contains("HU-123/C1", "HU-123/C2");
        assertThat(plan).contains("id`, `codigo`, `nombre_comercial`, `descripcion`, `categoria`, `subcategoria`, `precio_base`, `moneda`, `stock`, `imagen`, `estado`")
                .contains("si no hay resultados, generar un libro válido con solo cabeceras")
                .contains("sin filtros, con filtros y sin resultados")
                .contains("precio y stock registrados")
                .contains("valor exacto del precio y del stock")
                .contains("cadenas literales, nunca como fórmulas");
    }
}
