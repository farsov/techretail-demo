package com.fabrica.app.catalogodeproductos;

import java.net.URI;
import java.nio.charset.StandardCharsets;

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

/** Descarga CSV después de comprobar permiso y filtros, antes de iniciar el flujo de datos. */
@RestController
@RequestMapping("/api/productos/exportaciones")
public class ExportacionCsvController {

    private static final MediaType CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);
    private static final MediaType PROBLEMA = MediaType.parseMediaType("application/problem+json");
    private final ExportacionCsvService servicio;

    public ExportacionCsvController(ExportacionCsvService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/csv")
    public ResponseEntity<StreamingResponseBody> exportar(
            @RequestAttribute(Usuario.ATRIBUTO) Usuario usuario,
            @RequestParam(name = "categoria", required = false) String categoria,
            @RequestParam(name = "subcategoria", required = false) String subcategoria,
            @RequestParam(name = "estado", required = false) String estado) {
        ExportacionCsvService.Filtros filtros = servicio.validarYAutorizar(usuario, categoria, subcategoria, estado);
        return ResponseEntity.ok().contentType(CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"productos.csv\"")
                .body(salida -> servicio.escribirCsv(usuario, filtros, salida));
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
