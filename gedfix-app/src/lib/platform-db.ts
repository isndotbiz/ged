import initSqlJs, { type Database as SqlJsDatabase } from 'sql.js';
import type { PlatformDatabase } from './platform';

export class WebDatabase implements PlatformDatabase {
  private db: SqlJsDatabase;

  constructor(db: SqlJsDatabase) {
    this.db = db;
  }

  async select<T>(query: string, bindValues?: unknown[]): Promise<T> {
    // sql.js uses ? positional params, Tauri plugin-sql uses $1,$2,...
    const converted = query.replace(/\$(\d+)/g, '?');

    const stmt = this.db.prepare(converted);
    if (bindValues) {
      stmt.bind(bindValues as any[]);
    }
    const results: any[] = [];
    while (stmt.step()) {
      results.push(stmt.getAsObject());
    }
    stmt.free();
    return results as T;
  }

  async execute(query: string, bindValues?: unknown[]): Promise<{ rowsAffected: number; lastInsertId?: number }> {
    const converted = query.replace(/\$(\d+)/g, '?');
    if (bindValues) {
      this.db.run(converted, bindValues as any[]);
    } else {
      this.db.run(converted);
    }
    this.saveToIndexedDB();
    // sql.js doesn't expose last_insert_rowid directly, so we query it
    const stmt = this.db.prepare('SELECT last_insert_rowid() as id');
    let lastInsertId: number | undefined;
    if (stmt.step()) {
      lastInsertId = stmt.getAsObject().id as number;
    }
    stmt.free();
    return { rowsAffected: this.db.getRowsModified(), lastInsertId };
  }

  private async saveToIndexedDB() {
    // Persist to IndexedDB so data survives page reload
    const data = this.db.export();
    const blob = new Blob([data]);
    const request = indexedDB.open('gedfix', 1);
    request.onupgradeneeded = () => {
      request.result.createObjectStore('db');
    };
    request.onsuccess = () => {
      const tx = request.result.transaction('db', 'readwrite');
      tx.objectStore('db').put(blob, 'main');
    };
  }
}

export async function loadWebDatabase(): Promise<WebDatabase> {
  const SQL = await initSqlJs({
    locateFile: (file: string) => `https://sql.js.org/dist/${file}`,
  });

  // Try loading from IndexedDB first
  try {
    const data = await new Promise<ArrayBuffer | null>((resolve) => {
      const request = indexedDB.open('gedfix', 1);
      request.onupgradeneeded = () => {
        request.result.createObjectStore('db');
      };
      request.onsuccess = () => {
        const tx = request.result.transaction('db', 'readonly');
        const get = tx.objectStore('db').get('main');
        get.onsuccess = () => {
          if (get.result) {
            const reader = new FileReader();
            reader.onload = () => resolve(reader.result as ArrayBuffer);
            reader.readAsArrayBuffer(get.result);
          } else {
            resolve(null);
          }
        };
        get.onerror = () => resolve(null);
      };
      request.onerror = () => resolve(null);
    });

    if (data) {
      const db = new SQL.Database(new Uint8Array(data));
      return new WebDatabase(db);
    }
  } catch {
    // Fresh start
  }

  const db = new SQL.Database();
  return new WebDatabase(db);
}
