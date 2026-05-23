import { Injectable, inject, signal } from '@angular/core';
import { InvoiceRecord } from '../models/invoice-record.model';
import { InvoiceRecordApi } from '../services/invoice-record.api';

@Injectable({ providedIn: 'root' })
export class InvoiceRecordStore {
  private readonly api = inject(InvoiceRecordApi);

  private readonly _records  = signal<InvoiceRecord[]>([]);
  private readonly _selected = signal<InvoiceRecord | null>(null);
  private readonly _loading  = signal(false);
  private readonly _error    = signal<string | null>(null);

  readonly records  = this._records.asReadonly();
  readonly selected = this._selected.asReadonly();
  readonly loading  = this._loading.asReadonly();
  readonly error    = this._error.asReadonly();

  loadByCategory(categoryId: string): void {
    this._loading.set(true);
    this._error.set(null);
    this.api.listByCategory(categoryId).subscribe({
      next:  data => { this._records.set(data); this._loading.set(false); },
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

  remove(id: string): void {
    this.api.remove(id).subscribe({
      next: () => this._records.update(list => list.filter(r => r.id !== id)),
      error: err => this._error.set(err.message),
    });
  }

  clearSelected(): void { this._selected.set(null); }
}
