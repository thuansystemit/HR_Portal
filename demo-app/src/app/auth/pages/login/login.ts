import { Component, OnInit, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService, MfaChallenge, MfaEnrollmentChallenge } from '../../services/auth';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login implements OnInit {
  private readonly auth   = inject(AuthService);
  private readonly router = inject(Router);
  private readonly fb     = inject(FormBuilder);

  protected form = this.fb.group({
    email:    ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });

  protected error              = signal('');
  protected loading            = signal(false);
  protected showPwd            = signal(false);
  protected bannerText         = signal('');
  protected bannerAcknowledged = signal(false);

  readonly year = new Date().getFullYear();

  ngOnInit(): void {
    this.auth.getBanner().subscribe({
      next: res => this.bannerText.set(res.message),
      error: ()  => this.bannerAcknowledged.set(true),
    });
  }

  submit(): void {
    if (this.form.invalid || !this.bannerAcknowledged()) { this.form.markAllAsTouched(); return; }
    this.loading.set(true);
    this.error.set('');

    const { email, password } = this.form.getRawValue();
    this.auth.login({ email: email!, password: password! }).subscribe({
      next: result => {
        this.loading.set(false);
        if ((result as MfaEnrollmentChallenge).mfaEnrollmentRequired) {
          const { enrollmentToken } = result as MfaEnrollmentChallenge;
          this.router.navigate(['/mfa-setup'], { queryParams: { token: enrollmentToken } });
        } else if ((result as MfaChallenge).mfaRequired) {
          const { challengeToken } = result as MfaChallenge;
          this.router.navigate(['/mfa-verify'], { queryParams: { token: challengeToken } });
        } else {
          this.router.navigate(['/']);
        }
      },
      error: () => {
        this.loading.set(false);
        this.error.set('Invalid email or password. Please try again.');
      },
    });
  }

  protected isInvalid(field: string): boolean {
    const ctrl = this.form.get(field);
    return !!(ctrl?.invalid && ctrl.touched);
  }
}
