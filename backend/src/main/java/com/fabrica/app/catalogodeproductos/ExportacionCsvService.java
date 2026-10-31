package com.fabrica.app.catalogodeproductos;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import com.fabrica.app.infra.auth.Usuario;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;

/** Autoriza los filtros antes de leer productos y escribe únicamente las columnas del contrato CSV. */
@Service
public class ExportacionCsvService {

    private static final String GESTOR_PRODUCTOS = "GESTOR_PRODUCTOS";
    private static final Set<String> ESTADOS_PERMITIDOS = Set.of("ACTIVO", "INACTIVO");
    private static final String CABECERA =
            "codigo,nombre_comercial,categoria,subcategoria,precio_base,moneda,stock,estado\r\n";

    private final ProductoExportacionRepository repositorio;

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
        OutputStreamWriter escritor = new OutputStreamWriter(salida, StandardCharsets.UTF_8);
        escritor.write(CABECERA);
        try {
            repositorio.recorrer(usuario, filtros.categoria(), filtros.subcategoria(), filtros.estado(),
                    (producto, cb, solicitante) -> {
                        comprobarPermiso(solicitante);
                        return cb.conjunction();
                    }, fila -> {
                        try {
                            escribirFila(escritor, fila);
                        } catch (IOException error) {
                            throw new UncheckedIOException(error);
                        }
                    });
            escritor.flush();
        } catch (UncheckedIOException error) {
            throw error.getCause();
        }
    }

    private static void escribirFila(OutputStreamWriter escritor, ProductoExportacionFila fila)
            throws IOException {
        escritor.write(escapar(fila.codigo()));
        escritor.write(',');
        escritor.write(escapar(fila.nombreComercial()));
        escritor.write(',');
        escritor.write(escapar(fila.categoria()));
        escritor.write(',');
        escritor.write(escapar(fila.subcategoria()));
        escritor.write(',');
        escritor.write(fila.precioBase() == null ? "" : fila.precioBase().toPlainString());
        escritor.write(',');
        escritor.write(escapar(fila.moneda()));
        escritor.write(',');
        escritor.write(fila.stock() == null ? "" : fila.stock().toString());
        escritor.write(',');
        escritor.write(escapar(fila.estado()));
        escritor.write("\r\n");
    }

    private static String escapar(String valor) {
        if (valor == null) {
            return "";
        }
        if (valor.indexOf(',') >= 0 || valor.indexOf('"') >= 0 || valor.indexOf('\n') >= 0
                || valor.indexOf('\r') >= 0) {
            return '"' + valor.replace("\"", "\"\"") + '"';
        }
        return valor;
    }
}
