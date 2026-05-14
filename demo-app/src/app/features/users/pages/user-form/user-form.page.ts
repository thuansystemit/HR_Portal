import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { FormBuilder, Validators } from '@angular/forms';
import { SHARED_IMPORTS } from '../../../../shared/shared.imports';
import { DropdownOption } from '../../../../shared/components/dropdown/dropdown.model';
import { DialogService } from '../../../../shared/components/dialog/dialog.service';
import { RoleStore } from '../../../roles/store/role.store';
import { UserStore } from '../../store/user.store';
import { UserRole, UserStatus } from '../../models/user.model';

@Component({
  selector: 'app-user-form',
  imports: [...SHARED_IMPORTS],
  templateUrl: './user-form.page.html',
  styleUrl:    './user-form.page.scss',
})
export class UserFormPage implements OnInit {
  private readonly route     = inject(ActivatedRoute);
  private readonly fb        = inject(FormBuilder);
  private readonly dialog    = inject(DialogService);
  protected readonly store   = inject(UserStore);
  protected readonly roleStore = inject(RoleStore);

  protected editId: string | null = null;
  protected get isEdit() { return this.editId !== null; }

  protected form = this.fb.group({
    name:     ['',               [Validators.required, Validators.minLength(2)]],
    email:    ['',               [Validators.required, Validators.email]],
    roleId:   ['' as string,     Validators.required],
    status:   ['' as UserStatus, Validators.required],
    password: ['',               [Validators.minLength(8)]],
  });

  readonly statusOptions: DropdownOption[] = [
    { value: 'active',   label: 'Active' },
    { value: 'inactive', label: 'Inactive' },
    { value: 'pending',  label: 'Pending' },
  ];

  ngOnInit(): void {
    this.roleStore.loadAll();
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.editId = id;
      this.store.loadById(this.editId);
      this.form.get('password')?.clearValidators();
    } else {
      this.store.clearSelected();
      this.form.get('password')?.addValidators(Validators.required);
    }
    this.form.get('password')?.updateValueAndValidity();
  }

  ngDoCheck(): void {
    const user = this.store.selected();
    if (this.isEdit && user && !this.form.dirty) {
      this.form.patchValue({
        name:   user.name,
        email:  user.email,
        roleId: user.roleId,
        status: user.status,
      });
    }
  }

  get roleOptions(): DropdownOption[] {
    return this.roleStore.roles().map(r => ({ value: r.id, label: r.name }));
  }

  protected async submit(): Promise<void> {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }

    const raw = this.form.getRawValue() as { name: string; email: string; roleId: string; status: UserStatus; password: string };
    const selectedRole = this.roleStore.roles().find(r => r.id === raw.roleId);
    const dto = {
      name:     raw.name,
      email:    raw.email,
      roleId:   raw.roleId,
      role:     (selectedRole?.name.toLowerCase() === 'administrator' ? 'admin'
                 : selectedRole?.name.toLowerCase() === 'manager' ? 'manager' : 'viewer') as UserRole,
      status:   raw.status,
      ...(raw.password ? { password: raw.password } : {}),
    };

    const confirmed = await this.dialog.open({
      title:        this.isEdit ? 'Save Changes' : 'Create User',
      message:      this.isEdit
        ? `Save changes to "${dto.name}"?`
        : `Create a new user account for "${dto.name}"?`,
      type:         this.isEdit ? 'warning' : 'success',
      confirmLabel: this.isEdit ? 'Save Changes' : 'Create',
    });

    if (!confirmed) return;
    this.isEdit ? this.store.update(this.editId!, dto) : this.store.create(dto);
  }

  protected isInvalid(field: string): boolean {
    const ctrl = this.form.get(field);
    return !!(ctrl?.invalid && ctrl.touched);
  }
}
