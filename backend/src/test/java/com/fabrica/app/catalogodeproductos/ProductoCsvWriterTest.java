package com.fabrica.app.catalogodeproductos;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

/** Pruebas de formato del escritor usado por el servicio de exportación. */
class ProductoCsvWriterTest {

    private static final String CABECERA =
            "codigo,nombre_comercial,categoria,subcategoria,precio_base,moneda,stock,estado\r\n";
    private final ProductoCsvWriter escritor = new ProductoCsvWriter();

    @Test
    void escribe_cabecera_utf_8_aun_cuando_no_existan_productos() throws Exception {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();

        escritor.escribir(salida, consumidor -> { });

        assertThat(salida.toByteArray()).isEqualTo(CABECERA.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void conserva_los_limites_de_precio_y_stock_sin_redondeos_ni_conversiones() throws Exception {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();

        escritor.escribir(salida, consumidor -> {
            consumidor.accept(new ProductoExportacionFila("MAX", "Límite", "Hogar", "Cocina",
                    new BigDecimal("9999999999999.99"), "EUR", 2147483647L, "ACTIVO"));
            consumidor.accept(new ProductoExportacionFila("MIN", "Cero", "Hogar", "Cocina",
                    new BigDecimal("0.01"), "EUR", 0L, "INACTIVO"));
        });

        assertThat(salida.toByteArray()).isEqualTo((CABECERA
                + "MAX,Límite,Hogar,Cocina,9999999999999.99,EUR,2147483647,ACTIVO\r\n"
                + "MIN,Cero,Hogar,Cocina,0.01,EUR,0,INACTIVO\r\n")
                .getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void escapa_comas_comillas_y_saltos_de_linea_sin_perder_el_contenido_unicode() throws Exception {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();

        escritor.escribir(salida, consumidor -> consumidor.accept(new ProductoExportacionFila(
                "P,1", "Café \"especial\"\nGrande", "Bebidas, frías", "Té\r\nverde",
                new BigDecimal("12.30"), "EUR", 7L, "ACTIVO")));

        assertThat(salida.toByteArray()).isEqualTo((CABECERA
                + "\"P,1\",\"Café \"\"especial\"\"\nGrande\",\"Bebidas, frías\",\"Té\r\nverde\","
                + "12.30,EUR,7,ACTIVO\r\n").getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void mantiene_abierto_el_flujo_del_solicitante_al_terminar() throws Exception {
        AtomicBoolean cerrado = new AtomicBoolean();
        ByteArrayOutputStream salida = new ByteArrayOutputStream() {
            @Override
            public void close() {
                cerrado.set(true);
            }
        };

        escritor.escribir(salida, consumidor -> { });

        assertThat(cerrado).isFalse();
        assertThat(salida.toByteArray()).isEqualTo(CABECERA.getBytes(StandardCharsets.UTF_8));
    }
}
