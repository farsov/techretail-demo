import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { ExportacionCsvService, FiltrosExportacionCsv } from './exportacion-csv.service';

const ruta = '/api/productos/exportaciones/csv';
const vacios: FiltrosExportacionCsv = { categoria: '', subcategoria: '', estado: '' };

describe('ExportacionCsvService', () => {
  let servicio: ExportacionCsvService;
  let servidor: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    servicio = TestBed.inject(ExportacionCsvService);
    servidor = TestBed.inject(HttpTestingController);
  });

  afterEach(() => servidor.verify());

  it('HU-124/C1: solicita el CSV sin filtros y devuelve el archivo de la API intacto', async () => {
    const descarga = firstValueFrom(servicio.exportar(vacios));
    const peticion = servidor.expectOne(ruta);
    expect(peticion.request.method).toBe('GET');
    expect(peticion.request.responseType).toBe('blob');
    expect(peticion.request.params.keys()).toEqual([]);
    const csv = new Blob(
      [
        'codigo,nombre_comercial,categoria,subcategoria,precio_base,moneda,stock,estado\r\n' +
          'CAF-01,Café,Bebidas,Infusiones,123.45,EUR,17,ACTIVO\r\n',
      ],
      { type: 'text/csv;charset=UTF-8' },
    );
    peticion.flush(csv);
    expect(await descarga).toBe(csv);
  });

  it('HU-124/C2: envía únicamente los tres filtros admitidos y no modifica el CSV con precio y stock registrados', async () => {
    const filtros: FiltrosExportacionCsv = {
      categoria: 'Café y té',
      subcategoria: 'Infusiones, especiales',
      estado: 'ACTIVO',
    };
    const descarga = firstValueFrom(servicio.exportar(filtros));
    const peticion = servidor.expectOne((req) => req.url === ruta);
    expect(peticion.request.method).toBe('GET');
    expect(peticion.request.responseType).toBe('blob');
    expect(peticion.request.params.keys().sort()).toEqual(Object.keys(filtros).sort());
    for (const [nombre, valor] of Object.entries(filtros))
      expect(peticion.request.params.get(nombre)).toBe(valor);
    const csv = new Blob(
      [
        'codigo,nombre_comercial,categoria,subcategoria,precio_base,moneda,stock,estado\n' +
          'CAF-01,"Café, especial",Bebidas,Infusiones,9999999999999.99,EUR,2147483647,ACTIVO\n',
      ],
      { type: 'text/csv;charset=UTF-8' },
    );
    peticion.flush(csv);
    expect(await descarga).toBe(csv);
  });

  it('omite solo los filtros vacíos, sin exigir filtros ni añadir parámetros ajenos al contrato', async () => {
    const descarga = firstValueFrom(
      servicio.exportar({ ...vacios, categoria: 'Bebidas', estado: 'ACTIVO' }),
    );
    const peticion = servidor.expectOne((req) => req.url === ruta);
    expect(peticion.request.params.keys().sort()).toEqual(['categoria', 'estado']);
    expect(peticion.request.params.get('categoria')).toBe('Bebidas');
    expect(peticion.request.params.get('estado')).toBe('ACTIVO');
    peticion.flush(new Blob([]));
    await descarga;
  });
});
