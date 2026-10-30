package com.fabrica.app.catalogodeproductos;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.fabrica.app.catalogodeproductos.entity.Producto;
import com.fabrica.app.infra.auth.Usuario;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.hibernate.query.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Lee proyecciones completas de cada producto sin acumular el catálogo en memoria. */
@Repository
public class ExportacionProductosRepository {

    private static final int TAMANO_LOTE = 256;
    private final EntityManager entityManager;

    public ExportacionProductosRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /** La política de consulta debe venir del catálogo; nunca se deduce de los filtros ni del estado. */
    @FunctionalInterface
    public interface Visibilidad {
        Predicate predicado(Root<Producto> producto, CriteriaBuilder constructor, Usuario usuario);
    }

    /** Instantánea inmutable de una sola fila obtenida por una única consulta a la base de datos. */
    public record FilaProducto(Long id, String codigo, String nombreComercial, String descripcion,
            String categoria, String subcategoria, BigDecimal precioBase, String moneda,
            Long stock, String imagen, String estado) {
    }

    /** El consumidor se ejecuta dentro de la transacción; no debe retener el cursor al salir. */
    @Transactional(readOnly = true)
    public void recorrer(Usuario usuario, FiltrosExportacion filtros, Visibilidad visibilidad,
            Consumer<FilaProducto> consumidor) {
        Objects.requireNonNull(usuario, "Se necesita un usuario autenticado.");
        Objects.requireNonNull(filtros, "Se necesitan filtros de exportación.");
        Objects.requireNonNull(visibilidad, "Se necesita la política de consulta de productos.");
        Objects.requireNonNull(consumidor, "Se necesita el destino de las filas.");

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> consulta = cb.createTupleQuery();
        Root<Producto> producto = consulta.from(Producto.class);
        Predicate acceso = Objects.requireNonNull(visibilidad.predicado(producto, cb, usuario),
                "La política de consulta no puede omitir el predicado de acceso.");
        Predicate condiciones = acceso;
        if (filtros.codigo() != null) {
            condiciones = cb.and(condiciones, cb.equal(producto.get("codigo"), filtros.codigo()));
        }
        if (filtros.nombreComercial() != null) {
            condiciones = cb.and(condiciones, cb.equal(producto.get("nombreComercial"), filtros.nombreComercial()));
        }
        if (filtros.categoria() != null) {
            condiciones = cb.and(condiciones, cb.equal(producto.get("categoria"), filtros.categoria()));
        }
        if (filtros.subcategoria() != null) {
            condiciones = cb.and(condiciones, cb.equal(producto.get("subcategoria"), filtros.subcategoria()));
        }
        if (filtros.estado() != null) {
            condiciones = cb.and(condiciones, cb.equal(producto.get("estado"), filtros.estado()));
        }
        consulta.multiselect(producto.get("id"), producto.get("codigo"), producto.get("nombreComercial"),
                producto.get("descripcion"), producto.get("categoria"), producto.get("subcategoria"),
                producto.get("precioBase"), producto.get("moneda"), producto.get("stock"),
                producto.get("imagen"), producto.get("estado"));
        consulta.where(condiciones).orderBy(cb.asc(producto.get("id")));

        TypedQuery<Tuple> query = entityManager.createQuery(consulta);
        query.unwrap(Query.class).setFetchSize(TAMANO_LOTE);
        try (Stream<Tuple> filas = query.getResultStream()) {
            filas.forEach(fila -> consumidor.accept(new FilaProducto(
                    fila.get(0, Long.class), fila.get(1, String.class), fila.get(2, String.class),
                    fila.get(3, String.class), fila.get(4, String.class), fila.get(5, String.class),
                    fila.get(6, BigDecimal.class), fila.get(7, String.class), fila.get(8, Long.class),
                    fila.get(9, String.class), fila.get(10, String.class))));
        }
    }
}
