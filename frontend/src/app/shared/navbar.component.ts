import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { IconComponent } from './icon.component';

/**
 * Navegacion principal.
 *
 * Divide la aplicacion en las dos secciones: Compilador (uso final) y Avanzado
 * (informacion tecnica). Reutiliza el gradiente de marca del encabezado original,
 * de modo que la identidad visual del proyecto se mantiene intacta.
 */
@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive, IconComponent],
  template: `
    <nav class="navbar">
      <div class="navbar-contenido">

        <a class="marca" routerLink="/compilador">
          <span class="marca-icono">
            <app-icon name="terminal" [size]="22" />
          </span>
          <span class="marca-texto">
            <strong>Mini-Compilador SQL</strong>
            <small>Consultas en espanol</small>
          </span>
        </a>

        <div class="enlaces">
          <a routerLink="/compilador" routerLinkActive="activo" class="enlace">
            <app-icon name="play" [size]="16" />
            <span>Compilador</span>
          </a>
          <a routerLink="/avanzado" routerLinkActive="activo" class="enlace">
            <app-icon name="layers" [size]="16" />
            <span>Avanzado</span>
          </a>
        </div>

      </div>
    </nav>
  `,
  styles: [`
    .navbar {
      background: var(--gradiente-marca);
      color: white;
      box-shadow: 0 2px 10px rgba(102, 126, 234, 0.25);
    }

    .navbar-contenido {
      max-width: var(--ancho-contenido);
      margin: 0 auto;
      padding: 0 20px;
      height: 64px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 20px;
    }

    .marca {
      display: flex;
      align-items: center;
      gap: 12px;
      color: white;
      text-decoration: none;
    }

    .marca-icono {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 38px;
      height: 38px;
      border-radius: var(--radio-md);
      background: rgba(255, 255, 255, 0.16);
    }

    .marca-texto { display: flex; flex-direction: column; line-height: 1.25; }
    .marca-texto strong { font-size: 1rem; font-weight: 600; }
    .marca-texto small { font-size: 0.75rem; opacity: 0.85; }

    .enlaces { display: flex; gap: 6px; }

    .enlace {
      display: flex;
      align-items: center;
      gap: 7px;
      padding: 9px 16px;
      border-radius: var(--radio-md);
      color: rgba(255, 255, 255, 0.85);
      text-decoration: none;
      font-size: 0.9rem;
      font-weight: 500;
      transition: background 0.2s, color 0.2s;
    }

    .enlace:hover { background: rgba(255, 255, 255, 0.12); color: white; }

    .enlace.activo {
      background: rgba(255, 255, 255, 0.22);
      color: white;
      font-weight: 600;
    }

    @media (max-width: 640px) {
      .marca-texto small { display: none; }
      .enlace span { display: none; }
      .enlace { padding: 9px 12px; }
    }
  `]
})
export class NavbarComponent {}
