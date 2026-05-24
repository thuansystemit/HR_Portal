import { Injectable, inject, signal } from '@angular/core';
import { Observable } from 'rxjs';
import { tap } from 'rxjs/operators';
import { HiringRequestApi } from '../services/hiring-request.api';
import { HiringRequest, CreateHiringRequestDto } from '../models/hiring-request.model';

@Injectable({ providedIn: 'root' })
export class HiringRequestStore {
  private readonly api = inject(HiringRequestApi);

  // -- State signals --
  private readonly _requests = signal<HiringRequest[]>([]);
  private readonly _loading  = signal(false);
  private readonly _error    = signal<string | null>(null);
  private readonly _saving   = signal(false);

  // -- Public readonly signals --
  readonly requests = this._requests.asReadonly();
  readonly loading  = this._loading.asReadonly();
  readonly error    = this._error.asReadonly();
  readonly saving   = this._saving.asReadonly();

  loadAll(): void {
    this._loading.set(true);
    this._error.set(null);
    this.api.listAll().subscribe({
      next: items => {
        this._requests.set(items);
        this._loading.set(false);
      },
      error: err => {
        this._error.set(err.error?.message || err.message || 'Failed to load hiring requests');
        this._loading.set(false);
      },
    });
  }

  loadMine(): void {
    this._loading.set(true);
    this._error.set(null);
    this.api.listMine().subscribe({
      next: items => {
        this._requests.set(items);
        this._loading.set(false);
      },
      error: err => {
        this._error.set(err.error?.message || err.message || 'Failed to load your hiring requests');
        this._loading.set(false);
      },
    });
  }

  create(dto: CreateHiringRequestDto): Observable<HiringRequest> {
    this._saving.set(true);
    this._error.set(null);
    return this.api.create(dto).pipe(
      tap({
        next: () => {
          this._saving.set(false);
          this.loadAll();
        },
        error: err => {
          this._error.set(err.error?.message || err.message || 'Failed to create hiring request');
          this._saving.set(false);
        },
      }),
    );
  }

  clearError(): void {
    this._error.set(null);
  }
}
