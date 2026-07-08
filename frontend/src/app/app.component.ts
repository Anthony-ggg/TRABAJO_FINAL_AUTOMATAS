import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CompilerService, CompileResult, Metadata } from './compiler.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent implements OnInit {
  query = '';
  result: CompileResult | null = null;
  metadata: Metadata | null = null;
  loading = false;
  activeTab: 'result' | 'tokens' | 'ast' | 'observaciones' | 'records' = 'result';

  constructor(private compilerService: CompilerService) {}

  ngOnInit() {
    this.loadMetadata();
  }

  loadMetadata() {
    this.compilerService.getMetadata().subscribe({
      next: (data) => { this.metadata = data; },
      error: (err) => { console.error('Error loading metadata:', err); }
    });
  }

  compile() {
    if (!this.query.trim()) return;
    this.loading = true;
    this.result = null;
    this.compilerService.compile(this.query).subscribe({
      next: (data) => {
        this.result = data;
        this.loading = false;
        this.activeTab = 'result';
        this.loadMetadata();
      },
      error: (err) => {
        this.result = {
          exitoso: false,
          mensaje: 'Error de conexión con el servidor',
          tokens: [],
          ast: null as any,
          tablaSimbolos: null,
          fraseOriginal: this.query,
          observaciones: null as any,
          errores: ['No se pudo conectar con el backend']
        };
        this.loading = false;
      }
    });
  }

  setExample(query: string) {
    this.query = query;
    this.compile();
  }

  getTokenClass(tipo: string): string {
    switch (tipo) {
      case 'PALABRA_RESERVADA': return 'token-keyword';
      case 'IDENTIFICADOR': return 'token-identifier';
      case 'NUMERO': return 'token-number';
      case 'CADENA': return 'token-string';
      case 'OPERADOR': return 'token-operator';
      case 'SIMBOLO': return 'token-symbol';
      default: return 'token-unknown';
    }
  }

  formatJson(obj: any): string {
    return JSON.stringify(obj, null, 2);
  }

  getFaseKeys(): string[] {
    if (!this.result?.observaciones?.tiemposPorFase) return [];
    return Object.keys(this.result.observaciones.tiemposPorFase);
  }

  getFaseWidth(ms: number): number {
    if (!this.result?.observaciones?.tiemposPorFase) return 0;
    const max = Math.max(...Object.values(this.result.observaciones.tiemposPorFase), 1);
    return (ms / max) * 100;
  }

  isArray(val: any): boolean { return Array.isArray(val); }
  isObject(val: any): boolean { return val !== null && typeof val === 'object' && !Array.isArray(val); }

  getColumnasTabla(tabla: string): string[] {
    return this.metadata?.columnas?.[tabla] || [];
  }

  getRegistrosTabla(tabla: string): any[] {
    return this.metadata?.registros?.[tabla] || [];
  }

  astToTreeString(node: any, prefix: string = '', isLast: boolean = true): string {
    if (!node) return '';
    const connector = isLast ? '└── ' : '├── ';
    const childPrefix = isLast ? '    ' : '│   ';
    let result = prefix + connector + node.tipo + (node.valor ? ': ' + node.valor : '') + '\n';
    const children = node.hijos || [];
    for (let i = 0; i < children.length; i++) {
      result += this.astToTreeString(children[i], prefix + childPrefix, i === children.length - 1);
    }
    return result;
  }

  get astTree(): string {
    return this.result?.ast ? this.astToTreeString(this.result.ast) : '';
  }
}
