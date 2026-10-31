import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface FiltrosExportacionCsv {
  categoria: string;
  subcategoria: string;
  estado: string;
}

@Injectable({ providedIn: 'root' })
export class ExportacionCsvService {
  private readonly http = inject(HttpClient);

  exportar(filtros: FiltrosExportacionCsv): Observable<Blob> {
    let params = new HttpParams();
    for (const [nombre, valor] of Object.entries(filtros)) {
      if (valor !== '') params = params.set(nombre, valor);
    }
    return this.http.get('/api/productos/exportaciones/csv', { params, responseType: 'blob' });
  }
}
