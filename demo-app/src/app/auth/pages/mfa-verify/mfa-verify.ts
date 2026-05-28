import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';
import { AuthService } from '../../services/auth';

@Component({
  selector: 'app-mfa-verify',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './mfa-verify.html',
})
export class MfaVerify implements OnInit {
  private readonly auth           = inject(AuthService);
  private readonly router         = inject(Router);
  private readonly route          = inject(ActivatedRoute);
  private readonly fb             = inject(FormBuilder);

  protected form = this.fb.group({
    code: ['', [Validators.required, Validators.pattern(/^[\d]{6}$|^[A-Fa-f0-9]{10}$/)]],
  });

  protected loading        = signal(false);
  protected error          = signal('');
  protected isBackupMode   = signal(false);
  private   challengeToken = '';

  ngOnInit(): void {
    this.challengeToken = this.route.snapshot.queryParams['token'] ?? '';
    if (!this.challengeToken) {
      this.router.navigate(['/login']);
    }
  }

  submit(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.loading.set(true);
    this.error.set('');

    const code = this.form.getRawValue().code!;
    this.auth.verifyMfa(this.challengeToken, code)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: () => this.router.navigate(['/']),
        error: () => this.error.set('Invalid code. Please try again.'),
      });
  }

  toggleMode(): void {
    this.isBackupMode.update(v => !v);
    this.form.reset();
    this.error.set('');
    const pattern = this.isBackupMode()
      ? /^[A-Fa-f0-9]{10}$/
      : /^\d{6}$/;
    this.form.get('code')!.setValidators([Validators.required, Validators.pattern(pattern)]);
    this.form.get('code')!.updateValueAndValidity();
  }
}
