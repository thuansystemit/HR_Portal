import {
  Component, OnInit, TemplateRef, ViewChild, computed, inject, signal,
} from '@angular/core';
import { Router } from '@angular/router';
import { SHARED_IMPORTS } from '../../../../shared/shared.imports';
import { TableColumn, PageEvent } from '../../../../shared/components/data-table/data-table.model';
import { DialogService } from '../../../../shared/components/dialog/dialog.service';
import { AuthService } from '../../../../auth/services/auth';
import { appFormatDate } from '../../../../shared/utils/date.utils';
import { CategoryFormBody } from '../../components/category-form-body/category-form-body';
import { CategoryPermCell } from '../../components/category-perm-cell/category-perm-cell';
import { DocumentCategoryStore } from '../../store/document-category.store';
import { DocumentCategory } from '../../models/document.model';

@Component({
  selector: 'app-category-list',
  imports: [...SHARED_IMPORTS, CategoryPermCell],
  templateUrl: './category-list.page.html',
  styleUrl:    './category-list.page.scss',
})
export class CategoryListPage implements OnInit {
  protected readonly store  = inject(DocumentCategoryStore);
  protected readonly auth   = inject(AuthService);
  private  readonly dialog = inject(DialogService);
  private  readonly router = inject(Router);

  @ViewChild('typeTpl',    { static: true }) typeTpl!:    TemplateRef<{ $implicit: unknown; row: unknown }>;
  @ViewChild('permTpl',    { static: true }) permTpl!:    TemplateRef<{ $implicit: unknown; row: unknown }>;
  @ViewChild('actionsTpl', { static: true }) actionsTpl!: TemplateRef<{ $implicit: unknown; row: unknown }>;

  protected visibleCategories = computed(() => {
    const user = this.auth.user();
    if (!user) return [];
    return this.store.categories().filter(cat => {
      const perm = cat.permissions.find(p => p.roleId === user.roleId);
      return perm?.canView ?? false;
    });
  });

  readonly pageSize = 10;
  protected page      = signal(1);
  protected total     = signal(0);
  protected pageCount = computed(() => Math.ceil(this.total() / this.pageSize) || 1);
  protected columns   = signal<TableColumn[]>([]);

  ngOnInit(): void {
    this.store.loadAll();
    this.columns.set([
      { key: 'name',          label: 'Category',    sortable: true, filterable: true },
      { key: 'description',   label: 'Description' },
      { key: 'documentType',  label: 'Type',        sortable: true, align: 'center', cellTemplate: this.typeTpl },
      ...( this.auth.can('rolesView')
        ? [{ key: 'permissions', label: 'Role Permissions', cellTemplate: this.permTpl }]
        : [] ),
      { key: 'documentCount', label: 'Documents',   sortable: true, align: 'center' },
      { key: 'createdAt',     label: 'Created',     sortable: true, formatter: appFormatDate },
      { key: 'updatedAt',     label: 'Updated',     sortable: true, formatter: appFormatDate },
      { key: 'actions',       label: 'Actions',     align: 'center', cellTemplate: this.actionsTpl },
    ]);
  }

  onStateChange(state: PageEvent): void {
    this.total.set(state.total);
    if (state.page !== this.page()) this.page.set(state.page);
  }

  openAdd(): void {
    this.dialog.openForm(CategoryFormBody, {
      title: 'Add Category', type: 'success', confirmLabel: 'Create Category', size: 'lg',
    });
  }

  edit(row: unknown): void {
    const cat = row as DocumentCategory;
    this.dialog.openForm(
      CategoryFormBody,
      { title: `Edit Category — ${cat.name}`, type: 'warning', confirmLabel: 'Save Changes', size: 'lg' },
      { editId: cat.id },
    );
  }

  openDocuments(row: unknown): void {
    this.router.navigate(['/documents', (row as DocumentCategory).id]);
  }

  openCandidates(row: unknown): void {
    this.router.navigate(['/cv-candidates', (row as DocumentCategory).id]);
  }

  async confirmDelete(row: unknown): Promise<void> {
    const cat = row as DocumentCategory;
    const ok = await this.dialog.confirm(
      'Delete Category',
      `Delete "${cat.name}" and all its documents? This cannot be undone.`,
    );
    if (ok) this.store.remove(cat.id);
  }
}
