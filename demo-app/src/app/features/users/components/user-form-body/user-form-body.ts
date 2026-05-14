import {
  Component, OnDestroy, OnInit, effect, inject, input, untracked,
} from '@angular/core';
import { FormBuilder, Validators } from '@angular/forms';
import { SHARED_IMPORTS } from '../../../../shared/shared.imports';
import { DropdownOption } from '../../../../shared/components/dropdown/dropdown.model';
import { DialogFormBody } from '../../../../shared/components/dialog/dialog.model';
import { DialogFormRegistry } from '../../../../shared/components/dialog/dialog-form-registry.service';
import { RoleStore } from '../../../roles/store/role.store';
import { UserStore } from '../../store/user.store';
import { UserRole, UserStatus, UserDto } from '../../models/user.model';

@Component({
  selector: 'app-user-form-body',
  imports: [...SHARED_IMPORTS],
  templateUrl: './user-form-body.html',
  styleUrl: './user-form-body.scss',
})
export class UserFormBody implements OnInit, OnDestroy, DialogFormBody {
  editId = input<string | null>(null);

  private readonly fb          = inject(FormBuilder);
  private readonly registry    = inject(DialogFormRegistry, { optional: true });
  protected readonly store     = inject(UserStore);
  protected readonly roleStore = inject(RoleStore);

  protected get isEdit(): boolean { return this.editId() !== null; }

  protected form = this.fb.group({
    name:     ['',               [Validators.required, Validators.minLength(2)]],
    email:    ['',               [Validators.required, Validators.email]],
    roleId:   ['' as string,     Validators.required],
    status:   ['' as UserStatus, Validators.required],
    password: ['',               [Validators.minLength(8)]],
  });

  get roleOptions(): DropdownOption[] {
    return this.roleStore.roles().map(r => ({ value: r.id, label: r.name }));
  }

  readonly statusOptions: DropdownOption[] = [
    { value: 'active',   label: 'Active' },
    { value: 'inactive', label: 'Inactive' },
    { value: 'pending',  label: 'Pending' },
  ];

  constructor() {
    // React to editId signal: load user data or reset for create
    effect(() => {
      const id = this.editId();
      untracked(() => {
        this.store.clearSelected();
        this.form.reset({ name: '', email: '', roleId: '', status: '' as UserStatus, password: '' });

        if (id !== null) {
          this.store.loadById(id);
        } else {
          // password required only when creating
          this.form.get('password')?.addValidators(Validators.required);
          this.form.get('password')?.updateValueAndValidity();
        }
      });
    });

    // Patch form fields once the selected user arrives from the store
    effect(() => {
      const user = this.store.selected();
      if (this.editId() !== null && user) {
        untracked(() => {
          this.form.patchValue({
            name:   user.name,
            email:  user.email,
            roleId: user.roleId,
            status: user.status,
          });
        });
      }
    });
  }

  ngOnInit(): void {
    this.registry?.register(this);
    if (this.roleStore.roles().length === 0) this.roleStore.loadAll();
  }

  ngOnDestroy(): void {
    this.registry?.unregister();
    this.store.clearSelected();
  }

  async onConfirm(): Promise<boolean> {
    if (this.form.invalid) { this.form.markAllAsTouched(); return false; }

    const raw = this.form.getRawValue() as {
      name: string; email: string; roleId: string; status: UserStatus; password: string;
    };
    const selectedRole = this.roleStore.roles().find(r => r.id === raw.roleId);
    const roleName = selectedRole?.name.toLowerCase() ?? '';
    const dto: UserDto = {
      name:   raw.name,
      email:  raw.email,
      roleId: raw.roleId,
      role:   (roleName === 'administrator' ? 'admin' : roleName === 'manager' ? 'manager' : 'viewer') as UserRole,
      status: raw.status,
      ...(raw.password ? { password: raw.password } : {}),
    };

    return new Promise<boolean>(resolve => {
      const onSuccess = () => { this.store.clearSelected(); resolve(true); };
      const onError   = () => resolve(false);
      this.isEdit
        ? this.store.update(this.editId()!, dto, onSuccess, onError)
        : this.store.create(dto, onSuccess, onError);
    });
  }

  protected isInvalid(field: string): boolean {
    const ctrl = this.form.get(field);
    return !!(ctrl?.invalid && ctrl.touched);
  }
}
