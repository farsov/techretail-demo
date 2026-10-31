package com.fabrica.app.catalogodeproductos;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/** Pruebas unitarias de las decisiones documentales del modelo y las capas de exportación. */
class PlanModeloLecturaTest {

    private String plan() throws IOException {
        Path ruta = Path.of("docs/catalogo-de-productos/hu-124/plan.md");
        if (!Files.exists(ruta)) {
            ruta = Path.of("../docs/catalogo-de-productos/hu-124/plan.md");
        }
        return Files.readString(ruta);
    }

    @Test
    void documenta_el_modelo_la_fuente_y_las_reglas_de_acceso_existentes() throws IOException {
        assertThat(plan()).contains("entity.Producto", "`productos`", "ExportacionProductosRepository",
                "`codigo`, `nombre_comercial`, `categoria`, `subcategoria`, `precio_base`, `moneda`, `stock`, `estado`",
                "No leer ni exportar `historial_cambios_producto`", "`id` sirve únicamente para ordenar",
                "`Usuario.ATRIBUTO`", "`GESTOR_PRODUCTOS`", "permiso de exportación en **cada** solicitud",
                "predicado de visibilidad del usuario con todos los filtros, incluido `estado`",
                "sin filtros se usa igualmente el predicado de visibilidad",
                "sin inventar nuevos roles, estados o permisos");
    }

    @Test
    void documenta_lectura_coherente_acotada_y_valores_numericos_sin_conversion() throws IOException {
        assertThat(plan()).contains("proyección/DTO de exportación inmutable",
                "misma lectura por fila", "evitar mezclar versiones anteriores y posteriores",
                "lotes o mediante cursor/stream de JPA", "liberar el cursor al terminar",
                "`numeric(15,2)`", "`BigDecimal`, sin redondeos adicionales",
                "`integer`, sin conversiones de cantidad", "`stock` como `Long`");
    }

    @Test
    void separa_controller_servicio_repositorio_escritor_y_frontend_sin_alterar_excel() throws IOException {
        assertThat(plan()).contains("**Controller y DTO de filtros:**", "**Service:**",
                "**Repository / entity / DTO:**", "**Escritor CSV y Angular:**",
                "No modificar el comportamiento de la exportación Excel",
                "No exponer entidades por la API", "Angular recibe un `Blob` y lo descarga sin recalcular");
    }
}
