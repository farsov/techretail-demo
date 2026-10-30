package com.fabrica.app.catalogodeproductos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.fabrica.app.catalogodeproductos.ExportacionProductosRepository.FilaProducto;
import com.fabrica.app.infra.auth.Usuario;
import org.junit.jupiter.api.Test;

class ExportacionProductosServiceVerificacionTest {

    private final ExportacionProductosRepository repositorio = mock(ExportacionProductosRepository.class);
    private final ExcelProductosWriter escritor = mock(ExcelProductosWriter.class);
    private final ExportacionProductosService servicio = new ExportacionProductosService(repositorio, escritor);

    @Test
    void gestor_envia_al_repositorio_los_filtros_originales_y_la_identidad_autorizada() throws Exception {
        Usuario gestor = new Usuario("gestora", List.of("GESTOR_PRODUCTOS"));
        FiltrosExportacion filtros = new FiltrosExportacion("CAFE-901", null, "Cocina", null, "ACTIVO");
        ByteArrayOutputStream destino = new ByteArrayOutputStream();
        AtomicReference<FilaProducto> recibida = new AtomicReference<>();
        FilaProducto producto = new FilaProducto(901L, "CAFE-901", "Cafetera", "Descripción",
                "Cocina", "Café", new BigDecimal("123.45"), "EUR", 7L, "foto.png", "ACTIVO");
        doAnswer(invocacion -> {
            @SuppressWarnings("unchecked")
            Consumer<Consumer<FilaProducto>> proveedor = invocacion.getArgument(1);
            proveedor.accept(recibida::set);
            return null;
        }).when(escritor).escribir(same(destino), any());
        doAnswer(invocacion -> {
            @SuppressWarnings("unchecked")
            Consumer<FilaProducto> consumidor = invocacion.getArgument(3);
            consumidor.accept(producto);
            return null;
        }).when(repositorio).recorrer(same(gestor), same(filtros), any(), any());

        servicio.escribirExcel(gestor, filtros, destino);

        verify(repositorio).recorrer(same(gestor), same(filtros), any(), any());
        assertThat(recibida.get()).isSameAs(producto);
    }

    @Test
    void solicitante_sin_permiso_no_consulta_ni_escribe_aunque_filtre_por_estado() throws Exception {
        Usuario lector = new Usuario("lector", List.of("LECTOR"));
        FiltrosExportacion filtros = new FiltrosExportacion(null, null, null, null, "ACTIVO");

        assertThatThrownBy(() -> servicio.escribirExcel(lector, filtros, new ByteArrayOutputStream()))
                .isInstanceOf(ExportacionProductosService.PermisoExportacionDenegado.class)
                .hasMessageContaining("permiso");
        verify(repositorio, never()).recorrer(any(), any(), any(), any());
        verify(escritor, never()).escribir(any(), any());
    }
}
