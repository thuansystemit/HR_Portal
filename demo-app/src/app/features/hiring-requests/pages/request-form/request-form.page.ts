import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { SHARED_IMPORTS } from '../../../../shared/shared.imports';
import { DropdownOption } from '../../../../shared/components/dropdown/dropdown.model';
import { HiringRequestApi } from '../../services/hiring-request.api';
import { CreateHiringRequestDto, RoleType, Urgency } from '../../models/hiring-request.model';

@Component({
  selector: 'app-request-form',
  imports: [...SHARED_IMPORTS],
  templateUrl: './request-form.page.html',
  styleUrl: './request-form.page.scss',
})
export class RequestFormPage implements OnInit {
  private readonly fb     = inject(FormBuilder);
  private readonly router = inject(Router);
  private readonly api    = inject(HiringRequestApi);

  protected saving      = signal(false);
  protected serverError = signal<string | null>(null);

  protected form = this.fb.group({
    title:       ['', [Validators.required, Validators.minLength(2)]],
    roleType:    ['' as RoleType,  Validators.required],
    department:  ['', Validators.required],
    urgency:     ['MEDIUM' as Urgency, Validators.required],
    description: [''],
  });

  readonly roleTypeOptions: DropdownOption[] = [
    { value: 'FRONTEND',  label: 'Frontend' },
    { value: 'BACKEND',   label: 'Backend' },
    { value: 'FULLSTACK', label: 'Fullstack' },
  ];

  readonly urgencyOptions: DropdownOption[] = [
    { value: 'LOW',      label: 'Low' },
    { value: 'MEDIUM',   label: 'Medium' },
    { value: 'HIGH',     label: 'High' },
    { value: 'CRITICAL', label: 'Critical' },
  ];

  ngOnInit(): void {
    // urgency defaults to MEDIUM — already set in form definition
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const raw = this.form.getRawValue() as {
      title: string;
      roleType: RoleType;
      department: string;
      urgency: Urgency;
      description: string;
    };

    const dto: CreateHiringRequestDto = {
      title:      raw.title.trim(),
      roleType:   raw.roleType,
      department: raw.department.trim(),
      urgency:    raw.urgency,
      ...(raw.description?.trim() ? { description: raw.description.trim() } : {}),
    };

    this.saving.set(true);
    this.serverError.set(null);

    this.api.create(dto).subscribe({
      next: () => {
        this.saving.set(false);
        this.router.navigate(['/hiring-requests']);
      },
      error: err => {
        this.serverError.set(err.error?.message || err.message || 'Failed to submit request. Please try again.');
        this.saving.set(false);
      },
    });
  }

  protected isInvalid(field: string): boolean {
    const ctrl = this.form.get(field);
    return !!(ctrl?.invalid && ctrl.touched);
  }
}
