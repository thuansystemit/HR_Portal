import { Component, HostListener, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormBuilder, Validators } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { SHARED_IMPORTS } from '../../../../shared/shared.imports';
import { RecruitmentStore } from '../../store/recruitment.store';
import { RecruitmentApi } from '../../services/recruitment.api';
import { Application, AppStage, BoardResponse } from '../../models/recruitment.model';
import { environment } from '../../../../../environments/environment';

interface CvCandidateOption {
  id: string;
  fullName: string;
  email: string;
}

@Component({
  selector: 'app-job-board',
  imports: [...SHARED_IMPORTS, RouterLink],
  templateUrl: './job-board.page.html',
  styleUrl:    './job-board.page.scss',
})
export class JobBoardPage implements OnInit {
  private readonly route   = inject(ActivatedRoute);
  private readonly router  = inject(Router);
  private readonly fb      = inject(FormBuilder);
  private readonly http    = inject(HttpClient);
  private readonly api     = inject(RecruitmentApi);
  protected readonly store = inject(RecruitmentStore);

  protected jobId = '';
  protected readonly STAGE_ORDER: AppStage[] = [
    'APPLIED', 'SCREENING', 'INTERVIEW', 'OFFER', 'HIRED', 'REJECTED',
  ];

  // ── Apply Candidate Modal ───────────────────────────────────────────────────
  protected showApplyModal    = signal(false);
  protected candidateOptions  = signal<CvCandidateOption[]>([]);
  protected candidateSearch   = signal('');
  protected selectedCandidate = signal<CvCandidateOption | null>(null);
  protected applyError        = signal<string | null>(null);
  protected applyLoading      = signal(false);
  protected applyForm = this.fb.group({
    candidateQuery: [''],
    notes:          [''],
  });

  // ── Interview Modal ─────────────────────────────────────────────────────────
  protected showInterviewModal = signal(false);
  protected interviewAppId     = signal<string | null>(null);
  protected interviewError     = signal<string | null>(null);
  protected interviewLoading   = signal(false);
  protected interviewForm = this.fb.group({
    scheduledAt: ['', Validators.required],
    meetingLink: [''],
    notes:       [''],
  });

  // ── Pipeline stats ──────────────────────────────────────────────────────────
  protected readonly pipelineStats = computed(() => {
    const board = this.store.board();
    if (!board) return null;
    const total = this.STAGE_ORDER.reduce((s, st) => s + (board.columns[st]?.length ?? 0), 0);
    const hired = board.columns['HIRED']?.length ?? 0;
    const active = total - (board.columns['HIRED']?.length ?? 0) - (board.columns['REJECTED']?.length ?? 0);
    const hireRate = total === 0 ? 0 : Math.round(hired / total * 100);
    return { total, active, hired, hireRate };
  });

  // ── Move Stage ──────────────────────────────────────────────────────────────
  protected movingAppId     = signal<string | null>(null);
  protected openMoveId      = signal<string | null>(null);
  protected moveDropdownPos = signal<{ top: number; left: number }>({ top: 0, left: 0 });

  toggleMove(appId: string, event: MouseEvent): void {
    event.stopPropagation();
    if (this.openMoveId() === appId) { this.openMoveId.set(null); return; }
    const rect = (event.currentTarget as HTMLElement).getBoundingClientRect();
    this.moveDropdownPos.set({ top: rect.bottom + 4, left: rect.left });
    this.openMoveId.set(appId);
  }

  @HostListener('document:click')
  closeMoveDropdown(): void { this.openMoveId.set(null); }

  ngOnInit(): void {
    this.jobId = this.route.snapshot.paramMap.get('id') ?? '';
    this.store.loadPosting(this.jobId);
    this.store.loadBoard(this.jobId);
  }

  get board(): BoardResponse | null {
    return this.store.board();
  }

  getColumn(stage: AppStage): Application[] {
    return this.board?.columns[stage] ?? [];
  }

  getStageLabel(stage: AppStage): string {
    const labels: Record<AppStage, string> = {
      APPLIED:   'Applied',
      SCREENING: 'Screening',
      INTERVIEW: 'Interview',
      OFFER:     'Offer',
      HIRED:     'Hired',
      REJECTED:  'Rejected',
    };
    return labels[stage] ?? stage;
  }

