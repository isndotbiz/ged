/** Common interface for both Tauri Database and WebDatabase */
export interface PlatformDatabase {
  select<T>(query: string, bindValues?: unknown[]): Promise<T>;
  execute(query: string, bindValues?: unknown[]): Promise<{ rowsAffected: number; lastInsertId?: number }>;
}

export interface FileAdapter {
  pickFile(filters?: { name: string; extensions: string[] }[]): Promise<{ name: string; data: Uint8Array } | null>;
  saveFile(data: Uint8Array, suggestedName: string): Promise<void>;
  readDir?(path: string): Promise<string[]>;
}

export interface FetchAdapter {
  fetch(url: string, options?: RequestInit): Promise<Response>;
}

export interface NotificationAdapter {
  notify(title: string, body: string): void;
  announce(message: string): void;
}

export interface PlatformCapabilities {
  platform: 'desktop' | 'android' | 'web' | 'chrome-ext';
  hasFilesystem: boolean;
  hasNativeDialog: boolean;
  hasCamera: boolean;
  hasShareTarget: boolean;
  hasFaceDetection: boolean;
}

export interface PlatformAdapter {
  db: PlatformDatabase;
  fetch: FetchAdapter;
  fs: FileAdapter;
  notifications: NotificationAdapter;
  capabilities: PlatformCapabilities;
}
