import { Injectable, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class LayoutService {
  private readonly _mobileOpen  = signal(false);
  private readonly _collapsed   = signal(false);

  readonly mobileOpen = this._mobileOpen.asReadonly();
  readonly collapsed  = this._collapsed.asReadonly();

  toggle(): void {
    this._mobileOpen.update(v => !v);
    this._collapsed.update(v => !v);
  }

  close(): void { this._mobileOpen.set(false); }
}
