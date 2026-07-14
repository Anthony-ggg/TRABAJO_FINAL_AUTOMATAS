import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { IconComponent } from '../shared/icon.component';
import { CompilerStateService } from '../compiler-state.service';
import { CompilerService, CompileResult, Explicacion, Metadata } from '../compiler.service';

/**
 * Seccion Normal: la vista para quien solo quiere USAR el compilador.
 *
 * Muestra editor, resultado, explicacion del error, ejemplos, ayuda e historial.
 * Deliberadamente NO muestra tokens, AST, metricas ni interioridades del compilador:
 * todo eso vive ahora en la seccion Avanzada.
 */
@Component({
  selector: 'app-compilador',
  standalone: true,
  imports: [CommonModule, FormsModule, IconComponent],
  templateUrl: './compilador.component.html',
  styleUrl: './compilador.component.css'
})
export class CompiladorComponent implements OnInit {

  private readonly compilerService = inject(CompilerService);
  readonly estado = inject(CompilerStateService);

  query = '';
  metadata: Metadata | null = null;

  readonly compilando = signal(false);
  readonly explicacion = signal<Explicacion | null>(null);
  readonly explicando = signal(false);
  readonly mostrarAyuda = signal(false);

  ngOnInit(): void {
    this.query = this.estado.consulta();
    this.compilerService.getMetadata().subscribe({
      next: data => (this.metadata = data),
      error: () => (this.metadata = null)
    });
  }

  get resultado(): CompileResult | null {
    return this.estado.resultado();
  }

  compilar(): void {
    if (!this.query.trim() || this.compilando()) return;

    this.compilando.set(true);
    this.explicacion.set(null);

    this.compilerService.compile(this.query).subscribe({
      next: resultado => {
        this.estado.registrar(this.query, resultado);
        this.compilando.set(false);

        // Ante un error, se pide al tutor que lo explique. El analisis ya lo hizo el
        // compilador: el LLM solo traduce el diagnostico a lenguaje comprensible.
        if (!resultado.exitoso && resultado.erroresDetallados?.length) {
          this.pedirExplicacion(resultado);
        }
        this.refrescarMetadata();
      },
      error: () => {
        this.estado.registrar(this.query, this.resultadoSinConexion());
        this.compilando.set(false);
      }
    });
  }

  private pedirExplicacion(resultado: CompileResult): void {
    this.explicando.set(true);
    this.compilerService.explainError(this.query, resultado.erroresDetallados).subscribe({
      next: exp => {
        this.explicacion.set(exp);
        this.explicando.set(false);
      },
      error: () => this.explicando.set(false)
    });
  }

  private refrescarMetadata(): void {
    this.compilerService.getMetadata().subscribe({
      next: data => (this.metadata = data)
    });
  }

  usarEjemplo(ejemplo: string): void {
    this.query = ejemplo;
    this.compilar();
  }

  limpiar(): void {
    this.query = '';
    this.explicacion.set(null);
  }

  get columnasResultado(): string[] {
    const filas = this.resultado?.filasResultado;
    return filas?.length ? Object.keys(filas[0]) : [];
  }

  /** Resultado sintetico cuando el backend no responde, para no dejar la vista en blanco. */
  private resultadoSinConexion(): CompileResult {
    return {
      exitoso: false,
      mensaje: 'No se pudo conectar con el servidor',
      tokens: [],
      ast: null as any,
      tablaSimbolos: null,
      fraseOriginal: this.query,
      observaciones: null as any,
      errores: ['El backend no responde. Verifica que este ejecutandose en el puerto 8086.'],
      erroresDetallados: []
    };
  }
}
