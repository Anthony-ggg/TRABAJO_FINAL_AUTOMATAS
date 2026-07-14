import { Injectable, signal } from '@angular/core';
import { CompileResult } from './compiler.service';

/** Entrada del historial de consultas. */
export interface EntradaHistorial {
  consulta: string;
  exitoso: boolean;
  fecha: number;
}

/**
 * Estado compartido entre la seccion Normal y la Avanzada.
 *
 * Antes toda la aplicacion vivia en un unico componente, asi que el estado era local.
 * Al separar las vistas, la ultima compilacion debe sobrevivir a la navegacion: el
 * usuario compila en Normal y puede pasar a Avanzada a ver los tokens, el AST y las
 * metricas de ESA misma compilacion.
 */
@Injectable({ providedIn: 'root' })
export class CompilerStateService {

  private static readonly CLAVE_HISTORIAL = 'compilador.historial';
  private static readonly MAX_HISTORIAL = 10;

  readonly resultado = signal<CompileResult | null>(null);
  readonly consulta = signal<string>('');
  readonly historial = signal<EntradaHistorial[]>(this.cargarHistorial());

  registrar(consulta: string, resultado: CompileResult): void {
    this.consulta.set(consulta);
    this.resultado.set(resultado);
    this.agregarAlHistorial(consulta, resultado.exitoso);
  }

  private agregarAlHistorial(consulta: string, exitoso: boolean): void {
    const sinDuplicado = this.historial().filter(e => e.consulta !== consulta);
    const actualizado = [{ consulta, exitoso, fecha: Date.now() }, ...sinDuplicado]
      .slice(0, CompilerStateService.MAX_HISTORIAL);

    this.historial.set(actualizado);
    this.guardarHistorial(actualizado);
  }

  limpiarHistorial(): void {
    this.historial.set([]);
    this.guardarHistorial([]);
  }

  private cargarHistorial(): EntradaHistorial[] {
    try {
      const crudo = localStorage.getItem(CompilerStateService.CLAVE_HISTORIAL);
      return crudo ? JSON.parse(crudo) : [];
    } catch {
      // localStorage puede no estar disponible o contener datos corruptos: no es critico.
      return [];
    }
  }

  private guardarHistorial(historial: EntradaHistorial[]): void {
    try {
      localStorage.setItem(CompilerStateService.CLAVE_HISTORIAL, JSON.stringify(historial));
    } catch {
      // Sin persistencia el historial sigue funcionando en memoria.
    }
  }
}
