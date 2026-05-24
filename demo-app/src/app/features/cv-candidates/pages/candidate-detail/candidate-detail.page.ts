import { Component, OnInit, inject, input, signal } from '@angular/core';
import { Router } from '@angular/router';
import { SHARED_IMPORTS } from '../../../../shared/shared.imports';
import { AuthService } from '../../../../auth/services/auth';
import { CvCandidateStore } from '../../store/cv-candidate.store';
import { CvCandidateApi } from '../../services/cv-candidate.api';
import { CandidateHiringStatus, CvWorkExperience } from '../../models/cv-candidate.model';
import { Application } from '../../../recruitment/models/recruitment.model';

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
  private  readonly api    = inject(CvCandidateApi);
  private  readonly auth   = inject(AuthService);

  protected readonly candidate     = this.store.selected;
  protected readonly applications  = signal<Application[]>([]);
  protected readonly appsLoading   = signal(false);
  protected readonly statusLoading = signal(false);

  protected get isHR(): boolean { return this.auth.can('hiringRequestsManage'); }

  private readonly STAGE_COLORS: Record<string, string> = {
    APPLIED:   'secondary',
    SCREENING: 'info',
    INTERVIEW: 'primary',
    OFFER:     'warning',
    HIRED:     'success',
    REJECTED:  'danger',
  };

  protected stageBadgeClass(stage: string): string {
    return 'badge bg-' + (this.STAGE_COLORS[stage] ?? 'secondary');
  }

  protected hiringStatusBadge(status: CandidateHiringStatus | null | undefined): string {
    const map: Record<string, string> = {
      AVAILABLE:  'badge bg-secondary',
      IN_PROCESS: 'badge bg-info text-dark',
      OFFERED:    'badge bg-warning text-dark',
      HIRED:      'badge bg-success',
      REJECTED:   'badge bg-danger',
      WITHDRAWN:  'badge bg-dark',
    };
    return map[status ?? 'AVAILABLE'] ?? 'badge bg-secondary';
  }

  protected hiringStatusLabel(status: CandidateHiringStatus | null | undefined): string {
    const map: Record<string, string> = {
      AVAILABLE:  'Available',
      IN_PROCESS: 'In Process',
      OFFERED:    'Offered',
      HIRED:      'Hired',
      REJECTED:   'Rejected',
      WITHDRAWN:  'Withdrawn',
    };
    return map[status ?? 'AVAILABLE'] ?? (status ?? 'Available');
  }

  ngOnInit(): void {
    this.store.loadById(this.candidateId());
    this.loadApplications();
  }

  private loadApplications(): void {
    this.appsLoading.set(true);
    this.api.getApplications(this.candidateId()).subscribe({
      next: data => { this.applications.set(data); this.appsLoading.set(false); },
      error: ()  => this.appsLoading.set(false),
    });
  }

  protected withdraw(): void {
    this.statusLoading.set(true);
    this.api.updateHiringStatus(this.candidateId(), 'WITHDRAWN').subscribe({
      next: () => { this.store.loadById(this.candidateId()); this.statusLoading.set(false); },
      error: () => this.statusLoading.set(false),
    });
  }

  protected returnToPool(): void {
    this.statusLoading.set(true);
    this.api.updateHiringStatus(this.candidateId(), 'AVAILABLE').subscribe({
      next: () => { this.store.loadById(this.candidateId()); this.statusLoading.set(false); },
      error: () => this.statusLoading.set(false),
    });
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
