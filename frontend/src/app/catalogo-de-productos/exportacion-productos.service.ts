import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface FiltrosExportacionProductos {
  codigo: string;
  nombre_comercial: string;
  categoria: string;
  subcategoria: string;
  estado: string;
}

@Injectable({ providedIn: 'root' })
export class ExportacionProductosService {
  private readonly http = inject(HttpClient);

  exportar(filtros: FiltrosExportacionProductos): Observable<Blob> {
    let params = new HttpParams();
    for (const [nombre, valor] of Object.entries(filtros)) {
      if (valor !== '') params = params.set(nombre, valor);
    }
    return this.http.get('/api/productos/exportaciones/excel', { params, responseType: 'blob' });
  }
}
