package com.fabrica.app.catalogodeproductos;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

import com.fabrica.app.infra.auth.Usuario;
import org.springframework.stereotype.Service;

/** Coordina autorización, consulta y escritura incremental del catálogo. */
@Service
public class ExportacionProductosService {

    // La política de consulta disponible para este catálogo es la del Gestor de productos:
    // dicho rol consulta todos los productos. Los filtros siempre se combinan con su visibilidad.
    private static final String GESTOR_PRODUCTOS = "GESTOR_PRODUCTOS";

    private final ExportacionProductosRepository repositorio;
    private final ExcelProductosWriter escritor;

    public ExportacionProductosService(ExportacionProductosRepository repositorio, ExcelProductosWriter escritor) {
        this.repositorio = repositorio;
        this.escritor = escritor;
    }

    public void comprobarPermiso(Usuario usuario) {
        if (usuario == null || !usuario.tieneRol(GESTOR_PRODUCTOS)) {
            throw new PermisoExportacionDenegado();
        }
    }

    public void escribirExcel(Usuario usuario, FiltrosExportacion filtros, OutputStream salida) throws IOException {
        comprobarPermiso(usuario);
        Objects.requireNonNull(filtros, "Se necesitan filtros de exportación.");
        escritor.escribir(salida, consumidor -> repositorio.recorrer(usuario, filtros,
                (producto, cb, solicitante) -> {
                    // Una denegación nunca se transforma en un predicado sin restricciones.
                    comprobarPermiso(solicitante);
                    return cb.conjunction();
                }, consumidor));
    }

    public static final class PermisoExportacionDenegado extends RuntimeException {
        public PermisoExportacionDenegado() {
            super("No tienes permiso para exportar el catálogo de productos.");
        }
    }
}
