import { getDb } from './db';

export interface UndoAction {
  id: string;
  description: string;
  timestamp: string;
  undo: () => Promise<void>;
  redo: () => Promise<void>;
}

class UndoManager {
  private undoStack: UndoAction[] = [];
  private redoStack: UndoAction[] = [];
  private maxSize = 50;

  async push(action: UndoAction, metadata?: { tableName?: string; rowId?: string; oldData?: unknown; newData?: unknown }): Promise<void> {
    this.undoStack.push(action);
    this.redoStack = [];
    if (this.undoStack.length > this.maxSize) this.undoStack.shift();

    const d = await getDb();
    await d.execute(
      `INSERT OR REPLACE INTO undo_log (id, description, tableName, rowId, oldData, newData, createdAt)
       VALUES ($1,$2,$3,$4,$5,$6,$7)`,
      [
        action.id,
        action.description,
        metadata?.tableName || 'unknown',
        metadata?.rowId || action.id,
        JSON.stringify(metadata?.oldData ?? null),
        JSON.stringify(metadata?.newData ?? null),
        action.timestamp,
      ]
    );
    await d.execute(
      `DELETE FROM undo_log WHERE id NOT IN (SELECT id FROM undo_log ORDER BY createdAt DESC LIMIT 50)`
    );
  }

  async undo(): Promise<UndoAction | null> {
    const action = this.undoStack.pop() ?? null;
    if (!action) return null;
    await action.undo();
    this.redoStack.push(action);
    const d = await getDb();
    await d.execute(`DELETE FROM undo_log WHERE id = $1`, [action.id]);
    return action;
  }

  async redo(): Promise<UndoAction | null> {
    const action = this.redoStack.pop() ?? null;
    if (!action) return null;
    await action.redo();
    this.undoStack.push(action);
    return action;
  }

  canUndo(): boolean {
    return this.undoStack.length > 0;
  }

  canRedo(): boolean {
    return this.redoStack.length > 0;
  }

  getUndoStack(): UndoAction[] {
    return [...this.undoStack];
  }

  getRedoStack(): UndoAction[] {
    return [...this.redoStack];
  }

  clear(): void {
    this.undoStack = [];
    this.redoStack = [];
  }
}

export const undoManager = new UndoManager();
