import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./exportacion-productos.component').then((m) => m.ExportacionProductosComponent),
  },
];
