import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ExportacionCsvComponent } from './exportacion-csv.component';

const ruta = '/api/productos/exportaciones/csv';
const cabecera =
  'codigo,nombre_comercial,categoria,subcategoria,precio_base,moneda,stock,estado\r\n';

describe('ExportacionCsvComponent: flujo de descarga', () => {
  let fixture: ComponentFixture<ExportacionCsvComponent>;
  let servidor: HttpTestingController;
  let crearUrl: ReturnType<typeof vi.fn>;
  let enlaceDescargado: HTMLAnchorElement | undefined;
  let descriptorCrear: PropertyDescriptor | undefined;
  let descriptorRevocar: PropertyDescriptor | undefined;

  beforeEach(async () => {
    descriptorCrear = Object.getOwnPropertyDescriptor(URL, 'createObjectURL');
    descriptorRevocar = Object.getOwnPropertyDescriptor(URL, 'revokeObjectURL');
    crearUrl = vi.fn(() => 'blob:catalogo-csv');
    Object.defineProperty(URL, 'createObjectURL', { configurable: true, value: crearUrl });
    Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: vi.fn() });
    enlaceDescargado = undefined;
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (
      this: HTMLAnchorElement,
    ) {
      enlaceDescargado = this;
    });
    await TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    servidor = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ExportacionCsvComponent);
    fixture.detectChanges();
    await fixture.whenStable();
  });

  afterEach(() => {
    servidor.verify();
    vi.restoreAllMocks();
    if (descriptorCrear) Object.defineProperty(URL, 'createObjectURL', descriptorCrear);
    else Reflect.deleteProperty(URL, 'createObjectURL');
    if (descriptorRevocar) Object.defineProperty(URL, 'revokeObjectURL', descriptorRevocar);
    else Reflect.deleteProperty(URL, 'revokeObjectURL');
  });

  function escribir(campo: string, valor: string): void {
    const entrada = fixture.nativeElement.querySelector(
      `input[name="${campo}"]`,
    ) as HTMLInputElement;
    entrada.value = valor;
    entrada.dispatchEvent(new Event('input', { bubbles: true }));
  }

  function enviar(): void {
    (fixture.nativeElement.querySelector('form') as HTMLFormElement).dispatchEvent(
      new Event('submit', { bubbles: true, cancelable: true }),
    );
  }

  it('HU-124/C1: sin filtros inicia la descarga CSV con productos y conserva el archivo recibido', async () => {
    const entradas = fixture.nativeElement.querySelectorAll(
      'input',
    ) as NodeListOf<HTMLInputElement>;
    expect(Array.from(entradas, (entrada) => [entrada.name, entrada.maxLength])).toEqual([
      ['categoria', 120],
      ['subcategoria', 120],
      ['estado', 20],
    ]);
    expect(fixture.nativeElement.querySelector('button')?.textContent).toContain('Descargar CSV');
    enviar();
    const peticion = servidor.expectOne(ruta);
    expect(peticion.request.method).toBe('GET');
    expect(peticion.request.responseType).toBe('blob');
    expect(peticion.request.params.keys()).toEqual([]);
    const csv = new Blob([cabecera + 'CAF-01,Café,Bebidas,Café,123.45,EUR,17,ACTIVO\r\n'], {
      type: 'text/csv;charset=UTF-8',
    });
    peticion.flush(csv);
    await fixture.whenStable();
    expect(crearUrl).toHaveBeenCalledTimes(1);
    expect(crearUrl).toHaveBeenCalledWith(csv);
    expect(enlaceDescargado?.download).toBe('productos.csv');
    expect(enlaceDescargado?.href).toBe('blob:catalogo-csv');
    expect(fixture.nativeElement.querySelector('[role="status"]')?.textContent).toContain(
      'Se ha iniciado la descarga',
    );
    expect(fixture.nativeElement.querySelector('[role="alert"]')).toBeNull();
  });

  it('HU-124/C2: descarga sin recalcular precios ni existencias registrados incluso en sus límites', async () => {
    const filtros = { categoria: 'Electrónica', subcategoria: 'Audio, vídeo', estado: 'ACTIVO' };
    for (const [campo, valor] of Object.entries(filtros)) escribir(campo, valor);
    await fixture.whenStable();
    enviar();
    const peticion = servidor.expectOne((req) => req.url === ruta);
    expect(peticion.request.params.keys().sort()).toEqual(Object.keys(filtros).sort());
    for (const [campo, valor] of Object.entries(filtros))
      expect(peticion.request.params.get(campo)).toBe(valor);
    const csv = new Blob(
      [
        cabecera +
          'ALT-01,"Altavoz ""Pro"", edición",Electrónica,"Audio, vídeo",9999999999999.99,EUR,2147483647,ACTIVO\r\n',
      ],
      { type: 'text/csv;charset=UTF-8' },
    );
    peticion.flush(csv);
    await fixture.whenStable();
    expect(crearUrl).toHaveBeenCalledTimes(1);
    expect(crearUrl).toHaveBeenCalledWith(csv);
    expect(enlaceDescargado?.download).toBe('productos.csv');
    expect(fixture.nativeElement.querySelector('[role="alert"]')).toBeNull();
  });

  it('si la consulta no devuelve productos, descarga el CSV de solo cabecera', async () => {
    escribir('categoria', 'Sin coincidencias');
    await fixture.whenStable();
    enviar();
    const peticion = servidor.expectOne((req) => req.url === ruta);
    expect(peticion.request.params.keys()).toEqual(['categoria']);
    expect(peticion.request.params.get('categoria')).toBe('Sin coincidencias');
    const csv = new Blob([cabecera], { type: 'text/csv;charset=UTF-8' });
    peticion.flush(csv);
    await fixture.whenStable();
    expect(crearUrl).toHaveBeenCalledTimes(1);
    expect(crearUrl).toHaveBeenCalledWith(csv);
    expect(enlaceDescargado?.download).toBe('productos.csv');
    expect(fixture.nativeElement.querySelector('[role="alert"]')).toBeNull();
  });

  it.each([
    [400, 'Bad Request', 'El estado indicado no es válido.', 'El estado indicado no es válido.'],
    [401, 'Unauthorized', 'No autenticado.', 'Tu sesión ha caducado'],
    [
      403,
      'Forbidden',
      'No tienes permiso para aplicar este filtro.',
      'No tienes permiso para aplicar este filtro.',
    ],
  ] as const)(
    'ante un error %i informa sin descargar el archivo',
    async (codigo, texto, detalle, esperado) => {
      enviar();
      servidor.expectOne(ruta).flush(
        new Blob([JSON.stringify({ title: texto, status: codigo, detail: detalle })], {
          type: 'application/problem+json',
        }),
        { status: codigo, statusText: texto },
      );
      await fixture.whenStable();
      expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain(
        esperado,
      );
      expect(fixture.nativeElement.querySelector('[role="status"]')).toBeNull();
      expect(crearUrl).not.toHaveBeenCalled();
      expect(enlaceDescargado).toBeUndefined();
    },
  );
});
