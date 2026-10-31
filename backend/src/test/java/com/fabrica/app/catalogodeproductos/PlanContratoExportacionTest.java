package com.fabrica.app.catalogodeproductos;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/** Contrasta el plan de HU-124 con su contrato, sin confundirlo con HU-123. */
class PlanContratoExportacionTest {

    private String documento(String nombre) throws IOException {
        Path ruta = Path.of("docs/catalogo-de-productos/hu-124", nombre);
        if (!Files.exists(ruta)) {
            ruta = Path.of("../docs/catalogo-de-productos/hu-124", nombre);
        }
        return Files.readString(ruta);
    }

    @Test
    void el_plan_respeta_el_contrato_csv_y_sus_errores() throws IOException {
        String spec = documento("SPEC.md");
        String plan = documento("plan.md");

        assertThat(spec).contains("/api/productos/exportaciones/csv", "text/csv", "HU-124/C1", "HU-124/C2");
        assertThat(plan).contains("GET /api/productos/exportaciones/csv", "`categoria` (máximo 120)",
                "`subcategoria` (máximo 120)", "`estado` (máximo 20)",
                "`text/csv`", "UTF-8", "attachment; filename=\"productos.csv\"",
                "400, 401 y 403", "application/problem+json", "ProblemDetail",
                "Sin coincidencias se devuelve 200 con solo la cabecera");
        assertThat(plan).contains("No se añaden altas, modificaciones, consultas independientes ni exportación Excel");
    }

    @Test
    void el_plan_preve_verificar_los_dos_criterios_y_los_casos_limite() throws IOException {
        String plan = documento("plan.md");

        assertThat(plan).contains("HU-124/C1 (todos los productos accesibles sin filtro",
                "coincidencias filtradas y archivo vacío con cabecera",
                "HU-124/C2 (precio y stock exactos, fila coherente ante cambios)",
                "verificar contenido real, UTF-8, MIME y disposición",
                "400 para filtros vacíos, demasiado largos y estado inválido",
                "403 para filtro no autorizado y usuario sin permiso", "401 sin token",
                "nunca entregar el archivo en estos errores");
        assertThat(plan).contains("codigo,nombre_comercial,categoria,subcategoria,precio_base,moneda,stock,estado",
                "duplicar las comillas internas", "Sin filas escribir solo la cabecera");
    }
}
