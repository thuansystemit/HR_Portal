import {
  Component, OnInit, TemplateRef, ViewChild, computed, inject, signal,
} from '@angular/core';
import { SHARED_IMPORTS } from '../../../../shared/shared.imports';
import { TableColumn, PageEvent } from '../../../../shared/components/data-table/data-table.model';
import { DialogService } from '../../../../shared/components/dialog/dialog.service';
import { AuthService } from '../../../../auth/services/auth';
import { appFormatDate } from '../../../../shared/utils/date.utils';
import { RoleFormBody } from '../../components/role-form-body/role-form-body';
import { RoleStore } from '../../store/role.store';
import { Role, TOTAL_PERMISSIONS } from '../../models/role.model';

@Component({
  selector: 'app-role-list',
  imports: [...SHARED_IMPORTS],
  templateUrl: './role-list.page.html',
  styleUrl:    './role-list.page.scss',
})
export class RoleListPage implements OnInit {
  protected readonly store  = inject(RoleStore);
  protected readonly auth   = inject(AuthService);
  private  readonly dialog = inject(DialogService);

  @ViewChild('nameTpl',    { static: true }) nameTpl!:    TemplateRef<{ $implicit: unknown; row: unknown }>;
  @ViewChild('permTpl',    { static: true }) permTpl!:    TemplateRef<{ $implicit: unknown; row: unknown }>;
  @ViewChild('actionsTpl', { static: true }) actionsTpl!: TemplateRef<{ $implicit: unknown; row: unknown }>;

  readonly pageSize = 10;
  protected page      = signal(1);
  protected total     = signal(0);
  protected pageCount = computed(() => Math.ceil(this.total() / this.pageSize) || 1);

  protected columns = signal<TableColumn[]>([]);

  ngOnInit(): void {
    this.store.loadAll();
    this.columns.set([
      { key: 'name',        label: 'Role',        sortable: true, filterable: true,
        cellTemplate: this.nameTpl },
      { key: 'description', label: 'Description' },
      { key: 'permissions', label: 'Permissions', align: 'center',
        cellTemplate: this.permTpl },
      { key: 'userCount',   label: 'Users',       sortable: true, align: 'center' },
      { key: 'createdAt',   label: 'Created',     sortable: true, formatter: appFormatDate },
      { key: 'actions',     label: 'Actions',     align: 'center', cellTemplate: this.actionsTpl },
    ]);
  }

  onStateChange(state: PageEvent): void {
    this.total.set(state.total);
    if (state.page !== this.page()) this.page.set(state.page);
  }

  openAdd(): void {
    this.dialog.openForm(RoleFormBody, {
      title: 'Add Role', type: 'success', confirmLabel: 'Create Role', size: 'lg',
    });
  }

  edit(row: unknown): void {
    const role = row as Role;
    this.dialog.openForm(
      RoleFormBody,
      { title: `Edit Role — ${role.name}`, type: 'warning', confirmLabel: 'Save Changes', size: 'lg' },
      { editId: role.id },
    );
  }

  async confirmDelete(row: unknown): Promise<void> {
    const role = row as Role;
    const ok = await this.dialog.confirm(
      'Delete Role',
      `Are you sure you want to delete the "${role.name}" role? This cannot be undone.`,
    );
    if (ok) this.store.remove(role.id);
  }

  permCount(row: unknown): number {
    return Object.values((row as Role).permissions).filter(Boolean).length;
  }

  protected readonly totalPermissions = TOTAL_PERMISSIONS;
}
