package com.fabrica.app.catalogodeproductos.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Mapeo exclusivamente de las columnas del producto necesarias para exportar. */
@Entity
@Table(name = "productos")
public class Producto {

    @Id
    private Long id;

    @Column(name = "codigo", length = 64)
    private String codigo;

    @Column(name = "nombre_comercial", length = 255)
    private String nombreComercial;

    @Column(name = "descripcion")
    private String descripcion;

    @Column(name = "categoria", length = 120)
    private String categoria;

    @Column(name = "subcategoria", length = 120)
    private String subcategoria;

    @Column(name = "precio_base")
    private BigDecimal precioBase;

    @Column(name = "moneda")
    private String moneda;

    @Column(name = "stock")
    private Long stock;

    @Column(name = "imagen")
    private String imagen;

    @Column(name = "estado", length = 20)
    private String estado;

    protected Producto() {
        // Requerido por JPA; los resultados se leen como proyecciones, no como entidades.
    }
}
