import { Routes } from '@angular/router';

// Registro de módulos: UNA línea por módulo, con coma final, sin tocar las líneas de los demás.
// Se fusiona con merge=union (.gitattributes): dos ramas que agregan su línea no chocan.
// Ejemplo: { path: 'vacaciones', loadChildren: () => import('./vacaciones/vacaciones.routes').then((m) => m.routes) },
// prettier-ignore
export const rutasDeModulos: Routes = [
];
