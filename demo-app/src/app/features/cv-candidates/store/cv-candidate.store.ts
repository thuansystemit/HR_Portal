import { Injectable, inject, signal } from '@angular/core';
import { CvCandidate } from '../models/cv-candidate.model';
import { CvCandidateApi } from '../services/cv-candidate.api';

@Injectable({ providedIn: 'root' })
export class CvCandidateStore {
  private readonly api = inject(CvCandidateApi);

  private readonly _candidates = signal<CvCandidate[]>([]);
  private readonly _selected   = signal<CvCandidate | null>(null);
  private readonly _loading    = signal(false);
  private readonly _error      = signal<string | null>(null);

  readonly candidates = this._candidates.asReadonly();
  readonly selected   = this._selected.asReadonly();
  readonly loading    = this._loading.asReadonly();
  readonly error      = this._error.asReadonly();

  loadByCategory(categoryId: string): void {
    this._loading.set(true);
    this._error.set(null);
    this.api.listByCategory(categoryId).subscribe({
      next:  data => { this._candidates.set(data); this._loading.set(false); },
      error: err  => { this._error.set(err.message); this._loading.set(false); },
    });
  }

  loadById(id: string): void {
    this._loading.set(true);
    this._error.set(null);
    this.api.getById(id).subscribe({
      next:  data => { this._selected.set(data); this._loading.set(false); },
      error: err  => { this._error.set(err.message); this._loading.set(false); },
    });
  }

  loadByDocument(documentId: string): void {
    this._loading.set(true);
    this._error.set(null);
    this.api.getByDocument(documentId).subscribe({
      next:  data => { this._selected.set(data); this._loading.set(false); },
      error: err  => { this._error.set(err.message); this._loading.set(false); },
    });
  }

  remove(id: string, onSuccess?: () => void): void {
    this.api.remove(id).subscribe({
      next: () => {
        this._candidates.update(list => list.filter(c => c.id !== id));
        onSuccess?.();
      },
      error: err => this._error.set(err.message),
    });
  }

  clearSelected(): void { this._selected.set(null); }
}
