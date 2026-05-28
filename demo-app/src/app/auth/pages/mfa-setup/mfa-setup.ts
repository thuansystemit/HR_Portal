import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../services/auth';

type Step = 'loading' | 'scan' | 'confirm' | 'backup' | 'error';

@Component({
  selector: 'app-mfa-setup',
  imports: [ReactiveFormsModule],
  templateUrl: './mfa-setup.html',
})
export class MfaSetup implements OnInit {
  private readonly route  = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly auth   = inject(AuthService);
  private readonly fb     = inject(FormBuilder);

  protected step           = signal<Step>('loading');
  protected qrCodeUri      = signal('');
  protected secret         = signal('');
  protected backupCodes    = signal<string[]>([]);
  protected error          = signal('');
  protected loading        = signal(false);
  protected enrollToken    = '';

  protected form = this.fb.group({
    code: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]],
  });

  ngOnInit(): void {
    const token = this.route.snapshot.queryParamMap.get('token');
    if (!token) {
      this.router.navigate(['/login']);
      return;
    }
    this.enrollToken = token;
    this.auth.initMfaEnrollment(token).subscribe({
      next: res => {
        this.qrCodeUri.set(res.qrCodeUri);
        this.secret.set(res.secret);
        this.step.set('scan');
      },
      error: () => {
        this.error.set('Enrollment session expired or invalid. Please log in again.');
        this.step.set('error');
      },
    });
  }

  proceed(): void {
    this.step.set('confirm');
  }

  submit(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.loading.set(true);
    this.error.set('');

    const code = this.form.getRawValue().code!;
    this.auth.confirmMfaEnrollment(this.enrollToken, code).subscribe({
      next: ({ backupCodes }) => {
        this.backupCodes.set(backupCodes);
        this.loading.set(false);
        this.step.set('backup');
      },
      error: () => {
        this.loading.set(false);
        this.error.set('Invalid code. Please try again.');
      },
    });
  }

  finish(): void {
    this.router.navigate(['/']);
  }

  protected isInvalid(): boolean {
    const ctrl = this.form.get('code');
    return !!(ctrl?.invalid && ctrl.touched);
  }
}
