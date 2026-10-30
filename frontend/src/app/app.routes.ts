import { Routes } from '@angular/router';

import { rutasDeModulos } from './modulos.routes';

// Los módulos de negocio se registran en modulos.routes.ts (una línea por módulo); este archivo no se toca.
export const routes: Routes = [...rutasDeModulos];
