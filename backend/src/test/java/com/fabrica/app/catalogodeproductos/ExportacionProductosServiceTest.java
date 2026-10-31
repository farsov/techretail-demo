package com.fabrica.app.catalogodeproductos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.function.Consumer;

import com.fabrica.app.catalogodeproductos.ExportacionProductosRepository.FilaProducto;
import com.fabrica.app.catalogodeproductos.ExportacionProductosRepository.Visibilidad;
import com.fabrica.app.catalogodeproductos.entity.Producto;
import com.fabrica.app.infra.auth.Usuario;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;

class ExportacionProductosServiceTest {

    private final ExportacionProductosRepository repositorio = mock(ExportacionProductosRepository.class);
    private final ExcelProductosWriter escritor = mock(ExcelProductosWriter.class);
    private final ExportacionProductosService servicio = new ExportacionProductosService(repositorio, escritor);
    private final FiltrosExportacion filtros = new FiltrosExportacion(null, null, null, null, null);

    @Test
    void deniega_exportacion_sin_rol_y_no_invoca_ni_lectura_ni_escritura() throws Exception {
        Usuario sinPermiso = new Usuario("ana", List.of("LECTOR"));
        assertThatThrownBy(() -> servicio.comprobarPermiso(sinPermiso))
                .isInstanceOf(ExportacionProductosService.PermisoExportacionDenegado.class)
                .hasMessageContaining("permiso");
        assertThatThrownBy(() -> servicio.escribirExcel(sinPermiso, filtros, new ByteArrayOutputStream()))
                .isInstanceOf(ExportacionProductosService.PermisoExportacionDenegado.class);
        assertThatThrownBy(() -> servicio.escribirExcel(null, filtros, new ByteArrayOutputStream()))
                .isInstanceOf(ExportacionProductosService.PermisoExportacionDenegado.class);
        verify(escritor, never()).escribir(any(), any());
        verify(repositorio, never()).recorrer(any(), any(), any(), any());
    }

    @Test
    void entrega_al_escritor_el_cursor_con_filtros_y_visibilidad_del_usuario_autorizado() throws Exception {
        Usuario gestor = new Usuario("gestora", List.of("GESTOR_PRODUCTOS"));
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        doAnswer(invocacion -> {
            @SuppressWarnings("unchecked")
            Consumer<Consumer<FilaProducto>> proveedor = invocacion.getArgument(1);
            proveedor.accept(fila -> { });
            return null;
        }).when(escritor).escribir(same(salida), any());

        servicio.escribirExcel(gestor, filtros, salida);

        verify(escritor).escribir(same(salida), any());
        verify(repositorio).recorrer(same(gestor), same(filtros), any(), any());
    }

    @Test
    void la_politica_de_visibilidad_no_autoriza_a_un_solicitante_distinto_sin_permiso() throws Exception {
        Usuario gestor = new Usuario("gestora", List.of("GESTOR_PRODUCTOS"));
        Usuario lector = new Usuario("lector", List.of("LECTOR"));
        @SuppressWarnings("unchecked")
        Root<Producto> raiz = mock(Root.class);
        CriteriaBuilder constructor = mock(CriteriaBuilder.class);
        Predicate verdadero = mock(Predicate.class);
        when(constructor.conjunction()).thenReturn(verdadero);
        doAnswer(invocacion -> {
            @SuppressWarnings("unchecked")
            Consumer<Consumer<FilaProducto>> proveedor = invocacion.getArgument(1);
            proveedor.accept(fila -> { });
            return null;
        }).when(escritor).escribir(any(), any());
        doAnswer(invocacion -> {
            Visibilidad politica = invocacion.getArgument(2);
            assertThat(politica.predicado(raiz, constructor, gestor)).isSameAs(verdadero);
            assertThatThrownBy(() -> politica.predicado(raiz, constructor, lector))
                    .isInstanceOf(ExportacionProductosService.PermisoExportacionDenegado.class);
            return null;
        }).when(repositorio).recorrer(any(), any(), any(), any());

        servicio.escribirExcel(gestor, filtros, new ByteArrayOutputStream());
        verify(repositorio).recorrer(same(gestor), same(filtros), any(), any());
    }
}
