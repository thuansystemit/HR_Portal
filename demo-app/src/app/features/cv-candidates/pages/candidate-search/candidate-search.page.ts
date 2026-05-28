import {
  Component, OnInit, TemplateRef, ViewChild, computed, inject, signal,
} from '@angular/core';
import { FormBuilder, FormGroup } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { SHARED_IMPORTS } from '../../../../shared/shared.imports';
import { TableColumn, PageEvent } from '../../../../shared/components/data-table/data-table.model';
import { CvSearchStore } from '../../store/cv-search.store';
import { CvSearchResult, CvSearchParams } from '../../models/cv-search.model';
import { RecruitmentApi } from '../../../recruitment/services/recruitment.api';
import { JobPostingSummary } from '../../../recruitment/models/recruitment.model';

@Component({
  selector: 'app-candidate-search',
  imports: [...SHARED_IMPORTS],
  templateUrl: './candidate-search.page.html',
  styleUrl: './candidate-search.page.scss',
})
export class CandidateSearchPage implements OnInit {
  protected readonly store          = inject(CvSearchStore);
  private  readonly router         = inject(Router);
  private  readonly route          = inject(ActivatedRoute);
  private  readonly fb             = inject(FormBuilder);
  private  readonly recruitmentApi = inject(RecruitmentApi);

  @ViewChild('scoreTpl',       { static: true }) scoreTpl!:       TemplateRef<{ $implicit: unknown; row: unknown }>;
  @ViewChild('skillsTpl',      { static: true }) skillsTpl!:      TemplateRef<{ $implicit: unknown; row: unknown }>;
  @ViewChild('locationTpl',    { static: true }) locationTpl!:    TemplateRef<{ $implicit: unknown; row: unknown }>;
  @ViewChild('actionsTpl',     { static: true }) actionsTpl!:     TemplateRef<{ $implicit: unknown; row: unknown }>;
  @ViewChild('batchSelectTpl', { static: true }) batchSelectTpl!: TemplateRef<{ $implicit: unknown; row: unknown }>;

  protected form!: FormGroup;
  protected columns   = signal<TableColumn[]>([]);
  protected page      = signal(1);
  protected total     = signal(0);
  protected pageCount = computed(() => Math.ceil(this.store.totalElements() / (this.store.pageSize() || 20)) || 1);
  protected hasSearched = signal(false);

  /** Job posting context — set when navigated from Job List or Board */
  protected forJobPostingId = signal<string | null>(null);

  /** Skill tag input state */
  protected skillTags  = signal<string[]>([]);
  protected skillInput = signal('');

  /** Apply-to-Job modal state */
  protected showApplyModal    = signal(false);
  protected applyTarget       = signal<CvSearchResult | null>(null);
  protected openPostings      = signal<JobPostingSummary[]>([]);
  protected applyJobId        = signal<string>('');
  protected applyNotes        = signal<string>('');
  protected applyLoading      = signal(false);
  protected applyError        = signal<string | null>(null);
  protected applySuccess      = signal<string | null>(null);

  /** Batch-apply state — only active when forJobPostingId is set */
  protected selectedIds  = signal<Set<string>>(new Set());
  protected batchLoading = signal(false);
  protected batchResult  = signal<{ applied: number; skipped: number } | null>(null);

  ngOnInit(): void {
    this.form = this.fb.group({
      title:               [''],
      location:            [''],
      minYearsExperience:  [null],
      keyword:             [''],
      sortBy:              ['relevanceScore'],
    });

    // Read job posting context from query params
    const jobPostingId = this.route.snapshot.queryParams['forJobPostingId'] as string | undefined;
    if (jobPostingId) {
      this.forJobPostingId.set(jobPostingId);
    }

    // Read skills pre-filter from query params (navigated from Top Skills chart)
    const skillsParam = this.route.snapshot.queryParams['skills'] as string | undefined;
    if (skillsParam) {
      const tags = skillsParam.split(',').map((s: string) => s.trim()).filter(Boolean);
      this.skillTags.set(tags);
      // auto-trigger search with a microtask to ensure form is ready
      Promise.resolve().then(() => this.onSearch());
    }

    const checkboxCol: TableColumn = {
      key: 'select', label: '', align: 'center', cellTemplate: this.batchSelectTpl,
    };
    const baseCols: TableColumn[] = [
      { key: 'relevanceScore', label: 'Score',       sortable: true, align: 'center', cellTemplate: this.scoreTpl },
      { key: 'fullName',       label: 'Name',        sortable: true, filterable: true },
      { key: 'currentTitle',   label: 'Current Title', sortable: true },
      { key: 'topSkills',      label: 'Top Skills',  cellTemplate: this.skillsTpl },
      { key: 'totalExperienceYears', label: 'Experience', sortable: true,
        formatter: (v) => v != null ? `${v} yrs` : '-' },
      { key: 'city',           label: 'Location',    cellTemplate: this.locationTpl },
      { key: 'email',          label: 'Email' },
      { key: 'actions',        label: '',             align: 'center', cellTemplate: this.actionsTpl },
    ];
    this.columns.set(this.forJobPostingId() ? [checkboxCol, ...baseCols] : baseCols);
  }

