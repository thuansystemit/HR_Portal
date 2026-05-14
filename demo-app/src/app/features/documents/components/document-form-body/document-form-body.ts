import {
  Component, OnInit, OnDestroy, inject, input, signal,
} from '@angular/core';
import { FormBuilder, Validators } from '@angular/forms';
import { SHARED_IMPORTS } from '../../../../shared/shared.imports';
import { DialogFormBody } from '../../../../shared/components/dialog/dialog.model';
import { DialogFormRegistry } from '../../../../shared/components/dialog/dialog-form-registry.service';
import { DocumentStore } from '../../store/document.store';
import { AppDocumentDto } from '../../models/document.model';
import { formatFileSize } from '../../utils/file-size.utils';

const ALLOWED_TYPES = [
  'application/pdf',
  'application/msword',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
];
const ALLOWED_EXT_RE = /\.(pdf|doc|docx)$/i;

@Component({
  selector: 'app-document-form-body',
  imports: [...SHARED_IMPORTS],
  templateUrl: './document-form-body.html',
  styleUrl: './document-form-body.scss',
})
export class DocumentFormBody implements OnInit, OnDestroy, DialogFormBody {
  categoryId = input.required<string>();

  private readonly fb       = inject(FormBuilder);
  private readonly registry = inject(DialogFormRegistry, { optional: true });
  protected readonly store  = inject(DocumentStore);

  protected form = this.fb.group({
    name:     ['', [Validators.required, Validators.minLength(2)]],
    fileName: ['', Validators.required],
    fileSize: [0, [Validators.required, Validators.min(1)]],
  });

  protected selectedFile   = signal<File | null>(null);
  protected fileTypeError  = signal(false);
  protected fileSizeError  = signal(false);
  protected readonly MAX_FILE_SIZE = 50 * 1024 * 1024; // 50 MB
  protected readonly formatFileSize = formatFileSize;

  ngOnInit(): void    { this.registry?.register(this); }
  ngOnDestroy(): void { this.registry?.unregister(); }

  protected onFileChange(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.applyFile(input.files?.[0] ?? null, input);
  }

  protected onFileDrop(event: DragEvent): void {
    event.preventDefault();
    this.applyFile(event.dataTransfer?.files?.[0] ?? null, null);
  }

  protected clearFile(input: HTMLInputElement): void {
    input.value = '';
    this.selectedFile.set(null);
    this.fileTypeError.set(false);
    this.fileSizeError.set(false);
    this.form.patchValue({ fileName: '', fileSize: 0 });
  }

  private applyFile(file: File | null, input: HTMLInputElement | null): void {
    if (!file) {
      this.selectedFile.set(null);
      this.fileTypeError.set(false);
      this.fileSizeError.set(false);
      this.form.patchValue({ fileName: '', fileSize: 0 });
      return;
    }

    const valid = ALLOWED_TYPES.includes(file.type) || ALLOWED_EXT_RE.test(file.name);
    if (!valid) {
      this.fileTypeError.set(true);
      this.fileSizeError.set(false);
      this.selectedFile.set(null);
      this.form.patchValue({ fileName: '', fileSize: 0 });
      if (input) input.value = '';
      return;
    }

    if (file.size > this.MAX_FILE_SIZE) {
      this.fileSizeError.set(true);
      this.fileTypeError.set(false);
      this.selectedFile.set(null);
      this.form.patchValue({ fileName: '', fileSize: 0 });
      if (input) input.value = '';
      return;
    }

    this.selectedFile.set(file);
    this.fileTypeError.set(false);
    this.fileSizeError.set(false);
    this.form.patchValue({ fileName: file.name, fileSize: file.size });
    this.form.get('fileName')!.markAsTouched();
    this.form.get('fileSize')!.markAsTouched();
  }

  async onConfirm(): Promise<boolean> {
    if (this.form.invalid || !this.selectedFile()) {
      this.form.markAllAsTouched();
      return false;
    }

    const raw = this.form.getRawValue();
    const dto: AppDocumentDto = {
      name:       raw.name!,
      categoryId: this.categoryId(),
      file:       this.selectedFile()!,
    };

    return new Promise<boolean>(resolve => {
      this.store.create(dto, () => resolve(true), () => resolve(false));
    });
  }

  protected isInvalid(field: string): boolean {
    const ctrl = this.form.get(field);
    return !!(ctrl?.invalid && ctrl.touched);
  }
}
