import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { ExportacionCsvService, FiltrosExportacionCsv } from './exportacion-csv.service';

type CampoFiltro = keyof FiltrosExportacionCsv;

@Component({
  selector: 'app-exportacion-csv',
  standalone: true,
  template: `
    <section aria-labelledby="titulo-exportacion-csv">
      <h2 id="titulo-exportacion-csv">Exportar catálogo de productos en CSV</h2>
      <p>
        Descarga los productos que puedes consultar. Deja los filtros vacíos para incluirlos todos.
      </p>
      <form (submit)="descargar($event)">
        @for (campo of campos; track campo.clave) {
          <label [for]="campo.clave">{{ campo.etiqueta }}</label>
          <input
            #entrada
            [id]="campo.clave"
            [name]="campo.clave"
            type="text"
            [attr.maxlength]="campo.longitud"
            [value]="filtros()[campo.clave]"
            (input)="actualizar(campo.clave, entrada.value)"
          />
        }
        <button type="submit" [disabled]="descargando()">
          {{ descargando() ? 'Preparando CSV…' : 'Descargar CSV' }}
        </button>
      </form>
      @if (mensaje()) {
        <p role="status">{{ mensaje() }}</p>
      }
      @if (error()) {
        <p role="alert">{{ error() }}</p>
      }
    </section>
  `,
  styleUrl: './exportacion-csv.component.css',
})
export class ExportacionCsvComponent {
  private readonly servicio = inject(ExportacionCsvService);
  readonly campos: ReadonlyArray<{ clave: CampoFiltro; etiqueta: string; longitud: number }> = [
    { clave: 'categoria', etiqueta: 'Categoría', longitud: 120 },
    { clave: 'subcategoria', etiqueta: 'Subcategoría', longitud: 120 },
    { clave: 'estado', etiqueta: 'Estado', longitud: 20 },
  ];
  readonly filtros = signal<FiltrosExportacionCsv>({ categoria: '', subcategoria: '', estado: '' });
  readonly descargando = signal(false);
  readonly mensaje = signal('');
  readonly error = signal('');

  actualizar(campo: CampoFiltro, valor: string): void {
    this.filtros.update((actuales) => ({ ...actuales, [campo]: valor }));
  }

  async descargar(evento: Event): Promise<void> {
    evento.preventDefault();
    if (this.descargando()) return;
    this.descargando.set(true);
    this.mensaje.set('');
    this.error.set('');
    try {
      const archivo = await firstValueFrom(this.servicio.exportar(this.filtros()));
      const url = URL.createObjectURL(archivo);
      const enlace = document.createElement('a');
      enlace.href = url;
      enlace.download = 'productos.csv';
      try {
        enlace.click();
      } finally {
        setTimeout(() => URL.revokeObjectURL(url), 0);
      }
      this.mensaje.set('Se ha iniciado la descarga del catálogo de productos.');
    } catch (fallo: unknown) {
      this.error.set(await this.mensajeDeError(fallo));
    } finally {
      this.descargando.set(false);
    }
  }

  private async mensajeDeError(fallo: unknown): Promise<string> {
    if (!(fallo instanceof HttpErrorResponse))
      return 'No se pudo descargar el catálogo. Inténtalo de nuevo.';
    let detalle: string | undefined;
    try {
      const problema =
        fallo.error instanceof Blob ? JSON.parse(await fallo.error.text()) : fallo.error;
      if (problema && typeof problema.detail === 'string') detalle = problema.detail;
    } catch {
      // Un error de red o una respuesta no JSON no tiene detalle de negocio.
    }
    if (fallo.status === 400)
      return detalle ?? 'Revisa los filtros del catálogo e inténtalo de nuevo.';
    if (fallo.status === 401) return 'Tu sesión ha caducado. Vuelve a intentarlo.';
    if (fallo.status === 403) return detalle ?? 'No tienes permiso para exportar el catálogo.';
    return 'No se pudo descargar el catálogo. Inténtalo de nuevo.';
  }
}
