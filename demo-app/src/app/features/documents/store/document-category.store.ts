import { Injectable, computed, inject, signal } from '@angular/core';
import { DocumentCategory, DocumentCategoryDto } from '../models/document.model';
import { DocumentCategoryApi } from '../services/document-category.api';

@Injectable({ providedIn: 'root' })
export class DocumentCategoryStore {
  private readonly api = inject(DocumentCategoryApi);

  private readonly _categories = signal<DocumentCategory[]>([]);
  private readonly _selected   = signal<DocumentCategory | null>(null);
  private readonly _loading    = signal(false);
  private readonly _saving     = signal(false);
  private readonly _error      = signal<string | null>(null);

  readonly categories = this._categories.asReadonly();
  readonly selected   = this._selected.asReadonly();
  readonly loading    = this._loading.asReadonly();
  readonly saving     = this._saving.asReadonly();
  readonly error      = this._error.asReadonly();
  readonly total      = computed(() => this._categories().length);

  loadAll(): void {
    this._loading.set(true);
    this._error.set(null);
    this.api.getAll().subscribe({
      next:  cats => { this._categories.set(cats); this._loading.set(false); },
      error: err  => { this._error.set(err.message); this._loading.set(false); },
    });
  }

  loadById(id: string): void {
    this._loading.set(true);
    this.api.getById(id).subscribe({
      next:  cat => { this._selected.set(cat ?? null); this._loading.set(false); },
      error: err => { this._error.set(err.message); this._loading.set(false); },
    });
  }

  create(dto: DocumentCategoryDto, onSuccess?: () => void, onError?: () => void): void {
    this._saving.set(true);
    this._error.set(null);
    this.api.create(dto).subscribe({
      next: cat => {
        this._categories.update(list => [...list, cat]);
        this._saving.set(false);
        onSuccess?.();
      },
      error: err => { this._error.set(err.message); this._saving.set(false); onError?.(); },
    });
  }

  update(id: string, dto: DocumentCategoryDto, onSuccess?: () => void, onError?: () => void): void {
    this._saving.set(true);
    this._error.set(null);
    this.api.update(id, dto).subscribe({
      next: updated => {
        this._categories.update(list => list.map(c => c.id === id ? updated : c));
        this._saving.set(false);
        onSuccess?.();
      },
      error: err => { this._error.set(err.message); this._saving.set(false); onError?.(); },
    });
  }

  remove(id: string): void {
    this.api.remove(id).subscribe({
      next: () => this._categories.update(list => list.filter(c => c.id !== id)),
    });
  }

  clearSelected(): void { this._selected.set(null); }
}
