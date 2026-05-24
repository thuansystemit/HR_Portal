import { Component, OnInit, inject, input, signal } from '@angular/core';
import { Router } from '@angular/router';
import { FormBuilder, Validators } from '@angular/forms';
import { SHARED_IMPORTS } from '../../../../shared/shared.imports';
import { CvShareApi } from '../../services/cv-share.api';
import { CvShare, Impression } from '../../models/cv-share.model';
import { CvCandidateStore } from '../../../cv-candidates/store/cv-candidate.store';
import { CvWorkExperience } from '../../../cv-candidates/models/cv-candidate.model';
import { DropdownOption } from '../../../../shared/components/dropdown/dropdown.model';

@Component({
  selector: 'app-cv-share-detail',
  imports: [...SHARED_IMPORTS],
  templateUrl: './cv-share-detail.page.html',
  styleUrl:    './cv-share-detail.page.scss',
})
export class CvShareDetailPage implements OnInit {
  shareId = input<string>('');

  private readonly api    = inject(CvShareApi);
  private readonly fb     = inject(FormBuilder);
  private readonly router = inject(Router);
  protected readonly store = inject(CvCandidateStore);

  protected share   = signal<CvShare | null>(null);
  protected loading = signal(false);
  protected saving  = signal(false);
  protected error   = signal<string | null>(null);
  protected success = signal<string | null>(null);

  readonly impressionOptions: DropdownOption[] = [
    { value: 'INTERESTED',     label: 'Interested' },
    { value: 'NOT_INTERESTED', label: 'Not Interested' },
    { value: 'REVIEW_LATER',   label: 'Review Later' },
  ];

  protected impressionForm = this.fb.group({
    impression: ['' as Impression, Validators.required],
    comment:    [''],
  });

  ngOnInit(): void {
    this.loading.set(true);
    this.api.getById(this.shareId()).subscribe({
      next: share => {
        this.share.set(share);
        if (share.impression) {
          this.impressionForm.patchValue({ impression: share.impression, comment: share.comment ?? '' });
        }
        this.store.loadById(share.cvCandidateId);
        this.loading.set(false);
      },
      error: () => { this.error.set('Could not load shared CV.'); this.loading.set(false); },
    });
  }

  protected submitImpression(): void {
    if (this.impressionForm.invalid) { this.impressionForm.markAllAsTouched(); return; }
    const { impression, comment } = this.impressionForm.getRawValue();
    const share = this.share();
    if (!share) return;

    this.saving.set(true);
    this.error.set(null);
    this.success.set(null);
    this.api.submitImpression(share.hiringRequestId, share.id, {
      impression: impression!,
      comment: comment ?? '',
    }).subscribe({
      next: updated => {
        this.share.set(updated);
        this.success.set('Impression submitted successfully.');
        this.saving.set(false);
      },
      error: err => {
        this.error.set(err.error?.message || 'Failed to submit impression.');
        this.saving.set(false);
      },
    });
  }

  protected goBack(): void { this.router.navigate(['/cv-shares/inbox']); }

  protected impressionBadge(imp: string): string {
    return { INTERESTED: 'bg-success', NOT_INTERESTED: 'bg-danger', REVIEW_LATER: 'bg-warning text-dark' }[imp] ?? 'bg-secondary';
  }

  protected formatDateRange(exp: CvWorkExperience): string {
    const start = this.formatExpDate(exp.startDate);
    const end   = exp.isCurrent ? 'Present' : this.formatExpDate(exp.endDate);
    if (!start && !end) return '';
    if (!start) return end;
    if (!end)   return start;
    return `${start} – ${end}`;
  }

  private formatExpDate(raw: string | null): string {
    if (!raw) return '';
    try {
      const d = new Date(raw);
      if (isNaN(d.getTime())) return raw;
      return d.toLocaleDateString('en-US', { month: 'short', year: 'numeric' });
    } catch { return raw; }
  }
}
