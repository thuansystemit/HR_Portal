import {
  Component, OnInit, TemplateRef, ViewChild, computed, inject, signal,
} from '@angular/core';
import { SHARED_IMPORTS } from '../../../../shared/shared.imports';
import { appFormatDate } from '../../../../shared/utils/date.utils';
import { TableColumn, PageEvent } from '../../../../shared/components/data-table/data-table.model';
import { DialogService } from '../../../../shared/components/dialog/dialog.service';
import { AuthService } from '../../../../auth/services/auth';
import { UserFormBody } from '../../components/user-form-body/user-form-body';
import { UserStatusBadge } from '../../components/user-status-badge/user-status-badge';
import { UserStore } from '../../store/user.store';
import { User } from '../../models/user.model';
import { DropdownOption } from '../../../../shared/components/dropdown/dropdown.model';

@Component({
  selector: 'app-user-list',
  imports: [...SHARED_IMPORTS, UserStatusBadge],
  templateUrl: './user-list.page.html',
  styleUrl:    './user-list.page.scss',
})
export class UserListPage implements OnInit {
  protected readonly store  = inject(UserStore);
  protected readonly auth   = inject(AuthService);
  private  readonly dialog = inject(DialogService);

  @ViewChild('statusTpl',  { static: true }) statusTpl!:  TemplateRef<{ $implicit: unknown; row: unknown }>;
  @ViewChild('actionsTpl', { static: true }) actionsTpl!: TemplateRef<{ $implicit: unknown; row: unknown }>;

  readonly pageSize = 8;
  protected page      = signal(1);
  protected total     = signal(0);
  protected pageCount = computed(() => Math.ceil(this.total() / this.pageSize) || 1);

  private readonly roleOptions: DropdownOption[] = [
    { value: 'admin',   label: 'Admin' },
    { value: 'manager', label: 'Manager' },
    { value: 'viewer',  label: 'Viewer' },
  ];

  private readonly statusOptions: DropdownOption[] = [
    { value: 'active',   label: 'Active' },
    { value: 'inactive', label: 'Inactive' },
    { value: 'pending',  label: 'Pending' },
  ];

  protected columns = signal<TableColumn[]>([]);

  ngOnInit(): void {
    this.store.loadAll();
    this.columns.set([
      { key: 'id',        label: '#',       sortable: true, align: 'center' },
      { key: 'name',      label: 'Name',    sortable: true, filterable: true },
      { key: 'email',     label: 'Email',   sortable: true, filterable: true },
      {
        key: 'role', label: 'Role', sortable: true,
        filterable: true, filterType: 'dropdown', filterOptions: this.roleOptions,
      },
      {
        key: 'status', label: 'Status', sortable: true,
        filterable: true, filterType: 'dropdown', filterOptions: this.statusOptions,
        cellTemplate: this.statusTpl,
      },
      { key: 'createdAt', label: 'Created', sortable: true, formatter: appFormatDate },
      { key: 'actions',   label: 'Actions', align: 'center', cellTemplate: this.actionsTpl },
    ]);
  }

  onStateChange(state: PageEvent): void {
    this.total.set(state.total);
    if (state.page !== this.page()) this.page.set(state.page);
  }

  openAdd(): void {
    this.dialog.openForm(UserFormBody, {
      title: 'Add User', type: 'success', confirmLabel: 'Create User', size: 'lg',
    });
  }

  edit(row: unknown): void {
    const user = row as User;
    this.dialog.openForm(
      UserFormBody,
      { title: `Edit User — ${user.name}`, type: 'warning', confirmLabel: 'Save Changes', size: 'lg' },
      { editId: user.id },
    );
  }

  async confirmDelete(row: unknown): Promise<void> {
    const user = row as User;
    const ok = await this.dialog.confirm(
      'Delete User',
      `Are you sure you want to delete "${user.name}"? This action cannot be undone.`,
    );
    if (ok) this.store.remove(user.id);
  }
}
