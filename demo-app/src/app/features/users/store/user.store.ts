import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { User, UserDto } from '../models/user.model';
import { UserApi } from '../services/user.api';

@Injectable({ providedIn: 'root' })
export class UserStore {
  private readonly api    = inject(UserApi);
  private readonly router = inject(Router);

  private readonly _users    = signal<User[]>([]);
  private readonly _selected = signal<User | null>(null);
  private readonly _loading  = signal(false);
  private readonly _saving   = signal(false);
  private readonly _error    = signal<string | null>(null);
  private readonly _total    = signal(0);

  readonly users    = this._users.asReadonly();
  readonly selected = this._selected.asReadonly();
  readonly loading  = this._loading.asReadonly();
  readonly saving   = this._saving.asReadonly();
  readonly error    = this._error.asReadonly();
  readonly total    = computed(() => this._total());

  loadAll(page = 0, size = 100): void {
    this._loading.set(true);
    this._error.set(null);
    this.api.getAll(page, size).subscribe({
      next:  res  => { this._users.set(res.users); this._total.set(res.total); this._loading.set(false); },
      error: err  => { this._error.set(err.message); this._loading.set(false); },
    });
  }

  loadById(id: string): void {
    this._loading.set(true);
    this.api.getById(id).subscribe({
      next:  user => { this._selected.set(user ?? null); this._loading.set(false); },
      error: err  => { this._error.set(err.message); this._loading.set(false); },
    });
  }

  create(dto: UserDto, onSuccess?: () => void, onError?: () => void): void {
    this._saving.set(true);
    this._error.set(null);
    this.api.create(dto).subscribe({
      next: user => {
        this._users.update(list => [...list, user]);
        this._saving.set(false);
        onSuccess ? onSuccess() : this.router.navigate(['/users']);
      },
      error: err => { this._error.set(err.message); this._saving.set(false); onError?.(); },
    });
  }

  update(id: string, dto: UserDto, onSuccess?: () => void, onError?: () => void): void {
    this._saving.set(true);
    this._error.set(null);
    this.api.update(id, dto).subscribe({
      next: updated => {
        this._users.update(list => list.map(u => u.id === id ? updated : u));
        this._saving.set(false);
        onSuccess ? onSuccess() : this.router.navigate(['/users']);
      },
      error: err => { this._error.set(err.message); this._saving.set(false); onError?.(); },
    });
  }

  remove(id: string): void {
    this.api.remove(id).subscribe({
      next: () => this._users.update(list => list.filter(u => u.id !== id)),
    });
  }

  clearSelected(): void { this._selected.set(null); }
}
