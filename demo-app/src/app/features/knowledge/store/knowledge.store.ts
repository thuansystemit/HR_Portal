import { Injectable, inject, signal } from '@angular/core';
import { KnowledgeApi } from '../services/knowledge.api';
import { KnowledgeEntity, KnowledgeEntitySummary } from '../models/knowledge.model';

@Injectable({ providedIn: 'root' })
export class KnowledgeStore {
  private readonly api = inject(KnowledgeApi);

  private readonly _entities      = signal<KnowledgeEntitySummary[]>([]);
  private readonly _selected      = signal<KnowledgeEntity | null>(null);
  private readonly _loading       = signal(false);
  private readonly _error         = signal<string | null>(null);
  private readonly _totalElements = signal(0);
  private readonly _currentPage   = signal(0);

  readonly entities      = this._entities.asReadonly();
  readonly selected      = this._selected.asReadonly();
  readonly loading       = this._loading.asReadonly();
  readonly error         = this._error.asReadonly();
  readonly totalElements = this._totalElements.asReadonly();
  readonly currentPage   = this._currentPage.asReadonly();

  search(q: string, type: string, page: number): void {
    this._loading.set(true);
    this._error.set(null);
    this.api.search({ q: q || undefined, type: type || undefined, page, size: 20 }).subscribe({
      next: data => {
        this._entities.set(data.content);
        this._totalElements.set(data.totalElements);
        this._currentPage.set(data.number);
        this._loading.set(false);
      },
      error: err => {
        this._error.set(err?.error?.message ?? err?.message ?? 'Failed to load knowledge entities.');
        this._loading.set(false);
      },
    });
  }

  loadById(id: string): void {
    this._loading.set(true);
    this._error.set(null);
    this.api.getById(id).subscribe({
      next: data => {
        this._selected.set(data);
        this._loading.set(false);
      },
      error: err => {
        this._error.set(err?.error?.message ?? err?.message ?? 'Failed to load entity.');
        this._loading.set(false);
      },
    });
  }

  clearSelected(): void {
    this._selected.set(null);
  }
}
