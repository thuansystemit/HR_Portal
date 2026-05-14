import { Component, computed, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, Validators } from '@angular/forms';
import { SHARED_IMPORTS } from '../../../../shared/shared.imports';
import { AuthService } from '../../../../auth/services/auth';

@Component({
  selector: 'app-profile',
  imports: [...SHARED_IMPORTS],
  templateUrl: './profile.html',
  styleUrl: './profile.scss',
})
export class Profile {
  protected readonly auth = inject(AuthService);
  private  readonly fb   = inject(FormBuilder);

  // ── Info form ───────────────────────────────────────────────────
  protected infoForm = this.fb.group({
    name: [this.auth.user()?.name ?? '', [Validators.required, Validators.minLength(2)]],
  });
  protected infoSaving  = signal(false);
  protected infoSuccess = signal(false);

  // ── Password form ───────────────────────────────────────────────
  protected pwForm = this.fb.group({
    current:  ['', Validators.required],
    next:     ['', [Validators.required, Validators.minLength(6)]],
    confirm:  ['', Validators.required],
  });
  protected pwSaving  = signal(false);
  protected pwError   = signal('');
  protected pwSuccess = signal(false);

  // ── Avatar ──────────────────────────────────────────────────────
  protected avatarUrl = computed(() => {
    const name = encodeURIComponent(this.auth.user()?.name ?? 'User');
    return `https://ui-avatars.com/api/?name=${name}&size=96&background=0d6efd&color=fff`;
  });

  // ── Role badge colour ───────────────────────────────────────────
  protected roleBadgeClass = computed(() => {
    const map: Record<string, string> = {
      Administrator: 'bg-danger',
      Manager:       'bg-warning text-dark',
      Viewer:        'bg-info text-dark',
    };
    return map[this.auth.user()?.roleName ?? ''] ?? 'bg-secondary';
  });

  // ── Actions ─────────────────────────────────────────────────────
  saveInfo(): void {
    if (this.infoForm.invalid) { this.infoForm.markAllAsTouched(); return; }
    this.infoSaving.set(true);
    this.infoSuccess.set(false);
    this.auth.updateName(this.infoForm.getRawValue().name!.trim());
    this.infoSaving.set(false);
    this.infoSuccess.set(true);
    setTimeout(() => this.infoSuccess.set(false), 3000);
  }

  savePassword(): void {
    this.pwError.set('');
    this.pwSuccess.set(false);
    if (this.pwForm.invalid) { this.pwForm.markAllAsTouched(); return; }
    const { current, next, confirm } = this.pwForm.getRawValue();
    if (next !== confirm) { this.pwError.set('New passwords do not match.'); return; }
    this.pwSaving.set(true);

    this.auth.changePassword(current!, next!).subscribe({
      next: () => {
        this.pwSaving.set(false);
        this.pwSuccess.set(true);
        this.pwForm.reset();
        setTimeout(() => this.pwSuccess.set(false), 3000);
      },
      error: () => {
        this.pwSaving.set(false);
        this.pwError.set('Current password is incorrect or request failed.');
      },
    });
  }

  protected isInvalid(form: 'info' | 'pw', field: string): boolean {
    const group: AbstractControl = form === 'info' ? this.infoForm : this.pwForm;
    const ctrl = group.get(field);
    return !!(ctrl?.invalid && ctrl.touched);
  }
}
