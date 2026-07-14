import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { NavbarComponent } from './shared/navbar.component';

/**
 * Shell de la aplicacion: navbar fija + area de contenido enrutada.
 *
 * Antes este componente contenia la aplicacion entera (editor, tokens, AST, metricas y
 * metadata en una sola pantalla). Ahora solo compone el marco; el contenido vive en las
 * dos secciones enrutadas (Compilador y Avanzado).
 */
@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, NavbarComponent],
  template: `
    <app-navbar />
    <main>
      <router-outlet />
    </main>
  `,
  styles: [`
    main { min-height: calc(100vh - 64px); }
  `]
})
export class AppComponent {}
