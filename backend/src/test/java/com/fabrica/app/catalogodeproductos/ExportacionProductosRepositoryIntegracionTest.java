package com.fabrica.app.catalogodeproductos;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.fabrica.app.infra.auth.Usuario;
import jakarta.persistence.criteria.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Integra la consulta JPA con productos realmente persistidos, sin presuponer una política de negocio aún no definida. */
@SpringBootTest
@Transactional
class ExportacionProductosRepositoryIntegracionTest {

    @Autowired
    ExportacionProductosRepository repositorio;

    @Autowired
    JdbcTemplate jdbc;

    private final Usuario usuario = new Usuario("equipo-a", List.of());

    // Política de prueba explícita: demuestra que el predicado recibido siempre restringe la consulta.
    private final ExportacionProductosRepository.Visibilidad visibilidad = (raiz, cb, solicitante) ->
            cb.like(raiz.get("codigo"), solicitante.sub() + "-%");

    @BeforeEach
    void registrar_productos() {
        jdbc.update("delete from productos");
        registrar(101L, "equipo-a-1", "Uno", "Herramientas", "Manual", "ACTIVO",
                new BigDecimal("123.45"), 12345678901L);
        registrar(102L, "equipo-a-2", "Dos", "Ropa", "Invierno", "INACTIVO",
                new BigDecimal("0.01"), 0L);
        registrar(103L, "equipo-b-1", "Reservado", "Herramientas", "Manual", "ACTIVO",
                new BigDecimal("999.99"), 99L);
    }

    @Test
    void sin_filtros_lee_todos_y_solo_los_productos_visibles_en_orden_estable() {
        List<ExportacionProductosRepository.FilaProducto> filas = leer(
                new FiltrosExportacion(null, null, null, null, null));
        assertThat(filas).extracting(ExportacionProductosRepository.FilaProducto::id)
                .containsExactly(101L, 102L);
        assertThat(filas.get(0)).isEqualTo(new ExportacionProductosRepository.FilaProducto(
                101L, "equipo-a-1", "Uno", "Descripción de Uno", "Herramientas", "Manual",
                new BigDecimal("123.45"), "EUR", 12345678901L, "uno.png", "ACTIVO"));
        assertThat(filas.get(1).precioBase()).isEqualByComparingTo("0.01");
        assertThat(filas.get(1).stock()).isZero();
    }

    @Test
    void cada_filtro_limita_la_lectura_sin_perder_la_restriccion_de_acceso() {
        assertCodigos(new FiltrosExportacion("equipo-a-1", null, null, null, null), "equipo-a-1");
        assertCodigos(new FiltrosExportacion(null, "Uno", null, null, null), "equipo-a-1");
        assertCodigos(new FiltrosExportacion(null, null, "Ropa", null, null), "equipo-a-2");
        assertCodigos(new FiltrosExportacion(null, null, null, "Invierno", null), "equipo-a-2");
        assertCodigos(new FiltrosExportacion(null, null, null, null, "ACTIVO"), "equipo-a-1");
        // La fila de equipo-b coincide con todos estos filtros, pero no con la política de visibilidad.
        assertCodigos(new FiltrosExportacion(null, null, "Herramientas", "Manual", "ACTIVO"),
                "equipo-a-1");
        assertThat(leer(new FiltrosExportacion("equipo-b-1", null, null, null, "ACTIVO"))).isEmpty();
    }

    @Test
    void sin_resultados_entrega_cero_filas_sin_convertirlo_en_error() {
        assertThat(leer(new FiltrosExportacion(null, "No existe", null, null, null))).isEmpty();
    }

    private List<ExportacionProductosRepository.FilaProducto> leer(FiltrosExportacion filtros) {
        List<ExportacionProductosRepository.FilaProducto> filas = new ArrayList<>();
        repositorio.recorrer(usuario, filtros, visibilidad, fila -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            filas.add(fila);
        });
        return filas;
    }

    private void assertCodigos(FiltrosExportacion filtros, String... esperados) {
        assertThat(leer(filtros)).extracting(ExportacionProductosRepository.FilaProducto::codigo)
                .containsExactly(esperados);
    }

    private void registrar(Long id, String codigo, String nombre, String categoria, String subcategoria,
            String estado, BigDecimal precio, Long stock) {
        jdbc.update("insert into productos (id, codigo, nombre_comercial, descripcion, categoria, "
                        + "subcategoria, precio_base, moneda, stock, imagen, estado) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, codigo, nombre, "Descripción de " + nombre, categoria, subcategoria,
                precio, "EUR", stock, "uno.png", estado);
    }
}
