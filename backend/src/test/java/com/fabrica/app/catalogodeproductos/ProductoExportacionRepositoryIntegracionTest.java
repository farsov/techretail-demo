package com.fabrica.app.catalogodeproductos;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.fabrica.app.infra.auth.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Integra la proyección de HU-124/C1 y C2 con el catálogo persistido en H2. */
@SpringBootTest
@Transactional
class ProductoExportacionRepositoryIntegracionTest {

    @Autowired
    ProductoExportacionRepository repositorio;

    @Autowired
    JdbcTemplate jdbc;

    private final Usuario usuario = new Usuario("equipo-a", List.of("GESTOR_PRODUCTOS"));
    private final ExportacionProductosRepository.Visibilidad visibilidad = (raiz, cb, solicitante) ->
            cb.like(raiz.get("codigo"), solicitante.sub() + "-%");

    @BeforeEach
    void preparar_catalogo() {
        jdbc.update("delete from productos");
        registrar(1L, "equipo-a-1", "Café, especial", "Bebidas", "Molido", "ACTIVO",
                new BigDecimal("9999999999999.99"), 2147483647L);
        registrar(2L, "equipo-a-2", "Té", "Bebidas", "Hojas", "INACTIVO",
                new BigDecimal("0.01"), 0L);
        registrar(3L, "equipo-b-1", "Oculto", "Bebidas", "Molido", "ACTIVO",
                new BigDecimal("55.00"), 8L);
    }

    @Test
    void hu_124_c1_sin_filtros_recupera_todos_y_solo_los_productos_accesibles() {
        assertThat(leer(null, null, null)).containsExactly(
                new ProductoExportacionFila("equipo-a-1", "Café, especial", "Bebidas", "Molido",
                        new BigDecimal("9999999999999.99"), "EUR", 2147483647L, "ACTIVO"),
                new ProductoExportacionFila("equipo-a-2", "Té", "Bebidas", "Hojas",
                        new BigDecimal("0.01"), "EUR", 0L, "INACTIVO"));
    }

    @Test
    void hu_124_c2_precio_y_stock_conservan_los_valores_registrados_incluso_en_sus_limites() {
        for (ProductoExportacionFila fila : leer(null, null, null)) {
            BigDecimal precioRegistrado = jdbc.queryForObject(
                    "select precio_base from productos where codigo = ?", BigDecimal.class, fila.codigo());
            Long stockRegistrado = jdbc.queryForObject(
                    "select stock from productos where codigo = ?", Long.class, fila.codigo());
            assertThat(fila.precioBase()).isEqualByComparingTo(precioRegistrado);
            assertThat(fila.stock()).isEqualTo(stockRegistrado);
        }
        assertThat(leer("Bebidas", "Hojas", "INACTIVO")).singleElement()
                .extracting(ProductoExportacionFila::precioBase).isEqualTo(new BigDecimal("0.01"));
    }

    @Test
    void combina_cada_filtro_con_la_visibilidad_y_no_entrega_filas_si_no_hay_coincidencias() {
        assertCodigos("Bebidas", null, null, "equipo-a-1", "equipo-a-2");
        assertCodigos(null, "Molido", null, "equipo-a-1");
        assertCodigos(null, null, "INACTIVO", "equipo-a-2");
        assertCodigos("Bebidas", "Molido", "ACTIVO", "equipo-a-1");
        assertThat(leer("Bebidas", "Molido", "INACTIVO")).isEmpty();
        assertThat(leer(null, "Solo equipo b", null)).isEmpty();
    }

    @Test
    void la_fila_ya_proyectada_permanece_coherente_aunque_cambie_el_producto() {
        List<ProductoExportacionFila> filas = new ArrayList<>();
        repositorio.recorrer(usuario, null, null, null, visibilidad, fila -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            filas.add(fila);
            if (fila.codigo().equals("equipo-a-1")) {
                jdbc.update("update productos set nombre_comercial = ?, precio_base = ?, stock = ? where id = ?",
                        "Café nuevo", new BigDecimal("2.00"), 4L, 1L);
            }
        });
        assertThat(filas).hasSize(2);
        assertThat(filas.getFirst()).isEqualTo(new ProductoExportacionFila("equipo-a-1",
                "Café, especial", "Bebidas", "Molido", new BigDecimal("9999999999999.99"),
                "EUR", 2147483647L, "ACTIVO"));
        assertThat(jdbc.queryForObject("select nombre_comercial from productos where id = 1", String.class))
                .isEqualTo("Café nuevo");
    }

    private List<ProductoExportacionFila> leer(String categoria, String subcategoria, String estado) {
        List<ProductoExportacionFila> filas = new ArrayList<>();
        repositorio.recorrer(usuario, categoria, subcategoria, estado, visibilidad, fila -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            filas.add(fila);
        });
        return filas;
    }

    private void assertCodigos(String categoria, String subcategoria, String estado, String... esperados) {
        assertThat(leer(categoria, subcategoria, estado)).extracting(ProductoExportacionFila::codigo)
                .containsExactly(esperados);
    }

    private void registrar(Long id, String codigo, String nombre, String categoria, String subcategoria,
            String estado, BigDecimal precio, Long stock) {
        jdbc.update("insert into productos (id, codigo, nombre_comercial, descripcion, categoria, "
                        + "subcategoria, precio_base, moneda, stock, imagen, estado) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, codigo, nombre, "Descripción", categoria, subcategoria,
                precio, "EUR", stock, "imagen.png", estado);
    }
}
