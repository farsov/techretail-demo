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

/** Consulta solo las columnas del CSV, intersectando siempre la visibilidad con los filtros. */
@Repository
public class ProductoExportacionRepository {

    private static final int TAMANO_LOTE = 256;

    private final EntityManager entityManager;

    public ProductoExportacionRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Entrega las filas mientras la transacción y el cursor están abiertos. La política de acceso
     * es obligatoria y debe haber sido autorizada por el servicio antes de comenzar la lectura.
     * Cada proyección procede de una única tupla de la misma consulta, incluso si el producto
     * cambia durante la exportación.
     */
    @Transactional(readOnly = true)
    public void recorrer(Usuario usuario, String categoria, String subcategoria, String estado,
            ExportacionProductosRepository.Visibilidad visibilidad,
            Consumer<ProductoExportacionFila> consumidor) {
        Objects.requireNonNull(usuario, "Se necesita un usuario autenticado.");
        Objects.requireNonNull(visibilidad, "Se necesita la política de consulta de productos.");
        Objects.requireNonNull(consumidor, "Se necesita el destino de las filas.");

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> consulta = cb.createTupleQuery();
        Root<Producto> producto = consulta.from(Producto.class);
        Predicate condiciones = Objects.requireNonNull(visibilidad.predicado(producto, cb, usuario),
                "La política de consulta no puede omitir el predicado de acceso.");
        if (categoria != null) {
            condiciones = cb.and(condiciones, cb.equal(producto.get("categoria"), categoria));
        }
        if (subcategoria != null) {
            condiciones = cb.and(condiciones, cb.equal(producto.get("subcategoria"), subcategoria));
        }
        if (estado != null) {
            condiciones = cb.and(condiciones, cb.equal(producto.get("estado"), estado));
        }
        consulta.multiselect(producto.get("codigo"), producto.get("nombreComercial"),
                producto.get("categoria"), producto.get("subcategoria"), producto.get("precioBase"),
                producto.get("moneda"), producto.get("stock"), producto.get("estado"));
        consulta.where(condiciones).orderBy(cb.asc(producto.get("id")));

        TypedQuery<Tuple> query = entityManager.createQuery(consulta);
        query.unwrap(Query.class).setFetchSize(TAMANO_LOTE);
        try (Stream<Tuple> filas = query.getResultStream()) {
            filas.forEach(fila -> consumidor.accept(new ProductoExportacionFila(
                    fila.get(0, String.class), fila.get(1, String.class), fila.get(2, String.class),
                    fila.get(3, String.class), fila.get(4, BigDecimal.class), fila.get(5, String.class),
                    fila.get(6, Long.class), fila.get(7, String.class))));
        }
    }
}
