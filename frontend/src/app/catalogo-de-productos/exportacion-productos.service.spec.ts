import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import {
  ExportacionProductosService,
  FiltrosExportacionProductos,
} from './exportacion-productos.service';

const vacios: FiltrosExportacionProductos = {
  codigo: '',
  nombre_comercial: '',
  categoria: '',
  subcategoria: '',
  estado: '',
};
const ruta = '/api/productos/exportaciones/excel';

describe('ExportacionProductosService', () => {
  let servicio: ExportacionProductosService;
  let servidor: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    servicio = TestBed.inject(ExportacionProductosService);
    servidor = TestBed.inject(HttpTestingController);
  });

  afterEach(() => servidor.verify());

  it('solicita todos los productos autorizados sin enviar parámetros vacíos y conserva el archivo recibido', async () => {
    const descarga = firstValueFrom(servicio.exportar(vacios));
    const peticion = servidor.expectOne(ruta);
    expect(peticion.request.method).toBe('GET');
    expect(peticion.request.responseType).toBe('blob');
    expect(peticion.request.params.keys()).toEqual([]);
    const excel = new Blob([new Uint8Array([0x50, 0x4b, 0x03, 0x04])], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    });
    peticion.flush(excel);
    expect(await descarga).toBe(excel);
  });

  it('envía exactamente los cinco filtros del contrato sin modificar sus valores', async () => {
    const filtros: FiltrosExportacionProductos = {
      codigo: 'ABC-001',
      nombre_comercial: 'Café especial',
      categoria: 'Bebidas',
      subcategoria: 'Café',
      estado: 'ACTIVO',
    };
    const descarga = firstValueFrom(servicio.exportar(filtros));
    const peticion = servidor.expectOne((req) => req.url === ruta);
    expect(peticion.request.method).toBe('GET');
    expect(peticion.request.responseType).toBe('blob');
    expect(peticion.request.params.keys().sort()).toEqual(Object.keys(filtros).sort());
    for (const [clave, valor] of Object.entries(filtros)) {
      expect(peticion.request.params.get(clave)).toBe(valor);
    }
    peticion.flush(new Blob([]));
    await descarga;
  });

  it('omite únicamente los filtros vacíos y no inventa otros parámetros', async () => {
    const descarga = firstValueFrom(
      servicio.exportar({ ...vacios, codigo: 'A-1', estado: 'INACTIVO' }),
    );
    const peticion = servidor.expectOne((req) => req.url === ruta);
    expect(peticion.request.params.keys().sort()).toEqual(['codigo', 'estado']);
    expect(peticion.request.params.get('codigo')).toBe('A-1');
    expect(peticion.request.params.get('estado')).toBe('INACTIVO');
    peticion.flush(new Blob([]));
    await descarga;
  });
});
