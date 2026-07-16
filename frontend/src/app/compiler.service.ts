import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface Token {
  tipo: string;
  valor: string;
  /** "AFD" (solo el automata) o "LLM+AFD" (el LLM tambien lo reconocio). */
  origen: string;
  posicion: number;
}

export interface ASTNode {
  tipo: string;
  valor: string;
  hijos: ASTNode[];
}

/** Error estructurado emitido por el compilador. */
export interface CompileError {
  fase: 'LEXICO' | 'SINTACTICO' | 'SEMANTICO' | 'EJECUCION';
  codigo: string;
  mensaje: string;
  tokenAfectado?: string;
  posicion: number;
  longitud: number;
  sugerencia?: string;
  severidad: string;
}

export interface Observaciones {
  tokensAFD: string[];
  tokensLLM: string[];
  hilosConcurrentes: number;
  tiempoTotalLexico: number;
  tiempoTotalCompilacion: number;
  tiemposPorFase: { [key: string]: number };

  /** Conciliacion entre la tokenizacion del LLM y la del AFD. */
  tokensConfirmados: number;
  tokensSoloAFD: string[];
  tokensAlucinados: string[];
  discrepanciasTipo: string[];
  coincidenciaLexica: number;
  llmDisponible: boolean;

  /** Origen del AST: "LLM" o "PARSER_DETERMINISTA". */
  origenAst: string;
  motivoRespaldoAst?: string;
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
  erroresDetallados: CompileError[];
  filasResultado?: any[];
  mensajeEjecucion?: string;
}

/** Explicacion didactica producida por el agente tutor. */
export interface Explicacion {
  explicacion: string;
  causa?: string;
  correccion?: string;
  ejemploCorrecto?: string;
  pasos: string[];
  /** "LLM" si la genero el modelo; "COMPILADOR" si es la de respaldo. */
  origen: string;
}

export interface Metadata {
  tablas: string[];
  columnas: { [key: string]: string[] };
  registros: { [key: string]: any[] };
  ejemplos: string[];
  ejemplos_invalidos: string[];
}

/** Definicion del AFD, para poder auditarlo desde la vista Avanzada. */
export interface Automata {
  estadoInicial: string;
  alfabeto: string[];
  estados: string[];
  estadosFinales: { estado: string; token: string }[];
  transiciones: { origen: string; simbolo: string; destino: string }[];
  palabrasReservadas: string[];
}

@Injectable({ providedIn: 'root' })
export class CompilerService {
  private apiUrl = 'http://localhost:8080/api/compiler';

  constructor(private http: HttpClient) {}

  compile(query: string): Observable<CompileResult> {
    return this.http.post<CompileResult>(`${this.apiUrl}/compile`, { query });
  }

  /**
   * Pide al agente tutor que explique errores que el compilador YA detecto.
   * El LLM no reanaliza la consulta: solo traduce el diagnostico a lenguaje llano.
   */
  explainError(query: string, errores: CompileError[]): Observable<Explicacion> {
    return this.http.post<Explicacion>(`${this.apiUrl}/explain-error`, { query, errores });
  }

  getMetadata(): Observable<Metadata> {
    return this.http.get<Metadata>(`${this.apiUrl}/metadata`);
  }

  getAutomata(): Observable<Automata> {
    return this.http.get<Automata>(`${this.apiUrl}/automata`);
  }

  getGrammar(): Observable<{ gramatica: string }> {
    return this.http.get<{ gramatica: string }>(`${this.apiUrl}/grammar`);
  }
}
