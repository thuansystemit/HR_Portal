import { Component, OnInit, computed, inject, input } from '@angular/core';
import { Router } from '@angular/router';
import { SHARED_IMPORTS } from '../../../../shared/shared.imports';
import { CvCandidateStore } from '../../store/cv-candidate.store';
import { CvWorkExperience } from '../../models/cv-candidate.model';

@Component({
  selector: 'app-candidate-detail',
  imports: [...SHARED_IMPORTS],
  templateUrl: './candidate-detail.page.html',
  styleUrl:    './candidate-detail.page.scss',
})
export class CandidateDetailPage implements OnInit {
  categoryId  = input<string>('');
  candidateId = input<string>('');

  protected readonly store  = inject(CvCandidateStore);
  private  readonly router = inject(Router);

  protected readonly candidate = this.store.selected;

  ngOnInit(): void {
    this.store.loadById(this.candidateId());
  }

  goBack(): void {
    this.router.navigate(['/cv-candidates', this.categoryId()]);
  }

  /** Returns a human-readable date range for a work experience entry. */
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
    } catch {
      return raw;
    }
  }
}
