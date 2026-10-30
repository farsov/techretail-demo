import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ExportacionProductosComponent } from './exportacion-productos.component';

const ruta = '/api/productos/exportaciones/excel';

describe('ExportacionProductosComponent: flujo de descarga', () => {
  let fixture: ComponentFixture<ExportacionProductosComponent>;
  let servidor: HttpTestingController;
  let crearUrl: ReturnType<typeof vi.fn>;
  let enlaceDescargado: HTMLAnchorElement | undefined;
  let descriptorCrear: PropertyDescriptor | undefined;
  let descriptorRevocar: PropertyDescriptor | undefined;

  beforeEach(async () => {
    descriptorCrear = Object.getOwnPropertyDescriptor(URL, 'createObjectURL');
    descriptorRevocar = Object.getOwnPropertyDescriptor(URL, 'revokeObjectURL');
    crearUrl = vi.fn(() => 'blob:catalogo-de-productos');
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
    fixture = TestBed.createComponent(ExportacionProductosComponent);
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

  it('HU-123/C1: sin filtros inicia la descarga del Excel recibido, sin alterar los datos del archivo', async () => {
    const entradas = fixture.nativeElement.querySelectorAll(
      'input',
    ) as NodeListOf<HTMLInputElement>;
    expect(Array.from(entradas, (entrada) => [entrada.name, entrada.maxLength])).toEqual([
      ['codigo', 64],
      ['nombre_comercial', 255],
      ['categoria', 120],
      ['subcategoria', 120],
      ['estado', 20],
    ]);
    const formulario = fixture.nativeElement.querySelector('form') as HTMLFormElement;
    expect(formulario.querySelector('button')?.textContent).toContain('Descargar Excel');
    formulario.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    const peticion = servidor.expectOne(ruta);
    expect(peticion.request.method).toBe('GET');
    expect(peticion.request.responseType).toBe('blob');
    expect(peticion.request.params.keys()).toEqual([]);
    const excel = new Blob([new Uint8Array([0x50, 0x4b, 0x03, 0x04, 0x01, 0x02])], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    });
    peticion.flush(excel);
    await fixture.whenStable();
    expect(crearUrl).toHaveBeenCalledTimes(1);
    expect(crearUrl).toHaveBeenCalledWith(excel);
    expect(enlaceDescargado?.download).toBe('catalogo-productos.xlsx');
    expect(enlaceDescargado?.href).toBe('blob:catalogo-de-productos');
    expect(fixture.nativeElement.querySelector('[role="status"]')?.textContent).toContain(
      'Se ha iniciado la descarga',
    );
    expect(fixture.nativeElement.querySelector('[role="alert"]')).toBeNull();
  });

  it('HU-123/C2: conserva sin transformar el Excel con los precios y existencias registrados', async () => {
    const filtros = {
      codigo: 'PROD-123',
      nombre_comercial: 'Té verde',
      categoria: 'Bebidas',
      subcategoria: 'Infusiones',
      estado: 'ACTIVO',
    };
    for (const [campo, valor] of Object.entries(filtros)) escribir(campo, valor);
    await fixture.whenStable();
    (fixture.nativeElement.querySelector('form') as HTMLFormElement).dispatchEvent(
      new Event('submit', { bubbles: true, cancelable: true }),
    );
    const peticion = servidor.expectOne((req) => req.url === ruta);
    expect(peticion.request.params.keys().sort()).toEqual(Object.keys(filtros).sort());
    for (const [campo, valor] of Object.entries(filtros)) {
      expect(peticion.request.params.get(campo)).toBe(valor);
    }
    // El Excel, incluidos precio_base y stock, debe descargarse tal cual lo devuelve la API.
    const excel = new Blob([new Uint8Array([0x50, 0x4b, 0x03, 0x04, 0xff])]);
    peticion.flush(excel);
    await fixture.whenStable();
    expect(crearUrl).toHaveBeenCalledTimes(1);
    expect(crearUrl).toHaveBeenCalledWith(excel);
    expect(enlaceDescargado?.download).toBe('catalogo-productos.xlsx');
  });

  it('muestra el detalle de un filtro inválido y no inicia descarga', async () => {
    (fixture.nativeElement.querySelector('form') as HTMLFormElement).dispatchEvent(
      new Event('submit', { bubbles: true, cancelable: true }),
    );
    servidor.expectOne(ruta).flush(
      new Blob([JSON.stringify({ status: 400, detail: 'El código es demasiado largo.' })], {
        type: 'application/problem+json',
      }),
      { status: 400, statusText: 'Bad Request' },
    );
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain(
      'El código es demasiado largo.',
    );
    expect(crearUrl).not.toHaveBeenCalled();
    expect(enlaceDescargado).toBeUndefined();
  });

  it('informa cuando el usuario no tiene permiso para exportar', async () => {
    (fixture.nativeElement.querySelector('form') as HTMLFormElement).dispatchEvent(
      new Event('submit', { bubbles: true, cancelable: true }),
    );
    servidor.expectOne(ruta).flush(
      new Blob([JSON.stringify({ status: 403, detail: 'No tienes permiso para exportar.' })], {
        type: 'application/problem+json',
      }),
      { status: 403, statusText: 'Forbidden' },
    );
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain(
      'No tienes permiso para exportar.',
    );
    expect(crearUrl).not.toHaveBeenCalled();
  });
});
