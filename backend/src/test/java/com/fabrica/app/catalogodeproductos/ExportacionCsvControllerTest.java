package com.fabrica.app.catalogodeproductos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.fabrica.app.infra.auth.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/** Comprueba que el controlador valida antes de publicar una descarga y traduce los errores. */
class ExportacionCsvControllerTest {

    private final ExportacionCsvService servicio = mock(ExportacionCsvService.class);
    private final ExportacionCsvController controlador = new ExportacionCsvController(servicio);
    private final Usuario gestor = new Usuario("gestora", List.of("GESTOR_PRODUCTOS"));

    @Test
    void entrega_descarga_csv_sin_filtros_solo_despues_de_validar_y_autorizar() throws Exception {
        ExportacionCsvService.Filtros filtros = new ExportacionCsvService.Filtros(null, null, null);
        when(servicio.validarYAutorizar(gestor, null, null, null)).thenReturn(filtros);

        ResponseEntity<StreamingResponseBody> respuesta = controlador.exportar(gestor, null, null, null);

        verify(servicio).validarYAutorizar(gestor, null, null, null);
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getHeaders().getContentType().isCompatibleWith(
                MediaType.parseMediaType("text/csv"))).isTrue();
        assertThat(respuesta.getHeaders().getContentType().getCharset()).isEqualTo(StandardCharsets.UTF_8);
        assertThat(respuesta.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("attachment; filename=\"productos.csv\"");
        assertThat(respuesta.getBody()).isNotNull();
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        respuesta.getBody().writeTo(salida);
        verify(servicio).escribirCsv(gestor, filtros, salida);
        verifyNoMoreInteractions(servicio);
    }

    @Test
    void transmite_los_filtros_al_servicio_antes_de_devolver_la_descarga() {
        ExportacionCsvService.Filtros filtros = new ExportacionCsvService.Filtros("Cocina", "Tazas", "ACTIVO");
        when(servicio.validarYAutorizar(gestor, "Cocina", "Tazas", "ACTIVO")).thenReturn(filtros);

        ResponseEntity<StreamingResponseBody> respuesta = controlador.exportar(gestor, "Cocina", "Tazas", "ACTIVO");

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(servicio).validarYAutorizar(gestor, "Cocina", "Tazas", "ACTIVO");
        verifyNoMoreInteractions(servicio);
    }

    @Test
    void no_entrega_archivo_si_el_servicio_rechaza_un_filtro() {
        IllegalArgumentException error = new IllegalArgumentException("El filtro estado no tiene un valor permitido.");
        when(servicio.validarYAutorizar(gestor, null, null, "DESCONOCIDO")).thenThrow(error);

        assertThatThrownBy(() -> controlador.exportar(gestor, null, null, "DESCONOCIDO"))
                .isSameAs(error);
        verify(servicio).validarYAutorizar(gestor, null, null, "DESCONOCIDO");
        verifyNoMoreInteractions(servicio);
        comprobarProblema(controlador.filtroInvalido(error), HttpStatus.BAD_REQUEST,
                "Filtro inválido", "urn:problema:catalogo:filtro-invalido", error.getMessage());
    }

    @Test
    void no_entrega_archivo_si_el_servicio_deniega_el_permiso() {
        Usuario lector = new Usuario("lector", List.of("LECTOR"));
        ExportacionProductosService.PermisoExportacionDenegado error =
                new ExportacionProductosService.PermisoExportacionDenegado();
        when(servicio.validarYAutorizar(lector, null, null, null)).thenThrow(error);

        assertThatThrownBy(() -> controlador.exportar(lector, null, null, null)).isSameAs(error);
        verify(servicio).validarYAutorizar(lector, null, null, null);
        verifyNoMoreInteractions(servicio);
        comprobarProblema(controlador.permisoDenegado(error), HttpStatus.FORBIDDEN,
                "Acceso denegado", "urn:problema:catalogo:acceso-denegado", error.getMessage());
    }

    private static void comprobarProblema(ResponseEntity<ProblemDetail> respuesta, HttpStatus estado,
            String titulo, String tipo, String detalle) {
        assertThat(respuesta.getStatusCode()).isEqualTo(estado);
        assertThat(respuesta.getHeaders().getContentType().toString()).isEqualTo("application/problem+json");
        assertThat(respuesta.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).isNull();
        assertThat(respuesta.getBody()).isNotNull();
        assertThat(respuesta.getBody().getStatus()).isEqualTo(estado.value());
        assertThat(respuesta.getBody().getTitle()).isEqualTo(titulo);
        assertThat(respuesta.getBody().getType().toString()).isEqualTo(tipo);
        assertThat(respuesta.getBody().getDetail()).isEqualTo(detalle);
    }
}
