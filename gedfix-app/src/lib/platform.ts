export function isTauri(): boolean {
  return typeof window !== 'undefined' && '__TAURI_INTERNALS__' in window;
}

export function isWeb(): boolean {
  return !isTauri();
}

/** Common interface for both Tauri Database and WebDatabase */
export interface PlatformDatabase {
  select<T>(query: string, bindValues?: unknown[]): Promise<T>;
  execute(query: string, bindValues?: unknown[]): Promise<{ rowsAffected: number; lastInsertId?: number }>;
}
