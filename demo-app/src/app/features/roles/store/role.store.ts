import { Injectable, computed, inject, signal } from '@angular/core';
import { Role, RoleDto } from '../models/role.model';
import { RoleApi } from '../services/role.api';

@Injectable({ providedIn: 'root' })
export class RoleStore {
  private readonly api = inject(RoleApi);

  private readonly _roles    = signal<Role[]>([]);
  private readonly _selected = signal<Role | null>(null);
  private readonly _loading  = signal(false);
  private readonly _saving   = signal(false);
  private readonly _error    = signal<string | null>(null);

  readonly roles    = this._roles.asReadonly();
  readonly selected = this._selected.asReadonly();
  readonly loading  = this._loading.asReadonly();
  readonly saving   = this._saving.asReadonly();
  readonly error    = this._error.asReadonly();
  readonly total    = computed(() => this._roles().length);

  loadAll(): void {
    this._loading.set(true);
    this._error.set(null);
    this.api.getAll().subscribe({
      next:  roles => { this._roles.set(roles);  this._loading.set(false); },
      error: err   => { this._error.set(err.message); this._loading.set(false); },
    });
  }

  loadById(id: string): void {
    this._loading.set(true);
    this.api.getById(id).subscribe({
      next:  role => { this._selected.set(role ?? null); this._loading.set(false); },
      error: err  => { this._error.set(err.message); this._loading.set(false); },
    });
  }

  create(dto: RoleDto, onSuccess?: () => void, onError?: () => void): void {
    this._saving.set(true);
    this._error.set(null);
    this.api.create(dto).subscribe({
      next: role => {
        this._roles.update(list => [...list, role]);
        this._saving.set(false);
        onSuccess?.();
      },
      error: err => { this._error.set(err.message); this._saving.set(false); onError?.(); },
    });
  }

  update(id: string, dto: RoleDto, onSuccess?: () => void, onError?: () => void): void {
    this._saving.set(true);
    this._error.set(null);
    this.api.update(id, dto).subscribe({
      next: updated => {
        this._roles.update(list => list.map(r => r.id === id ? updated : r));
        this._saving.set(false);
        onSuccess?.();
      },
      error: err => { this._error.set(err.message); this._saving.set(false); onError?.(); },
    });
  }

  remove(id: string): void {
    this.api.remove(id).subscribe({
      next: () => this._roles.update(list => list.filter(r => r.id !== id)),
    });
  }

  clearSelected(): void { this._selected.set(null); }
}
