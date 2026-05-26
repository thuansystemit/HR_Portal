import { Component, OnInit, inject, signal, input } from '@angular/core';
import { Router } from '@angular/router';
import { FormBuilder, Validators } from '@angular/forms';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { SHARED_IMPORTS } from '../../../../shared/shared.imports';
import { HiringRequestApi } from '../../services/hiring-request.api';
import { AuthService } from '../../../../auth/services/auth';
import { HiringRequest, RequestStatus } from '../../models/hiring-request.model';
import { DropdownOption } from '../../../../shared/components/dropdown/dropdown.model';
import { RecruitmentApi } from '../../../recruitment/services/recruitment.api';
import { CvShareApi } from '../../../cv-shares/services/cv-share.api';
import { CvShare } from '../../../cv-shares/models/cv-share.model';
import { UserApi } from '../../../users/services/user.api';
import { CvCandidateApi } from '../../../cv-candidates/services/cv-candidate.api';

@Component({
  selector: 'app-request-detail',
  imports: [...SHARED_IMPORTS],
  templateUrl: './request-detail.page.html',
  styleUrl: './request-detail.page.scss',
})
export class RequestDetailPage implements OnInit {
  requestId = input<string>('');

  private readonly api            = inject(HiringRequestApi);
  private readonly recruitmentApi = inject(RecruitmentApi);
  private readonly cvShareApi     = inject(CvShareApi);
  private readonly userApi        = inject(UserApi);
  private readonly cvCandidateApi = inject(CvCandidateApi);
  private readonly fb             = inject(FormBuilder);
  private readonly router         = inject(Router);
  protected readonly auth         = inject(AuthService);

  protected request            = signal<HiringRequest | null>(null);
  protected loading            = signal(false);
  protected saving             = signal(false);
  protected shareSaving        = signal(false);
  protected error              = signal<string | null>(null);
  protected success            = signal<string | null>(null);
  protected shareError         = signal<string | null>(null);
  protected shareSuccess       = signal<string | null>(null);
  protected jobPostingOptions  = signal<DropdownOption[]>([]);
  protected devTeamOptions     = signal<DropdownOption[]>([]);
  protected candidateOptions   = signal<DropdownOption[]>([]);
  protected shares             = signal<CvShare[]>([]);

  /** Link Job Posting (dedicated action) */
  protected linkPosting        = signal<string>('');
  protected linkSaving         = signal(false);
  protected linkError          = signal<string | null>(null);
  protected linkSuccess        = signal<string | null>(null);

  protected get isHR(): boolean { return this.auth.can('hiringRequestsManage'); }

  protected statusForm = this.fb.group({
    status:        ['' as RequestStatus, Validators.required],
    jobPostingId:  [''],
  });

  protected shareForm = this.fb.group({
    cvCandidateId: ['', Validators.required],
    sharedWith:    ['', Validators.required],
    comment:       [''],
  });

  readonly statusOptions: DropdownOption[] = [
    { value: 'PENDING',          label: 'Pending' },
    { value: 'IN_PROGRESS',      label: 'In Progress' },
    { value: 'CANDIDATE_FOUND',  label: 'Candidate Found' },
    { value: 'HIRED',            label: 'Hired' },
    { value: 'CLOSED',           label: 'Closed' },
    { value: 'REJECTED',         label: 'Rejected' },
  ];

  ngOnInit(): void {
    this.loading.set(true);
    this.api.getById(this.requestId()).subscribe({
      next: req => {
        this.request.set(req);
        this.statusForm.patchValue({
          status:       req.status,
          jobPostingId: req.jobPostingId ?? '',
        });
        this.loading.set(false);
      },
      error: () => { this.error.set('Could not load request.'); this.loading.set(false); },
    });

    if (this.isHR) {
      this.loadShares();

      this.cvCandidateApi.search({ size: 200 })
        .pipe(catchError(() => of({ content: [] as import('../../../cv-candidates/models/cv-search.model').CvSearchResult[] })))
        .subscribe(page => {
          this.candidateOptions.set(
            page.content.map(c => ({ value: c.candidateId, label: c.fullName })),
          );
        });

      this.userApi.getByRoleName('Dev Team')
        .pipe(catchError(() => of([] as import('../../../users/models/user.model').User[])))
        .subscribe(users => {
          this.devTeamOptions.set(
            users.map(u => ({ value: u.id, label: `${u.name} (${u.email})` })),
          );
        });

      this.recruitmentApi.listPostings(undefined, 0, 100)
        .pipe(catchError(() => of({ content: [] as import('../../../recruitment/models/recruitment.model').JobPostingSummary[] })))
        .subscribe(page => {
          this.jobPostingOptions.set([
            { value: '', label: '— None —' },
            ...page.content.map(p => ({ value: p.id, label: `${p.title} (${p.status})` })),
          ]);
        });
    }
  }

