import {
  Component, OnDestroy, OnInit, effect, inject, input, untracked,
} from '@angular/core';
import { FormArray, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { SHARED_IMPORTS } from '../../../../shared/shared.imports';
import { DialogFormBody } from '../../../../shared/components/dialog/dialog.model';
import { DialogFormRegistry } from '../../../../shared/components/dialog/dialog-form-registry.service';
import { RoleStore } from '../../../roles/store/role.store';
import { DocumentCategoryStore } from '../../store/document-category.store';
import { CategoryRolePermission, DocumentCategoryDto, DocumentType, CATEGORY_PERM_COLS, DOCUMENT_TYPE_OPTIONS } from '../../models/document.model';

@Component({
  selector: 'app-category-form-body',
  imports: [...SHARED_IMPORTS],
  templateUrl: './category-form-body.html',
  styleUrl: './category-form-body.scss',
})
export class CategoryFormBody implements OnInit, OnDestroy, DialogFormBody {
  editId = input<string | null>(null);

  private readonly fb          = inject(FormBuilder);
  private readonly registry    = inject(DialogFormRegistry, { optional: true });
  protected readonly roleStore = inject(RoleStore);
  protected readonly catStore  = inject(DocumentCategoryStore);

  protected get isEdit(): boolean { return this.editId() !== null; }
  protected readonly permCols     = CATEGORY_PERM_COLS;
  protected readonly docTypeOptions = DOCUMENT_TYPE_OPTIONS;

  protected form = this.fb.group({
    name:          ['', [Validators.required, Validators.minLength(2)]],
    description:   [''],
    documentType:  ['' as DocumentType | '', Validators.required],
    llmExtraction: [true],
    permissions:   this.fb.array([]),
  });

  protected get isCvType(): boolean {
    return this.form.get('documentType')?.value === 'CV';
  }

  protected get isExtractableType(): boolean {
    const t = this.form.get('documentType')?.value;
    return t === 'CV' || t === 'INVOICE';
  }

  protected get permArray(): FormArray { return this.form.get('permissions') as FormArray; }

  constructor() {
    // React to editId: reset and load when editing
    effect(() => {
      const id = this.editId();
      untracked(() => {
        this.catStore.clearSelected();
        this.form.patchValue({ name: '', description: '', documentType: '' });
        this.permArray.clear();
        if (id !== null) this.catStore.loadById(id);
      });
    });

    // Build / rebuild permission rows whenever roles become available
    effect(() => {
      const roles = this.roleStore.roles();
      if (roles.length === 0) return;

      untracked(() => {
        if (this.permArray.length === roles.length) return;

        this.permArray.clear();
        for (const role of roles) {
          this.permArray.push(this.fb.group({
            roleId:    [role.id],
            roleName:  [role.name],
            canView:   [false],
            canUpload: [false],
            canDelete: [false],
          }));
        }

        // Re-apply category permissions if the category already loaded
        const cat = this.catStore.selected();
        if (cat) this.applyCategory(cat);
      });
    });

    // Patch form once category data arrives
    effect(() => {
      const cat = this.catStore.selected();
      if (this.editId() !== null && cat) {
        untracked(() => this.applyCategory(cat));
      }
    });
  }

  private applyCategory(cat: { name: string; description: string; documentType: DocumentType; permissions: CategoryRolePermission[]; llmExtraction?: boolean }): void {
    this.form.patchValue({ name: cat.name, description: cat.description, documentType: cat.documentType, llmExtraction: cat.llmExtraction ?? true });
    this.permArray.controls.forEach(ctrl => {
      const match = cat.permissions.find(p => p.roleId === ctrl.value.roleId);
      if (match) ctrl.patchValue(match, { emitEvent: false });
    });
  }

  ngOnInit(): void {
    this.registry?.register(this);
    if (this.roleStore.roles().length === 0) this.roleStore.loadAll();
  }

  ngOnDestroy(): void {
    this.registry?.unregister();
    this.catStore.clearSelected();
  }

  async onConfirm(): Promise<boolean> {
    if (this.form.invalid) { this.form.markAllAsTouched(); return false; }

    const raw = this.form.getRawValue();
    const dto: DocumentCategoryDto = {
      name:          raw.name!,
      description:   raw.description ?? '',
      documentType:  raw.documentType as DocumentType,
      llmExtraction: (raw.documentType === 'CV' || raw.documentType === 'INVOICE')
        ? (raw.llmExtraction ?? true)
        : true,
      permissions:   raw.permissions as CategoryRolePermission[],
    };

    return new Promise<boolean>(resolve => {
      const onSuccess = () => { this.catStore.clearSelected(); resolve(true); };
      const onError   = () => resolve(false);
      this.isEdit
        ? this.catStore.update(this.editId()!, dto, onSuccess, onError)
        : this.catStore.create(dto, onSuccess, onError);
    });
  }

  protected isInvalid(field: string): boolean {
    const ctrl = this.form.get(field);
    return !!(ctrl?.invalid && ctrl.touched);
  }

  protected permGroupAt(i: number): FormGroup {
    return this.permArray.at(i) as FormGroup;
  }
}
