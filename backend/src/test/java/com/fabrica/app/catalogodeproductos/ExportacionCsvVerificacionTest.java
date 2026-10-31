package com.fabrica.app.catalogodeproductos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

import com.fabrica.app.infra.auth.Usuario;
import org.junit.jupiter.api.Test;

/** Comportamiento del servicio al consumir proyecciones coherentes y proteger la consulta. */
class ExportacionCsvVerificacionTest {
    private final ProductoExportacionRepository repositorio = mock(ProductoExportacionRepository.class);
    private final ExportacionCsvService servicio = new ExportacionCsvService(repositorio);
    private final Usuario gestor = new Usuario("gestora", List.of("GESTOR_PRODUCTOS"));

    @Test
    void sin_resultados_escribe_solo_la_cabecera_sin_exigir_filtros() throws Exception {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        servicio.escribirCsv(gestor, servicio.validarYAutorizar(gestor, null, null, null), salida);
        assertThat(salida.toString(StandardCharsets.UTF_8)).isEqualTo(
                "codigo,nombre_comercial,categoria,subcategoria,precio_base,moneda,stock,estado\r\n");
    }

    @Test
    void conserva_una_proyeccion_aunque_los_datos_cambien_durante_la_escritura() throws Exception {
        ProductoExportacionFila instantanea = new ProductoExportacionFila("P-1", "Café antiguo",
                "Bebidas", "Molido", new BigDecimal("9999999999999.99"), "EUR", 2147483647L, "ACTIVO");
        doAnswer(invocacion -> {
            @SuppressWarnings("unchecked")
            Consumer<ProductoExportacionFila> consumidor = invocacion.getArgument(5);
            consumidor.accept(instantanea);
            // La fila entregada al escritor no depende de una entidad JPA mutable.
            assertThat(instantanea.precioBase()).isEqualByComparingTo("9999999999999.99");
            return null;
        }).when(repositorio).recorrer(any(), any(), any(), any(), any(), any());
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        servicio.escribirCsv(gestor, servicio.validarYAutorizar(gestor, null, null, null), salida);
        assertThat(salida.toString(StandardCharsets.UTF_8)).isEqualTo(
                "codigo,nombre_comercial,categoria,subcategoria,precio_base,moneda,stock,estado\r\n"
                + "P-1,Café antiguo,Bebidas,Molido,9999999999999.99,EUR,2147483647,ACTIVO\r\n");
    }

    @Test
    void deniega_el_rol_lector_antes_de_validar_filtros_o_consultar_datos() {
        Usuario lector = new Usuario("lector", List.of("LECTOR"));
        assertThatThrownBy(() -> servicio.validarYAutorizar(lector, "", null, "BORRADO"))
                .isInstanceOf(ExportacionProductosService.PermisoExportacionDenegado.class);
        verifyNoInteractions(repositorio);
    }
}
