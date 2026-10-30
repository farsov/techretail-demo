import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { authInterceptor } from './auth';

const tick = () => new Promise<void>((r) => setTimeout(r, 0));

describe('authInterceptor (perfil de desarrollo)', () => {
  let http: HttpClient;
  let servidor: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    servidor = TestBed.inject(HttpTestingController);
  });

  afterEach(() => servidor.verify());

  it('pide el token una vez y lo envía como Bearer en las llamadas a /api', async () => {
    const primera = firstValueFrom(http.get('/api/vacaciones'));
    await tick();
    servidor.expectOne('/api/dev/token').flush({ token: 'tok-1', expira_en: 3600 });
    await tick();
    const req = servidor.expectOne('/api/vacaciones');
    expect(req.request.headers.get('Authorization')).toBe('Bearer tok-1');
    req.flush([]);
    await primera;

    const segunda = firstValueFrom(http.get('/api/vacaciones/1'));
    await tick();
    servidor.expectNone('/api/dev/token');
    servidor.expectOne('/api/vacaciones/1').flush({});
    await segunda;
  });

  it('las rutas públicas y las externas van sin token', async () => {
    const salud = firstValueFrom(http.get('/api/salud'));
    const req = servidor.expectOne('/api/salud');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({ estado: 'ok' });
    await salud;
  });

  it('ante un 401 renueva el token y reintenta una sola vez', async () => {
    const respuesta = firstValueFrom(http.get('/api/vacaciones'));
    await tick();
    servidor.expectOne('/api/dev/token').flush({ token: 'viejo', expira_en: 3600 });
    await tick();
    servidor.expectOne('/api/vacaciones').flush(null, { status: 401, statusText: 'Unauthorized' });
    await tick();
    servidor.expectOne('/api/dev/token').flush({ token: 'nuevo', expira_en: 3600 });
    await tick();
    const reintento = servidor.expectOne('/api/vacaciones');
    expect(reintento.request.headers.get('Authorization')).toBe('Bearer nuevo');
    reintento.flush(['ok']);
    expect(await respuesta).toEqual(['ok']);
  });
});
