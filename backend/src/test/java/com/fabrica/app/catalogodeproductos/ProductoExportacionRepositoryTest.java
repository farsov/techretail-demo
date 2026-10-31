package com.fabrica.app.catalogodeproductos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import com.fabrica.app.catalogodeproductos.entity.Producto;
import com.fabrica.app.infra.auth.Usuario;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.hibernate.query.Query;
import org.junit.jupiter.api.Test;

/** Prueba el contrato de lectura incremental sin depender del escritor CSV. */
class ProductoExportacionRepositoryTest {

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void proyecta_ocho_columnas_de_cada_tupla_sin_redondear_y_cierra_el_cursor() {
        EntityManager gestor = mock(EntityManager.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        CriteriaQuery<Tuple> criterios = mock(CriteriaQuery.class);
        Root<Producto> producto = mock(Root.class);
        Path<Object> columna = mock(Path.class);
        Predicate acceso = mock(Predicate.class);
        TypedQuery<Tuple> consulta = mock(TypedQuery.class);
        Query hibernate = mock(Query.class);
        Tuple tupla = mock(Tuple.class);
        AtomicBoolean cerrado = new AtomicBoolean();
        when(gestor.getCriteriaBuilder()).thenReturn(cb);
        when(cb.createTupleQuery()).thenReturn(criterios);
        when(criterios.from(Producto.class)).thenReturn(producto);
        when(producto.get(anyString())).thenReturn(columna);
        when(criterios.where(acceso)).thenReturn(criterios);
        when(gestor.createQuery(criterios)).thenReturn(consulta);
        when(consulta.unwrap(Query.class)).thenReturn(hibernate);
        when(consulta.getResultStream()).thenReturn(Stream.of(tupla).onClose(() -> cerrado.set(true)));
        when(tupla.get(0, String.class)).thenReturn("P-1");
        when(tupla.get(1, String.class)).thenReturn("Café, especial");
        when(tupla.get(2, String.class)).thenReturn("Bebidas");
        when(tupla.get(3, String.class)).thenReturn("Molido");
        when(tupla.get(4, BigDecimal.class)).thenReturn(new BigDecimal("9999999999999.99"));
        when(tupla.get(5, String.class)).thenReturn("EUR");
        when(tupla.get(6, Long.class)).thenReturn(2147483647L);
        when(tupla.get(7, String.class)).thenReturn("ACTIVO");
        List<ProductoExportacionFila> filas = new ArrayList<>();

        new ProductoExportacionRepository(gestor).recorrer(new Usuario("gestora", List.of()),
                null, null, null, (raiz, constructor, usuario) -> acceso, filas::add);

        assertThat(filas).containsExactly(new ProductoExportacionFila("P-1", "Café, especial",
                "Bebidas", "Molido", new BigDecimal("9999999999999.99"), "EUR",
                2147483647L, "ACTIVO"));
        assertThat(cerrado).isTrue();
        verify(hibernate).setFetchSize(256);
        verify(consulta, never()).getResultList();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void cierra_el_cursor_si_falla_el_consumidor_de_las_filas() {
        EntityManager gestor = mock(EntityManager.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        CriteriaQuery<Tuple> criterios = mock(CriteriaQuery.class);
        Root<Producto> producto = mock(Root.class);
        Predicate acceso = mock(Predicate.class);
        TypedQuery<Tuple> consulta = mock(TypedQuery.class);
        Tuple tupla = mock(Tuple.class);
        AtomicBoolean cerrado = new AtomicBoolean();
        when(gestor.getCriteriaBuilder()).thenReturn(cb);
        when(cb.createTupleQuery()).thenReturn(criterios);
        when(criterios.from(Producto.class)).thenReturn(producto);
        when(criterios.where(acceso)).thenReturn(criterios);
        when(gestor.createQuery(criterios)).thenReturn(consulta);
        when(consulta.unwrap(Query.class)).thenReturn(mock(Query.class));
        when(consulta.getResultStream()).thenReturn(Stream.of(tupla).onClose(() -> cerrado.set(true)));

        assertThatThrownBy(() -> new ProductoExportacionRepository(gestor).recorrer(
                new Usuario("gestora", List.of()), null, null, null,
                (raiz, constructor, usuario) -> acceso,
                fila -> { throw new IllegalStateException("No se pudo escribir la fila"); }))
                .isInstanceOf(IllegalStateException.class).hasMessage("No se pudo escribir la fila");
        assertThat(cerrado).isTrue();
    }

    @Test
    void rechaza_la_ausencia_de_usuario_o_politica_antes_de_consultar_la_base() {
        EntityManager gestor = mock(EntityManager.class);
        ProductoExportacionRepository repositorio = new ProductoExportacionRepository(gestor);
        Usuario usuario = new Usuario("gestora", List.of());
        assertThatThrownBy(() -> repositorio.recorrer(null, null, null, null,
                (raiz, cb, solicitante) -> cb.conjunction(), fila -> { }))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("usuario");
        assertThatThrownBy(() -> repositorio.recorrer(usuario, null, null, null,
                null, fila -> { }))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("política");
        verify(gestor, never()).getCriteriaBuilder();
    }
}