  // ── Skill tag management ──────────────────────────────────────

  addSkillTag(): void {
    const val = this.skillInput().trim();
    if (val && !this.skillTags().includes(val)) {
      this.skillTags.update(tags => [...tags, val]);
    }
    this.skillInput.set('');
  }

  removeSkillTag(tag: string): void {
    this.skillTags.update(tags => tags.filter(t => t !== tag));
  }

  onSkillKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' || event.key === ',') {
      event.preventDefault();
      this.addSkillTag();
    }
  }

  // ── Search actions ────────────────────────────────────────────

  onSearch(): void {
    if (this.skillInput().trim()) {
      this.addSkillTag();
    }
    const formVal = this.form.value;
    const params: CvSearchParams = {
      skills: this.skillTags().join(',') || undefined,
      title: formVal.title || undefined,
      location: formVal.location || undefined,
      minYearsExperience: formVal.minYearsExperience || undefined,
      keyword: formVal.keyword || undefined,
      sortBy: formVal.sortBy || 'relevanceScore',
      page: 0,
      size: 20,
      forJobPostingId: this.forJobPostingId() ?? undefined,
    };
    this.hasSearched.set(true);
    this.page.set(1);
    this.store.search(params);
  }

  onClear(): void {
    this.form.reset({ sortBy: 'relevanceScore' });
    this.skillTags.set([]);
    this.skillInput.set('');
    this.hasSearched.set(false);
    this.store.clear();
  }

  onPageChange(newPage: number): void {
    this.page.set(newPage);
    this.store.goToPage(newPage - 1);
  }

  onStateChange(state: PageEvent): void {
    this.total.set(state.total);
  }

  viewCandidate(row: unknown): void {
    const result = row as CvSearchResult;
    this.router.navigate(['/cv-candidates', result.documentCategoryId, result.candidateId]);
  }

  // ── Apply to Job modal ────────────────────────────────────────

  openApplyModal(row: unknown): void {
    const result = row as CvSearchResult;
    this.applyTarget.set(result);
    this.applyJobId.set('');
    this.applyNotes.set('');
    this.applyError.set(null);
    this.applySuccess.set(null);
    this.openPostings.set([]);
    this.showApplyModal.set(true);

    this.recruitmentApi.listPostings('OPEN', 0, 100).subscribe({
      next: page => this.openPostings.set(page.content),
      error: ()  => this.applyError.set('Could not load job postings.'),
    });
  }

  closeApplyModal(): void {
    this.showApplyModal.set(false);
    this.applyTarget.set(null);
  }

  submitApply(): void {
    const jobId    = this.applyJobId();
    const candidate = this.applyTarget();
    if (!jobId || !candidate) { this.applyError.set('Please select a job posting.'); return; }

    this.applyLoading.set(true);
    this.applyError.set(null);
    this.recruitmentApi.applyCandidate(jobId, {
      cvCandidateId: candidate.candidateId,
      notes: this.applyNotes() || undefined,
    }).subscribe({
      next: () => {
        this.applyLoading.set(false);
        this.applySuccess.set(`${candidate.fullName} applied successfully.`);
        this.applyJobId.set('');
      },
      error: err => {
        this.applyLoading.set(false);
        this.applyError.set(err.error?.message || 'Failed to apply candidate.');
      },
    });
  }

  // ── Batch-apply ───────────────────────────────────────────────

  clearSelection(): void {
    this.selectedIds.set(new Set());
  }

  toggleSelect(candidateId: string): void {
    this.selectedIds.update(set => {
      const next = new Set(set);
      next.has(candidateId) ? next.delete(candidateId) : next.add(candidateId);
      return next;
    });
  }

  isSelected(candidateId: string): boolean {
    return this.selectedIds().has(candidateId);
  }

  applySelected(): void {
    const jobId = this.forJobPostingId();
    if (!jobId || this.selectedIds().size === 0) return;
    this.batchLoading.set(true);
    this.batchResult.set(null);
    this.recruitmentApi.batchApply(jobId, {
      cvCandidateIds: [...this.selectedIds()],
    }).subscribe({
      next: result => {
        this.batchLoading.set(false);
        this.batchResult.set({ applied: result.applied.length, skipped: result.skipped.length });
        this.selectedIds.set(new Set());
        // refresh search to update "Applied" badges
        this.onSearch();
      },
      error: () => {
        this.batchLoading.set(false);
        this.batchResult.set(null);
      },
    });
  }
}
