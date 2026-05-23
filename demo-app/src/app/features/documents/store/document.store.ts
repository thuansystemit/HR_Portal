import { Injectable, computed, inject, signal } from '@angular/core';
import { Subscription, interval } from 'rxjs';
import { AppDocument, AppDocumentDto } from '../models/document.model';
import { DocumentApi } from '../services/document.api';

@Injectable({ providedIn: 'root' })
export class DocumentStore {
  private readonly api = inject(DocumentApi);

  private readonly _documents       = signal<AppDocument[]>([]);
  private readonly _loading         = signal(false);
  private readonly _saving          = signal(false);
  private readonly _error           = signal<string | null>(null);
  private _activeCategoryId: string | null = null;
  private _pollSub: Subscription | null = null;

  readonly documents = this._documents.asReadonly();
  readonly loading   = this._loading.asReadonly();
  readonly saving    = this._saving.asReadonly();
  readonly error     = this._error.asReadonly();

  readonly hasActiveExtraction = computed(() =>
    this._documents().some(d =>
      d.extractionStatus === 'PENDING' || d.extractionStatus === 'PROCESSING'
    )
  );

  loadByCategory(categoryId: string): void {
    this._stopPoll();
    this._activeCategoryId = categoryId;
    this._loading.set(true);
    this._error.set(null);
    this.api.getByCategory(categoryId).subscribe({
      next: docs => {
        this._documents.set(docs);
        this._loading.set(false);
        this._managePoll();
      },
      error: err => { this._error.set(err.message); this._loading.set(false); },
    });
  }

  create(dto: AppDocumentDto, onSuccess?: () => void, onError?: () => void): void {
    this._saving.set(true);
    this._error.set(null);
    this.api.create(dto).subscribe({
      next: doc => {
        this._documents.update(list => [...list, doc]);
        this._saving.set(false);
        this._managePoll();
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

  retryExtraction(documentId: string): void {
    this.api.retryExtraction(documentId).subscribe({
      next: () => {
        this._documents.update(list =>
          list.map(d => d.id === documentId
            ? { ...d, extractionStatus: 'PENDING' as const, extractionError: null }
            : d
          )
        );
        this._managePoll();
      },
    });
  }

  download(doc: AppDocument): void {
    this.api.download(doc);
  }

  private _managePoll(): void {
    if (this.hasActiveExtraction()) {
      if (!this._pollSub) {
        this._pollSub = interval(5000).subscribe(() => this._silentRefresh());
      }
    } else {
      this._stopPoll();
    }
  }

  private _silentRefresh(): void {
    if (!this._activeCategoryId) return;
    this.api.getByCategory(this._activeCategoryId).subscribe({
      next: docs => {
        this._documents.set(docs);
        this._managePoll();
      },
    });
  }

  private _stopPoll(): void {
    this._pollSub?.unsubscribe();
    this._pollSub = null;
  }
}
