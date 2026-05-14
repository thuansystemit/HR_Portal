import {
  Component, OnDestroy, OnInit, effect, inject, input, untracked,
} from '@angular/core';
import { FormBuilder, Validators } from '@angular/forms';
import { SHARED_IMPORTS } from '../../../../shared/shared.imports';
import { DialogFormBody } from '../../../../shared/components/dialog/dialog.model';
import { DialogFormRegistry } from '../../../../shared/components/dialog/dialog-form-registry.service';
import { RoleStore } from '../../store/role.store';
import { RoleDto, RolePermissions, PERMISSION_GROUPS, PERMISSION_LABELS } from '../../models/role.model';

@Component({
  selector: 'app-role-form-body',
  imports: [...SHARED_IMPORTS],
  templateUrl: './role-form-body.html',
  styleUrl: './role-form-body.scss',
})
export class RoleFormBody implements OnInit, OnDestroy, DialogFormBody {
  editId = input<string | null>(null);

  private readonly fb       = inject(FormBuilder);
  private readonly registry = inject(DialogFormRegistry, { optional: true });
  protected readonly store  = inject(RoleStore);

  protected get isEdit(): boolean { return this.editId() !== null; }

  protected readonly permissionGroups = PERMISSION_GROUPS;
  protected readonly permissionLabels = PERMISSION_LABELS;

  protected form = this.fb.group({
    name:        ['', [Validators.required, Validators.minLength(2)]],
    description: [''],
    permissions: this.fb.group({
      usersView:   [false],
      usersCreate: [false],
      usersEdit:   [false],
      usersDelete: [false],
      rolesView:   [false],
      rolesEdit:   [false],
    }),
  });

  constructor() {
    // React to editId: reset and load when editing
    effect(() => {
      const id = this.editId();
      untracked(() => {
        this.store.clearSelected();
        this.form.reset({
          name: '', description: '',
          permissions: {
            usersView: false, usersCreate: false, usersEdit: false,
            usersDelete: false, rolesView: false, rolesEdit: false,
          },
        });
        if (id !== null) this.store.loadById(id);
      });
    });

    // Patch form once selected role arrives
    effect(() => {
      const role = this.store.selected();
      if (this.editId() !== null && role) {
        untracked(() => {
          this.form.patchValue({
            name:        role.name,
            description: role.description,
            permissions: { ...role.permissions },
          });
        });
      }
    });
  }

  ngOnInit(): void  { this.registry?.register(this); }
  ngOnDestroy(): void {
    this.registry?.unregister();
    this.store.clearSelected();
  }

  async onConfirm(): Promise<boolean> {
    if (this.form.invalid) { this.form.markAllAsTouched(); return false; }

    const raw = this.form.getRawValue();
    const dto: RoleDto = {
      name:        raw.name!,
      description: raw.description ?? '',
      permissions: raw.permissions as RolePermissions,
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
