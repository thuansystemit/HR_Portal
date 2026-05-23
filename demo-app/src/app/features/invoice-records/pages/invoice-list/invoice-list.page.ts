import {
  Component, OnInit, TemplateRef, ViewChild, computed, inject, input, signal,
} from '@angular/core';
import { Router } from '@angular/router';
import { SHARED_IMPORTS } from '../../../../shared/shared.imports';
import { TableColumn, PageEvent } from '../../../../shared/components/data-table/data-table.model';
import { DialogService } from '../../../../shared/components/dialog/dialog.service';
import { appFormatDate } from '../../../../shared/utils/date.utils';
import { InvoiceRecordStore } from '../../store/invoice-record.store';
import { InvoiceRecord } from '../../models/invoice-record.model';

@Component({
  selector: 'app-invoice-list',
  imports: [...SHARED_IMPORTS],
  templateUrl: './invoice-list.page.html',
  styleUrl:    './invoice-list.page.scss',
})
export class InvoiceListPage implements OnInit {
  categoryId = input<string>('');

  protected readonly store  = inject(InvoiceRecordStore);
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
      { key: 'invoiceNumber', label: 'Invoice #',    sortable: true, filterable: true },
      { key: 'invoiceDate',   label: 'Invoice Date', sortable: true, formatter: v => v ? String(v) : '—' },
      { key: 'dueDate',       label: 'Due Date',     sortable: true, formatter: v => v ? String(v) : '—' },
      { key: 'currency',      label: 'Currency',     sortable: true },
      { key: 'total',         label: 'Total',        sortable: true, align: 'end',
        formatter: v => v != null ? Number(v).toLocaleString(undefined, { minimumFractionDigits: 2 }) : '—' },
      { key: 'confidenceOverall', label: 'Confidence', sortable: true, align: 'center',
        cellTemplate: this.confidenceTpl },
      { key: 'extractedAt',   label: 'Extracted At', sortable: true, formatter: appFormatDate },
      { key: 'actions',       label: 'Actions',      align: 'center', cellTemplate: this.actionsTpl },
    ]);
  }

  onStateChange(state: PageEvent): void {
    this.total.set(state.total);
    if (state.page !== this.page()) this.page.set(state.page);
  }

  viewDetail(row: unknown): void {
    const record = row as InvoiceRecord;
    this.router.navigate(['/invoice-records', this.categoryId(), record.id]);
  }

  async confirmDelete(row: unknown): Promise<void> {
    const record = row as InvoiceRecord;
    const label = record.invoiceNumber ?? record.id;
    const ok = await this.dialog.confirm(
      'Delete Invoice Record',
      `Delete the invoice record "${label}"? This cannot be undone.`,
    );
    if (ok) this.store.remove(record.id);
  }
}
