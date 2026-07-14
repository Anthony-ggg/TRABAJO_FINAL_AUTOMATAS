import { Routes } from '@angular/router';

/**
 * Las dos secciones de la aplicacion.
 *
 *  /compilador : seccion Normal, para usar el compilador (editor, resultado, ayuda, historial).
 *  /avanzado   : seccion Avanzada, con todo el detalle tecnico de cada fase.
 *
 * Se cargan de forma diferida para que la vista Normal, que es la habitual, no arrastre
 * el peso de la vista tecnica.
 */
export const routes: Routes = [
  { path: '', redirectTo: 'compilador', pathMatch: 'full' },
  {
    path: 'compilador',
    title: 'Mini-Compilador SQL',
    loadComponent: () =>
      import('./pages/compilador.component').then(m => m.CompiladorComponent)
  },
  {
    path: 'avanzado',
    title: 'Analisis tecnico - Mini-Compilador SQL',
    loadComponent: () =>
      import('./pages/avanzado.component').then(m => m.AvanzadoComponent)
  },
  { path: '**', redirectTo: 'compilador' }
];
