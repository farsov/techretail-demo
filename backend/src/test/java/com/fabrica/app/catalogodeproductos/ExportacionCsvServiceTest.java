package com.fabrica.app.catalogodeproductos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

import com.fabrica.app.catalogodeproductos.ExportacionProductosRepository.Visibilidad;
import com.fabrica.app.catalogodeproductos.entity.Producto;
import com.fabrica.app.infra.auth.Usuario;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;

class ExportacionCsvServiceTest {

    private final ProductoExportacionRepository repositorio = mock(ProductoExportacionRepository.class);
    private final ExportacionCsvService servicio = new ExportacionCsvService(repositorio);
    private final Usuario gestor = new Usuario("gestora", List.of("GESTOR_PRODUCTOS"));

    @Test
    void acepta_la_consulta_sin_filtros_y_los_limites_de_categoria_y_subcategoria() {
        ExportacionCsvService.Filtros sinFiltros = servicio.validarYAutorizar(gestor, null, null, null);
        assertThat(sinFiltros.categoria()).isNull();
        assertThat(sinFiltros.subcategoria()).isNull();
        assertThat(sinFiltros.estado()).isNull();
        ExportacionCsvService.Filtros limites = servicio.validarYAutorizar(gestor,
                "A".repeat(120), "S".repeat(120), "ACTIVO");
        assertThat(limites.categoria()).hasSize(120);
        assertThat(limites.subcategoria()).hasSize(120);
        assertThat(limites.estado()).isEqualTo("ACTIVO");
        verifyNoInteractions(repositorio);
    }

    @Test
    void rechaza_filtros_vacios_excesivos_y_estados_no_permitidos_antes_de_leer_datos() {
        comprobarFiltroInvalido("categoria", "", null, null);
        comprobarFiltroInvalido("categoria", "A".repeat(121), null, null);
        comprobarFiltroInvalido("subcategoria", null, "  ", null);
        comprobarFiltroInvalido("subcategoria", null, "S".repeat(121), null);
        comprobarFiltroInvalido("estado", null, null, "");
        comprobarFiltroInvalido("estado", null, null, "X".repeat(21));
        comprobarFiltroInvalido("estado", null, null, "DESCONOCIDO");
        verifyNoInteractions(repositorio);
    }

    @Test
    void deniega_usuarios_no_autenticados_o_sin_permiso_incluso_si_solicitan_filtros() {
        Usuario lector = new Usuario("lector", List.of("LECTOR"));
        for (Usuario usuario : new Usuario[] {null, lector}) {
            assertThatThrownBy(() -> servicio.validarYAutorizar(usuario, "Cocina", null, "ACTIVO"))
                    .isInstanceOf(ExportacionProductosService.PermisoExportacionDenegado.class)
                    .hasMessageContaining("permiso");
            assertThatThrownBy(() -> servicio.escribirCsv(usuario,
                    new ExportacionCsvService.Filtros(null, null, null), new ByteArrayOutputStream()))
                    .isInstanceOf(ExportacionProductosService.PermisoExportacionDenegado.class);
        }
        verifyNoInteractions(repositorio);
    }

    @Test
    void requiere_filtros_validados_antes_de_abrir_la_consulta() {
        assertThatThrownBy(() -> servicio.escribirCsv(gestor, null, new ByteArrayOutputStream()))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repositorio);
    }

    @Test
    void entrega_filtros_y_politica_de_visibilidad_a_la_consulta_tras_autorizar() throws Exception {
        ExportacionCsvService.Filtros filtros = servicio.validarYAutorizar(gestor, "Cocina", null, "ACTIVO");
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        doAnswer(invocacion -> {
            Visibilidad visibilidad = invocacion.getArgument(4);
            @SuppressWarnings("unchecked")
            Root<Producto> raiz = mock(Root.class);
            CriteriaBuilder cb = mock(CriteriaBuilder.class);
            Predicate acceso = mock(Predicate.class);
            org.mockito.Mockito.when(cb.conjunction()).thenReturn(acceso);
            assertThat(visibilidad.predicado(raiz, cb, gestor)).isSameAs(acceso);
            assertThatThrownBy(() -> visibilidad.predicado(raiz, cb,
                    new Usuario("lector", List.of("LECTOR"))))
                    .isInstanceOf(ExportacionProductosService.PermisoExportacionDenegado.class);
            return null;
        }).when(repositorio).recorrer(any(), any(), any(), any(), any(), any());

        servicio.escribirCsv(gestor, filtros, salida);

        verify(repositorio).recorrer(same(gestor), org.mockito.ArgumentMatchers.eq("Cocina"),
                isNull(), org.mockito.ArgumentMatchers.eq("ACTIVO"), any(), any());
        assertThat(salida.toString(StandardCharsets.UTF_8)).isEqualTo(
                "codigo,nombre_comercial,categoria,subcategoria,precio_base,moneda,stock,estado\r\n");
    }

    @Test
    void conserva_precio_y_stock_y_escapa_textos_del_registro_sin_alterarlos() throws Exception {
        doAnswer(invocacion -> {
            @SuppressWarnings("unchecked")
            Consumer<ProductoExportacionFila> consumidor = invocacion.getArgument(5);
            consumidor.accept(new ProductoExportacionFila("P,1", "Café \"especial\"\nGrande",
                    "Cocina", "Tazas", new BigDecimal("9999999999999.99"), "EUR",
                    2147483647L, "ACTIVO"));
            return null;
        }).when(repositorio).recorrer(any(), any(), any(), any(), any(), any());
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        servicio.escribirCsv(gestor, servicio.validarYAutorizar(gestor, null, null, null), salida);
        assertThat(salida.toString(StandardCharsets.UTF_8)).isEqualTo(
                "codigo,nombre_comercial,categoria,subcategoria,precio_base,moneda,stock,estado\r\n"
                + "\"P,1\",\"Café \"\"especial\"\"\nGrande\",Cocina,Tazas,9999999999999.99,EUR,2147483647,ACTIVO\r\n");
    }

    private void comprobarFiltroInvalido(String campo, String categoria, String subcategoria, String estado) {
        assertThatThrownBy(() -> servicio.validarYAutorizar(gestor, categoria, subcategoria, estado))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(campo);
    }
}
