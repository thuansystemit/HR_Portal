import { Injectable, inject, signal } from '@angular/core';
import { AppDocument, AppDocumentDto } from '../models/document.model';
import { DocumentApi } from '../services/document.api';

@Injectable({ providedIn: 'root' })
export class DocumentStore {
  private readonly api = inject(DocumentApi);

  private readonly _documents = signal<AppDocument[]>([]);
  private readonly _loading   = signal(false);
  private readonly _saving    = signal(false);
  private readonly _error     = signal<string | null>(null);

  readonly documents = this._documents.asReadonly();
  readonly loading   = this._loading.asReadonly();
  readonly saving    = this._saving.asReadonly();
  readonly error     = this._error.asReadonly();

  loadByCategory(categoryId: string): void {
    this._loading.set(true);
    this._error.set(null);
    this.api.getByCategory(categoryId).subscribe({
      next:  docs => { this._documents.set(docs); this._loading.set(false); },
      error: err  => { this._error.set(err.message); this._loading.set(false); },
    });
  }

  create(dto: AppDocumentDto, onSuccess?: () => void, onError?: () => void): void {
    this._saving.set(true);
    this._error.set(null);
    this.api.create(dto).subscribe({
      next: doc => {
        this._documents.update(list => [...list, doc]);
        this._saving.set(false);
        onSuccess?.();
      },
      error: err => { this._error.set(err.message); this._saving.set(false); onError?.(); },
    });
  }

  remove(id: string): void {
    const doc = this._documents().find(d => d.id === id);
    if (!doc) return;
    this.api.remove(doc.categoryId, id).subscribe({
      next: () => this._documents.update(list => list.filter(d => d.id !== id)),
    });
  }

  download(doc: AppDocument): void {
    this.api.download(doc);
  }
}
