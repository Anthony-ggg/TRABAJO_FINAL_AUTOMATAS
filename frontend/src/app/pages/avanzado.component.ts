import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { IconComponent } from '../shared/icon.component';
import { CompilerStateService } from '../compiler-state.service';
import { Automata, CompileResult, CompilerService, Metadata } from '../compiler.service';

type Pestana = 'lexico' | 'sintactico' | 'semantico' | 'metricas' | 'compilador';

/**
 * Seccion Avanzada: toda la informacion tecnica del compilador.
 *
 * Recoge lo que antes aparecia mezclado en la unica pantalla (tokens, AST, tabla de
 * simbolos, observaciones, metadata) y lo organiza por fase del compilador. Anade lo
 * que la nueva arquitectura hace observable: la conciliacion LLM/AFD, el origen del AST
 * y la definicion del automata.
 */
@Component({
  selector: 'app-avanzado',
  standalone: true,
  imports: [CommonModule, RouterLink, IconComponent],
  templateUrl: './avanzado.component.html',
  styleUrl: './avanzado.component.css'
})
export class AvanzadoComponent implements OnInit {

  private readonly compilerService = inject(CompilerService);
  readonly estado = inject(CompilerStateService);

  readonly pestana = signal<Pestana>('lexico');

  metadata: Metadata | null = null;
  automata: Automata | null = null;
  gramatica = '';

  ngOnInit(): void {
    this.compilerService.getMetadata().subscribe({ next: d => (this.metadata = d) });
    this.compilerService.getAutomata().subscribe({ next: d => (this.automata = d) });
    this.compilerService.getGrammar().subscribe({ next: d => (this.gramatica = d.gramatica) });
  }

  get resultado(): CompileResult | null {
    return this.estado.resultado();
  }

  claseToken(tipo: string): string {
    switch (tipo) {
      case 'IDENTIFICADOR': return 'token-identifier';
      case 'NUMERO':        return 'token-number';
      case 'CADENA':        return 'token-string';
      case 'OPERADOR':      return 'token-operator';
      case 'SIMBOLO':       return 'token-symbol';
      case 'DESCONOCIDO':   return 'token-unknown';
      // El AFD emite el tipo concreto de la palabra reservada (DESDE, CUANDO, ...).
      default:              return 'token-keyword';
    }
  }

  get fases(): string[] {
    const t = this.resultado?.observaciones?.tiemposPorFase;
    return t ? Object.keys(t) : [];
  }

  anchoFase(ms: number): number {
    const tiempos = this.resultado?.observaciones?.tiemposPorFase;
    if (!tiempos) return 0;
    const max = Math.max(...Object.values(tiempos), 1);
    return (ms / max) * 100;
  }

  /** Representacion del AST en arbol de texto. */
  get astTexto(): string {
    const ast = this.resultado?.ast;
    return ast ? this.dibujarNodo(ast, '', true) : '';
  }

  private dibujarNodo(nodo: any, prefijo: string, ultimo: boolean): string {
    if (!nodo) return '';
    const conector = prefijo === '' ? '' : (ultimo ? '└── ' : '├── ');
    const prefijoHijo = prefijo === '' ? '' : (ultimo ? '    ' : '│   ');

    let salida = prefijo + conector + nodo.tipo
      + (nodo.valor && nodo.valor !== nodo.tipo ? ': ' + nodo.valor : '') + '\n';

    const hijos = nodo.hijos || [];
    hijos.forEach((hijo: any, i: number) => {
      salida += this.dibujarNodo(hijo, prefijo + prefijoHijo, i === hijos.length - 1);
    });
    return salida;
  }

  get simbolos(): { clave: string; valor: string }[] {
    const tabla = this.resultado?.tablaSimbolos;
    if (!tabla) return [];
    return Object.entries(tabla).map(([clave, valor]) => ({
      clave,
      valor: Array.isArray(valor) || typeof valor === 'object'
        ? JSON.stringify(valor)
        : String(valor)
    }));
  }

  columnasDe(tabla: string): string[] {
    return this.metadata?.columnas?.[tabla] ?? [];
  }

  registrosDe(tabla: string): any[] {
    return this.metadata?.registros?.[tabla] ?? [];
  }

  /** Transiciones agrupadas por estado de origen, para leer la tabla del AFD. */
  get transicionesPorEstado(): { estado: string; transiciones: { simbolo: string; destino: string }[] }[] {
    if (!this.automata) return [];

    const mapa = new Map<string, { simbolo: string; destino: string }[]>();
    for (const t of this.automata.transiciones) {
      if (!mapa.has(t.origen)) mapa.set(t.origen, []);
      mapa.get(t.origen)!.push({ simbolo: t.simbolo, destino: t.destino });
    }
    return [...mapa.entries()].map(([estado, transiciones]) => ({ estado, transiciones }));
  }

  tokenDeEstado(estado: string): string | null {
    return this.automata?.estadosFinales.find(f => f.estado === estado)?.token ?? null;
  }
}
