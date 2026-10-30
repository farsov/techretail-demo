package com.fabrica.app.catalogodeproductos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
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
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.hibernate.query.Query;
import org.junit.jupiter.api.Test;

class ExportacionProductosRepositoryTest {

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void consume_las_filas_incrementalmente_y_cierra_el_cursor_sin_modificar_los_datos() {
        EntityManager gestor = mock(EntityManager.class);
        CriteriaBuilder constructor = mock(CriteriaBuilder.class);
        CriteriaQuery<Tuple> consulta = mock(CriteriaQuery.class);
        Root<Producto> producto = mock(Root.class);
        Path<Object> columna = mock(Path.class);
        Predicate acceso = mock(Predicate.class);
        Order orden = mock(Order.class);
        TypedQuery<Tuple> consultaEjecutable = mock(TypedQuery.class);
        Query hibernate = mock(Query.class);
        Tuple primera = mock(Tuple.class);
        Tuple segunda = mock(Tuple.class);
        AtomicBoolean cerrado = new AtomicBoolean();
        when(gestor.getCriteriaBuilder()).thenReturn(constructor);
        when(constructor.createTupleQuery()).thenReturn(consulta);
        when(consulta.from(Producto.class)).thenReturn(producto);
        when(producto.get(anyString())).thenReturn(columna);
        when(constructor.asc(columna)).thenReturn(orden);
        when(consulta.where(acceso)).thenReturn(consulta);
        when(gestor.createQuery(consulta)).thenReturn(consultaEjecutable);
        when(consultaEjecutable.unwrap(Query.class)).thenReturn(hibernate);
        when(consultaEjecutable.getResultStream()).thenReturn(Stream.of(primera, segunda).onClose(() -> cerrado.set(true)));
        prepararFila(primera, 1L, "A-1", new BigDecimal("123.4567"), 10000000001L);
        prepararFila(segunda, 2L, "A-2", new BigDecimal("0.0100"), 0L);

        var filas = new java.util.ArrayList<ExportacionProductosRepository.FilaProducto>();
        new ExportacionProductosRepository(gestor).recorrer(new Usuario("ana", List.of()),
                new FiltrosExportacion(null, null, null, null, null), (raiz, cb, usuario) -> acceso,
                filas::add);

        assertThat(filas).hasSize(2);
        assertThat(filas.get(0)).isEqualTo(new ExportacionProductosRepository.FilaProducto(
                1L, "A-1", "Nombre A-1", "Descripción A-1", "Categoría", "Subcategoría",
                new BigDecimal("123.4567"), "EUR", 10000000001L, "imagen.png", "ACTIVO"));
        assertThat(filas.get(1).precioBase()).isEqualTo(new BigDecimal("0.0100"));
        assertThat(filas.get(1).stock()).isEqualTo(0L);
        assertThat(cerrado).isTrue();
        verify(hibernate).setFetchSize(256);
        verify(consultaEjecutable, never()).getResultList();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void cierra_el_cursor_incluso_si_el_consumidor_falla() {
        EntityManager gestor = mock(EntityManager.class);
        CriteriaBuilder constructor = mock(CriteriaBuilder.class);
        CriteriaQuery<Tuple> consulta = mock(CriteriaQuery.class);
        Root<Producto> producto = mock(Root.class);
        Path<Object> columna = mock(Path.class);
        Predicate acceso = mock(Predicate.class);
        TypedQuery<Tuple> consultaEjecutable = mock(TypedQuery.class);
        Tuple tupla = mock(Tuple.class);
        AtomicBoolean cerrado = new AtomicBoolean();
        when(gestor.getCriteriaBuilder()).thenReturn(constructor);
        when(constructor.createTupleQuery()).thenReturn(consulta);
        when(consulta.from(Producto.class)).thenReturn(producto);
        when(producto.get(anyString())).thenReturn(columna);
        when(consulta.where(acceso)).thenReturn(consulta);
        when(gestor.createQuery(consulta)).thenReturn(consultaEjecutable);
        when(consultaEjecutable.unwrap(Query.class)).thenReturn(mock(Query.class));
        when(consultaEjecutable.getResultStream()).thenReturn(Stream.of(tupla).onClose(() -> cerrado.set(true)));
        prepararFila(tupla, 1L, "A-1", new BigDecimal("1.00"), 2L);

        assertThatThrownBy(() -> new ExportacionProductosRepository(gestor).recorrer(
                new Usuario("ana", List.of()), new FiltrosExportacion(null, null, null, null, null),
                (raiz, cb, usuario) -> acceso, fila -> { throw new IllegalStateException("fallo de escritura"); }))
                .isInstanceOf(IllegalStateException.class).hasMessage("fallo de escritura");
        assertThat(cerrado).isTrue();
    }

    private void prepararFila(Tuple tupla, Long id, String codigo, BigDecimal precio, Long stock) {
        when(tupla.get(0, Long.class)).thenReturn(id);
        when(tupla.get(1, String.class)).thenReturn(codigo);
        when(tupla.get(2, String.class)).thenReturn("Nombre " + codigo);
        when(tupla.get(3, String.class)).thenReturn("Descripción " + codigo);
        when(tupla.get(4, String.class)).thenReturn("Categoría");
        when(tupla.get(5, String.class)).thenReturn("Subcategoría");
        when(tupla.get(6, BigDecimal.class)).thenReturn(precio);
        when(tupla.get(7, String.class)).thenReturn("EUR");
        when(tupla.get(8, Long.class)).thenReturn(stock);
        when(tupla.get(9, String.class)).thenReturn("imagen.png");
        when(tupla.get(10, String.class)).thenReturn("ACTIVO");
    }
}
