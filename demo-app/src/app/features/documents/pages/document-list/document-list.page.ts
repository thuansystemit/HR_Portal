import {
  Component, OnInit, TemplateRef, ViewChild, computed, inject, input, signal,
} from '@angular/core';
import { Router } from '@angular/router';
import { SHARED_IMPORTS } from '../../../../shared/shared.imports';
import { TableColumn, PageEvent } from '../../../../shared/components/data-table/data-table.model';
import { DialogService } from '../../../../shared/components/dialog/dialog.service';
import { appFormatDate } from '../../../../shared/utils/date.utils';
import { formatFileSize } from '../../utils/file-size.utils';
import { DocumentFormBody } from '../../components/document-form-body/document-form-body';
import { DocumentCategoryStore } from '../../store/document-category.store';
import { DocumentStore } from '../../store/document.store';
import { AppDocument } from '../../models/document.model';
import { AuthService } from '../../../../auth/services/auth';
import { DocPreview } from '../../../../shared/components/doc-preview/doc-preview';
import { environment } from '../../../../../environments/environment';

@Component({
  selector: 'app-document-list',
  imports: [...SHARED_IMPORTS],
  templateUrl: './document-list.page.html',
  styleUrl:    './document-list.page.scss',
})
export class DocumentListPage implements OnInit {
  categoryId = input<string>('');

  protected readonly catStore = inject(DocumentCategoryStore);
  protected readonly docStore = inject(DocumentStore);
  private  readonly auth     = inject(AuthService);
  private  readonly dialog   = inject(DialogService);
  private  readonly router   = inject(Router);

  private catPerm = computed(() => {
    const cat  = this.catStore.selected();
    const user = this.auth.user();
    if (!cat || !user) return null;
    return cat.permissions.find(p => p.roleId === user.roleId) ?? null;
  });

  protected canView      = computed(() => this.catPerm()?.canView   ?? false);
  protected canUpload    = computed(() => this.catPerm()?.canUpload  ?? false);
  protected canDeleteDoc = computed(() => this.catPerm()?.canDelete  ?? false);

  @ViewChild('docTypeTpl', { static: true }) docTypeTpl!: TemplateRef<{ $implicit: unknown; row: unknown }>;
  @ViewChild('actionsTpl', { static: true }) actionsTpl!: TemplateRef<{ $implicit: unknown; row: unknown }>;

  readonly pageSize = 10;
  protected page      = signal(1);
  protected total     = signal(0);
  protected pageCount = computed(() => Math.ceil(this.total() / this.pageSize) || 1);
  protected columns   = signal<TableColumn[]>([]);

  protected get catId(): string { return this.categoryId(); }

  ngOnInit(): void {
    this.catStore.loadById(this.catId);
    this.docStore.loadByCategory(this.catId);
    this.columns.set([
      { key: 'name',         label: 'Document',    sortable: true, filterable: true },
      { key: 'documentType', label: 'Doc Type',    align: 'center', cellTemplate: this.docTypeTpl },
      { key: 'mimeType',     label: 'MIME Type',   sortable: true },
      { key: 'fileSize',    label: 'Size',        sortable: true, align: 'end',
        formatter: v => formatFileSize(Number(v)) },
      { key: 'uploadedBy',  label: 'Uploaded By', sortable: true },
      { key: 'uploadStatus', label: 'Status',     sortable: true },
      { key: 'createdAt',   label: 'Uploaded',    sortable: true, formatter: appFormatDate },
      { key: 'actions',     label: 'Actions',     align: 'center', cellTemplate: this.actionsTpl },
    ]);
  }

  onStateChange(state: PageEvent): void {
    this.total.set(state.total);
    if (state.page !== this.page()) this.page.set(state.page);
  }

  openPreview(doc: AppDocument): void {
    const url = `${environment.apiUrl}/categories/${doc.categoryId}/documents/${doc.id}/download`;
    this.dialog.open({
      title:        doc.name,
      type:         'info',
      showCancel:   false,
      confirmLabel: 'Close',
      size:         'xl',
      bodyComponent: DocPreview,
      bodyInputs:   { src: url },
    });
  }

  openUpload(): void {
    this.dialog.openForm(DocumentFormBody, {
      title: 'Upload Document', type: 'success', confirmLabel: 'Upload', size: 'lg',
    }, { categoryId: this.catId });
  }

  viewCandidate(_row: unknown): void {
    this.router.navigate(['/cv-candidates', this.catId]);
  }

  async confirmDelete(row: unknown): Promise<void> {
    const doc = row as AppDocument;
    const ok = await this.dialog.confirm(
      'Delete Document',
      `Delete "${doc.name}"? This cannot be undone.`,
    );
    if (ok) this.docStore.remove(doc.id);
  }
}
