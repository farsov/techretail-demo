import {
  HttpBackend,
  HttpClient,
  HttpErrorResponse,
  HttpInterceptorFn,
  HttpRequest,
} from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { catchError, firstValueFrom, from, switchMap, throwError } from 'rxjs';

interface TokenDev {
  token: string;
  expira_en: number;
}

/** Sesión del perfil de desarrollo: pide al backend el token de un usuario de prueba y lo guarda en memoria. */
@Injectable({ providedIn: 'root' })
export class SesionDev {
  // Sin interceptores: si no, pedir el token volvería a pasar por el interceptor.
  private readonly http = new HttpClient(inject(HttpBackend));
  private token: { valor: string; vence: number } | null = null;
  private pendiente: Promise<string> | null = null;

  obtener(renovar = false): Promise<string> {
    if (!renovar && this.token && this.token.vence > Date.now())
      return Promise.resolve(this.token.valor);
    this.pendiente ??= firstValueFrom(this.http.get<TokenDev>('/api/dev/token'))
      .then((t) => {
        // Se renueva un minuto antes de que venza.
        this.token = {
          valor: t.token,
          vence: Date.now() + (t.expira_en - 60) * 1000,
        };
        return t.token;
      })
      .finally(() => (this.pendiente = null));
    return this.pendiente;
  }
}

const PUBLICAS = ['/api/salud', '/api/dev/'];

const conToken = (req: HttpRequest<unknown>, token: string) =>
  req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });

/** Agrega el token a las llamadas a /api y, si el backend responde 401, lo renueva una vez. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.startsWith('/api/') || PUBLICAS.some((p) => req.url.startsWith(p))) return next(req);
  const sesion = inject(SesionDev);
  return from(sesion.obtener()).pipe(
    switchMap((token) => next(conToken(req, token))),
    catchError((e) =>
      e instanceof HttpErrorResponse && e.status === 401
        ? from(sesion.obtener(true)).pipe(switchMap((token) => next(conToken(req, token))))
        : throwError(() => e),
    ),
  );
};
