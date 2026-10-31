package com.fabrica.app.catalogodeproductos;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Set;

import com.fabrica.app.infra.auth.Usuario;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;

/** Autoriza los filtros antes de leer productos y escribe únicamente las columnas del contrato CSV. */
@Service
public class ExportacionCsvService {

    private static final String GESTOR_PRODUCTOS = "GESTOR_PRODUCTOS";
    private static final Set<String> ESTADOS_PERMITIDOS = Set.of("ACTIVO", "INACTIVO");

    private final ProductoExportacionRepository repositorio;
    private final ProductoCsvWriter escritor = new ProductoCsvWriter();

    public ExportacionCsvService(ProductoExportacionRepository repositorio) {
        this.repositorio = repositorio;
    }

    /** Valores opcionales del contrato; null significa que no se aplica el filtro. */
    public record Filtros(
            @Size(max = 120) String categoria,
            @Size(max = 120) String subcategoria,
            @Size(max = 20) String estado) {
        public Filtros {
            comprobarValor("categoria", categoria, 120);
            comprobarValor("subcategoria", subcategoria, 120);
            comprobarValor("estado", estado, 20);
            if (estado != null && !ESTADOS_PERMITIDOS.contains(estado)) {
                throw new IllegalArgumentException("El filtro estado no tiene un valor permitido.");
            }
        }

        private static void comprobarValor(String nombre, String valor, int longitudMaxima) {
            if (valor != null && (valor.isBlank() || valor.length() > longitudMaxima)) {
                throw new IllegalArgumentException("El filtro " + nombre + " no tiene un valor válido.");
            }
        }
    }

    public void comprobarPermiso(Usuario usuario) {
        if (usuario == null || !usuario.tieneRol(GESTOR_PRODUCTOS)) {
            throw new ExportacionProductosService.PermisoExportacionDenegado();
        }
    }

    /** Las comprobaciones de permiso y de filtros se hacen antes de entregar el archivo o consultar datos. */
    public Filtros validarYAutorizar(Usuario usuario, String categoria, String subcategoria, String estado) {
        comprobarPermiso(usuario);
        // La política de consulta del catálogo autoriza al gestor a ver todos sus productos;
        // los filtros restringen dicha visibilidad, nunca la amplían.
        return new Filtros(categoria, subcategoria, estado);
    }

    public void escribirCsv(Usuario usuario, Filtros filtros, OutputStream salida) throws IOException {
        comprobarPermiso(usuario);
        if (filtros == null) {
            throw new IllegalArgumentException("Se necesitan filtros de exportación.");
        }
        escritor.escribir(salida, consumidor -> repositorio.recorrer(usuario,
                filtros.categoria(), filtros.subcategoria(), filtros.estado(),
                (producto, cb, solicitante) -> {
                    comprobarPermiso(solicitante);
                    return cb.conjunction();
                }, consumidor));
    }
}
