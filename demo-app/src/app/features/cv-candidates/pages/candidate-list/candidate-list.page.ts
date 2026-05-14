import {
  Component, OnInit, TemplateRef, ViewChild, computed, inject, input, signal,
} from '@angular/core';
import { Router } from '@angular/router';
import { SHARED_IMPORTS } from '../../../../shared/shared.imports';
import { TableColumn, PageEvent } from '../../../../shared/components/data-table/data-table.model';
import { DialogService } from '../../../../shared/components/dialog/dialog.service';
import { appFormatDate } from '../../../../shared/utils/date.utils';
import { CvCandidateStore } from '../../store/cv-candidate.store';
import { CvCandidate } from '../../models/cv-candidate.model';

@Component({
  selector: 'app-candidate-list',
  imports: [...SHARED_IMPORTS],
  templateUrl: './candidate-list.page.html',
  styleUrl:    './candidate-list.page.scss',
})
export class CandidateListPage implements OnInit {
  categoryId = input<string>('');

  protected readonly store  = inject(CvCandidateStore);
  private  readonly router = inject(Router);
  private  readonly dialog = inject(DialogService);

  @ViewChild('confidenceTpl', { static: true }) confidenceTpl!: TemplateRef<{ $implicit: unknown; row: unknown }>;
  @ViewChild('actionsTpl',    { static: true }) actionsTpl!:    TemplateRef<{ $implicit: unknown; row: unknown }>;

  readonly pageSize = 10;
  protected page      = signal(1);
  protected total     = signal(0);
  protected pageCount = computed(() => Math.ceil(this.total() / this.pageSize) || 1);
  protected columns   = signal<TableColumn[]>([]);

  ngOnInit(): void {
    this.store.loadByCategory(this.categoryId());
    this.columns.set([
      { key: 'fullName',    label: 'Full Name',     sortable: true, filterable: true },
      { key: 'email',       label: 'Email',         sortable: true, filterable: true },
      { key: 'phone',       label: 'Phone' },
      { key: 'city',        label: 'Location',      sortable: true,
        formatter: (_v, row) => {
          const c = row as CvCandidate;
          return [c.city, c.country].filter(Boolean).join(', ');
        },
      },
      { key: 'confidenceOverall', label: 'Confidence', sortable: true, align: 'center',
        cellTemplate: this.confidenceTpl },
      { key: 'extractedAt', label: 'Extracted At',  sortable: true, formatter: appFormatDate },
      { key: 'actions',     label: 'Actions',       align: 'center', cellTemplate: this.actionsTpl },
    ]);
  }

  onStateChange(state: PageEvent): void {
    this.total.set(state.total);
    if (state.page !== this.page()) this.page.set(state.page);
  }

  viewProfile(row: unknown): void {
    const candidate = row as CvCandidate;
    this.router.navigate(['/cv-candidates', this.categoryId(), candidate.id]);
  }

  async confirmDelete(row: unknown): Promise<void> {
    const candidate = row as CvCandidate;
    const ok = await this.dialog.confirm(
      'Delete Candidate',
      `Delete the CV profile for "${candidate.fullName}"? This cannot be undone.`,
    );
    if (ok) this.store.remove(candidate.id);
  }
}