  getStageBadgeClass(stage: AppStage): string {
    const classes: Record<AppStage, string> = {
      APPLIED:   'bg-secondary',
      SCREENING: 'bg-info text-dark',
      INTERVIEW: 'bg-primary',
      OFFER:     'bg-warning text-dark',
      HIRED:     'bg-success',
      REJECTED:  'bg-danger',
    };
    return classes[stage] ?? 'bg-secondary';
  }

  getNextStages(current: AppStage): AppStage[] {
    return this.STAGE_ORDER.filter(s => s !== current);
  }

  protected fitScoreBadgeClass(score: number): string {
    if (score >= 70) return 'bg-success';
    if (score >= 40) return 'bg-warning text-dark';
    return 'bg-danger';
  }

  // ── Find Candidates ─────────────────────────────────────────────────────────

  findCandidates(): void {
    this.router.navigate(['/cv-candidates/search'], {
      queryParams: { forJobPostingId: this.jobId },
    });
  }

  // ── Apply Candidate Modal ───────────────────────────────────────────────────

  openApplyModal(): void {
    this.applyForm.reset();
    this.selectedCandidate.set(null);
    this.candidateOptions.set([]);
    this.applyError.set(null);
    this.showApplyModal.set(true);
  }

  closeApplyModal(): void {
    this.showApplyModal.set(false);
  }

  searchCandidates(query: string): void {
    this.candidateSearch.set(query);
    if (!query || query.length < 2) {
      this.candidateOptions.set([]);
      return;
    }
    this.http
      .get<CvCandidateOption[]>(`${environment.apiUrl}/cv-candidates/search-simple`, {
        params: { q: query, size: '10' },
      })
      .subscribe({
        next: results => this.candidateOptions.set(results),
        error: () => this.candidateOptions.set([]),
      });
  }

  selectCandidate(candidate: CvCandidateOption): void {
    this.selectedCandidate.set(candidate);
    this.candidateOptions.set([]);
    this.applyForm.patchValue({ candidateQuery: candidate.fullName });
  }

  submitApply(): void {
    if (!this.selectedCandidate()) {
      this.applyError.set('Please select a candidate.');
      return;
    }
    this.applyLoading.set(true);
    this.applyError.set(null);
    this.api
      .applyCandidate(this.jobId, {
        cvCandidateId: this.selectedCandidate()!.id,
        notes: this.applyForm.value.notes || undefined,
      })
      .subscribe({
        next: () => {
          this.closeApplyModal();
          this.applyLoading.set(false);
          this.store.loadBoard(this.jobId);
        },
        error: err => {
          this.applyError.set(err.error?.message || 'Failed to apply candidate');
          this.applyLoading.set(false);
        },
      });
  }

  // ── Move Stage ──────────────────────────────────────────────────────────────

  moveStage(app: Application, targetStage: AppStage): void {
    this.openMoveId.set(null);
    this.movingAppId.set(app.id);
    this.api
      .moveStage(this.jobId, app.id, { stage: targetStage })
      .subscribe({
        next: () => {
          this.movingAppId.set(null);
          this.store.loadBoard(this.jobId);
        },
        error: () => this.movingAppId.set(null),
      });
  }

  // ── Interview Modal ─────────────────────────────────────────────────────────

  openInterviewModal(app: Application): void {
    this.interviewAppId.set(app.id);
    this.interviewForm.reset();
    this.interviewError.set(null);
    this.showInterviewModal.set(true);
  }

  closeInterviewModal(): void {
    this.showInterviewModal.set(false);
    this.interviewAppId.set(null);
  }

  submitInterview(): void {
    if (this.interviewForm.invalid) { this.interviewForm.markAllAsTouched(); return; }
    const appId = this.interviewAppId();
    if (!appId) return;

    this.interviewLoading.set(true);
    this.interviewError.set(null);
    const raw = this.interviewForm.getRawValue();
    this.api
      .scheduleInterview(appId, {
        scheduledAt: raw.scheduledAt ? new Date(raw.scheduledAt).toISOString() : '',
        meetingLink: raw.meetingLink || undefined,
        notes:       raw.notes || undefined,
      })
      .subscribe({
        next: () => {
          this.closeInterviewModal();
          this.interviewLoading.set(false);
          this.store.loadBoard(this.jobId);
        },
        error: err => {
          this.interviewError.set(err.error?.message || 'Failed to schedule interview');
          this.interviewLoading.set(false);
        },
      });
  }
}
