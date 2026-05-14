import {
  Component, HostListener, Injector,
  computed, inject, input, output, signal,
} from '@angular/core';
import { NgClass, NgComponentOutlet } from '@angular/common';
import { AppButton, BtnVariant } from '../app-button/app-button';
import { DialogConfig, DialogType } from './dialog.model';
import { DialogFormRegistry } from './dialog-form-registry.service';

@Component({
  selector: 'app-dialog',
  imports: [NgClass, NgComponentOutlet, AppButton],
  providers: [DialogFormRegistry],
  templateUrl: './dialog.html',
  styleUrl: './dialog.scss',
})
export class Dialog {
  config = input.required<DialogConfig>();

  /** Emits true (confirmed) or false (cancelled/dismissed). */
  result = output<boolean>();

  protected visible  = signal(true);
  protected readonly injector  = inject(Injector);
  private  readonly registry  = inject(DialogFormRegistry);

  // ── Derived display values ────────────────────────────────────
  protected iconClass = computed(() => {
    const map: Record<DialogType, string> = {
      info:    'bi-info-circle-fill text-info',
      success: 'bi-check-circle-fill text-success',
      warning: 'bi-exclamation-triangle-fill text-warning',
      danger:  'bi-x-circle-fill text-danger',
    };
    return map[this.config().type ?? 'info'];
  });

  protected confirmVariant = computed<BtnVariant>(() => {
    const map: Record<DialogType, BtnVariant> = {
      info:    'primary',
      success: 'success',
      warning: 'warning',
      danger:  'danger',
    };
    return map[this.config().type ?? 'info'];
  });

  protected sizeClass = computed(() => {
    const map: Record<string, string> = {
      sm: 'modal-sm',
      md: '',
      lg: 'modal-lg',
      xl: 'modal-xl',
    };
    return map[this.config().size ?? 'md'];
  });

  protected get showCancel(): boolean {
    return this.config().showCancel !== false;
  }

  // ── Actions ───────────────────────────────────────────────────
  async confirm(): Promise<void> {
    const body = this.registry.instance;
    if (body) {
      const ok = await body.onConfirm();
      if (ok) this.close(true);
    } else {
      this.close(true);
    }
  }

  cancel(): void { this.close(false); }

  private close(value: boolean): void {
    this.visible.set(false);
    this.result.emit(value);
  }

  @HostListener('keydown.escape')
  onEscape(): void { this.cancel(); }
}