  private loadShares(): void {
    this.cvShareApi.listByRequest(this.requestId()).subscribe({
      next: list => this.shares.set(list),
    });
  }

  protected updateStatus(): void {
    if (this.statusForm.invalid) { this.statusForm.markAllAsTouched(); return; }
    const { status, jobPostingId } = this.statusForm.getRawValue();
    this.saving.set(true);
    this.error.set(null);
    this.success.set(null);
    this.api.updateStatus(this.requestId(), status!, jobPostingId || undefined).subscribe({
      next: updated => {
        this.request.set(updated);
        this.statusForm.patchValue({ status: updated.status, jobPostingId: updated.jobPostingId ?? '' });
        this.success.set('Status updated successfully.');
        this.saving.set(false);
      },
      error: err => {
        this.error.set(err.error?.message || 'Failed to update status.');
        this.saving.set(false);
      },
    });
  }

  protected shareCV(): void {
    if (this.shareForm.invalid) { this.shareForm.markAllAsTouched(); return; }
    const { cvCandidateId, sharedWith, comment } = this.shareForm.getRawValue();
    this.shareSaving.set(true);
    this.shareError.set(null);
    this.shareSuccess.set(null);
    this.cvShareApi.share(this.requestId(), {
      cvCandidateId: cvCandidateId!,
      sharedWith:    sharedWith!,
      comment:       comment ?? undefined,
    }).subscribe({
      next: newShare => {
        this.shares.update(list => [newShare, ...list]);
        this.shareForm.reset({ cvCandidateId: '', sharedWith: '', comment: '' });
        this.shareSuccess.set('CV shared successfully.');
        this.shareSaving.set(false);
      },
      error: err => {
        this.shareError.set(err.error?.message || 'Failed to share CV.');
        this.shareSaving.set(false);
      },
    });
  }

  protected submitLinkPosting(): void {
    const jobPostingId = this.linkPosting();
    if (!jobPostingId) { this.linkError.set('Please select a job posting.'); return; }

    this.linkSaving.set(true);
    this.linkError.set(null);
    this.linkSuccess.set(null);

    this.api.linkJobPosting(this.requestId(), jobPostingId).subscribe({
      next: updated => {
        this.request.set(updated);
        this.linkSuccess.set('Job posting linked successfully.');
        this.linkSaving.set(false);
      },
      error: err => {
        this.linkError.set(err.error?.message || 'Failed to link job posting.');
        this.linkSaving.set(false);
      },
    });
  }

  protected impressionBadge(imp: string | null): string {
    if (!imp) return 'bg-secondary';
    return { INTERESTED: 'bg-success', NOT_INTERESTED: 'bg-danger', REVIEW_LATER: 'bg-warning text-dark' }[imp] ?? 'bg-secondary';
  }

  protected urgencyBadgeClass(urgency: string): string {
    const map: Record<string, string> = {
      LOW: 'bg-secondary', MEDIUM: 'bg-info text-dark',
      HIGH: 'bg-warning text-dark', CRITICAL: 'bg-danger',
    };
    return map[urgency] ?? 'bg-secondary';
  }

  protected statusBadgeClass(status: string): string {
    const map: Record<string, string> = {
      PENDING: 'bg-secondary', IN_PROGRESS: 'bg-primary',
      CANDIDATE_FOUND: 'bg-info text-dark', HIRED: 'bg-success',
      CLOSED: 'bg-dark', REJECTED: 'bg-danger',
    };
    return map[status] ?? 'bg-secondary';
  }

  protected roleTypeBadgeClass(roleType: string): string {
    const map: Record<string, string> = {
      FRONTEND: 'bg-primary', BACKEND: 'bg-success', FULLSTACK: 'bg-warning text-dark',
    };
    return map[roleType] ?? 'bg-secondary';
  }

  protected statusLabel(s: string): string { return s.replace(/_/g, ' '); }

  protected goBack(): void { this.router.navigate(['/hiring-requests']); }

  protected isCompleted(current: string, step: string): boolean {
    const order = ['PENDING', 'IN_PROGRESS', 'CANDIDATE_FOUND', 'HIRED'];
    return order.indexOf(current) > order.indexOf(step);
  }
}
