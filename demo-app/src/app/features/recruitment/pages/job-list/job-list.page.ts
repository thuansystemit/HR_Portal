import { Component, OnInit, TemplateRef, ViewChild, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { SHARED_IMPORTS } from '../../../../shared/shared.imports';
import { TableColumn } from '../../../../shared/components/data-table/data-table.model';
import { DialogService } from '../../../../shared/components/dialog/dialog.service';
import { RecruitmentStore } from '../../store/recruitment.store';
import { JobPostingSummary } from '../../models/recruitment.model';

@Component({
  selector: 'app-job-list',
  imports: [...SHARED_IMPORTS],
  templateUrl: './job-list.page.html',
  styleUrl:    './job-list.page.scss',
})
export class JobListPage implements OnInit {
  protected readonly store  = inject(RecruitmentStore);
  private  readonly router  = inject(Router);
  private  readonly route   = inject(ActivatedRoute);
  private  readonly dialog  = inject(DialogService);

  @ViewChild('statusTpl',  { static: true }) statusTpl!:  TemplateRef<{ $implicit: unknown; row: unknown }>;
  @ViewChild('actionsTpl', { static: true }) actionsTpl!: TemplateRef<{ $implicit: unknown; row: unknown }>;

  protected activeStatus   = signal<string>('');
  protected columns        = signal<TableColumn[]>([]);
  protected highlightStage = signal<string | null>(null);

  readonly statusTabs = [
    { label: 'All',    value: '' },
    { label: 'Draft',  value: 'DRAFT' },
    { label: 'Open',   value: 'OPEN' },
    { label: 'Closed', value: 'CLOSED' },
  ];

  ngOnInit(): void {
    this.store.loadPostings();
    const stage = this.route.snapshot.queryParams['highlightStage'] as string | undefined;
    if (stage) this.highlightStage.set(stage);
    this.columns.set([
      { key: 'title',            label: 'Title',        sortable: true, filterable: true },
      { key: 'department',       label: 'Department',   sortable: true },
      { key: 'location',         label: 'Location',     sortable: true },
      { key: 'status',           label: 'Status',       align: 'center', cellTemplate: this.statusTpl },
      { key: 'deadline',         label: 'Deadline',     sortable: true },
      { key: 'applicationCount', label: 'Applications', align: 'center', sortable: true },
      { key: 'actions',          label: 'Actions',      align: 'center', cellTemplate: this.actionsTpl },
    ]);
  }

  selectStatus(status: string): void {
    this.activeStatus.set(status);
    this.store.loadPostings(status || undefined);
  }

  navigateNew(): void {
    this.router.navigate(['/recruitment/new']);
  }

  viewBoard(row: unknown): void {
    const posting = row as JobPostingSummary;
    this.router.navigate(['/recruitment', posting.id, 'board']);
  }

  editPosting(row: unknown): void {
    const posting = row as JobPostingSummary;
    this.router.navigate(['/recruitment', posting.id, 'edit']);
  }

  findCandidates(row: unknown): void {
    const posting = row as JobPostingSummary;
    this.router.navigate(['/cv-candidates/search'], {
      queryParams: { forJobPostingId: posting.id },
    });
  }

  async confirmDelete(row: unknown): Promise<void> {
    const posting = row as JobPostingSummary;
    const ok = await this.dialog.confirm(
      'Close Job Posting',
      `Close the job posting "${posting.title}"? It will no longer accept applications.`,
    );
    if (ok) this.store.deletePosting(posting.id);
  }
}
