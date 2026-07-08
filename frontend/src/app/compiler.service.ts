import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface Token {
  tipo: string;
  valor: string;
  origen: string;
  posicion: number;
}

export interface ASTNode {
  tipo: string;
  valor: string;
  hijos: ASTNode[];
}

export interface Observaciones {
  tokensAFD: string[];
  tokensLLM: string[];
  hilosConcurrentes: number;
  tiempoTotalLexico: number;
  tiempoTotalCompilacion: number;
  tiemposPorFase: { [key: string]: number };
}

export interface CompileResult {
  exitoso: boolean;
  mensaje: string;
  tokens: Token[];
  ast: ASTNode;
  tablaSimbolos: any;
  fraseOriginal: string;
  observaciones: Observaciones;
  errores: string[];
  filasResultado?: any[];
  mensajeEjecucion?: string;
}

export interface Metadata {
  tablas: string[];
  columnas: { [key: string]: string[] };
  registros: { [key: string]: any[] };
  ejemplos: string[];
  ejemplos_invalidos: string[];
}

@Injectable({ providedIn: 'root' })
export class CompilerService {
  private apiUrl = 'http://localhost:8080/api/compiler';

  constructor(private http: HttpClient) {}

  compile(query: string): Observable<CompileResult> {
    return this.http.post<CompileResult>(`${this.apiUrl}/compile`, { query });
  }

  getMetadata(): Observable<Metadata> {
    return this.http.get<Metadata>(`${this.apiUrl}/metadata`);
  }
}
