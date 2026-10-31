package com.fabrica.app.catalogodeproductos;

import java.net.URI;

import com.fabrica.app.infra.auth.Usuario;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/** Descarga los productos autorizados sin materializar el catálogo ni el archivo completo. */
@RestController
@RequestMapping("/api/productos/exportaciones")
public class ExportacionProductosController {

    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private static final MediaType PROBLEMA = MediaType.parseMediaType("application/problem+json");
    private final ExportacionProductosService servicio;

    public ExportacionProductosController(ExportacionProductosService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/excel")
    public ResponseEntity<StreamingResponseBody> exportar(
            @RequestAttribute(Usuario.ATRIBUTO) Usuario usuario,
            @RequestParam(name = "codigo", required = false) String codigo,
            @RequestParam(name = "nombre_comercial", required = false) String nombreComercial,
            @RequestParam(name = "categoria", required = false) String categoria,
            @RequestParam(name = "subcategoria", required = false) String subcategoria,
            @RequestParam(name = "estado", required = false) String estado) {
        FiltrosExportacion filtros = new FiltrosExportacion(codigo, nombreComercial, categoria, subcategoria, estado);
        servicio.comprobarPermiso(usuario);
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"catalogo-productos.xlsx\"")
                .body(salida -> servicio.escribirExcel(usuario, filtros, salida));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> filtroInvalido(IllegalArgumentException error) {
        return problema(HttpStatus.BAD_REQUEST, "Filtro inválido", "urn:problema:catalogo:filtro-invalido",
                error.getMessage());
    }

    @ExceptionHandler(ExportacionProductosService.PermisoExportacionDenegado.class)
    public ResponseEntity<ProblemDetail> permisoDenegado(ExportacionProductosService.PermisoExportacionDenegado error) {
        return problema(HttpStatus.FORBIDDEN, "Acceso denegado", "urn:problema:catalogo:acceso-denegado",
                error.getMessage());
    }

    private static ResponseEntity<ProblemDetail> problema(HttpStatus estado, String titulo, String tipo,
            String detalle) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
        problema.setType(URI.create(tipo));
        problema.setTitle(titulo);
        return ResponseEntity.status(estado).contentType(PROBLEMA).body(problema);
    }
}
