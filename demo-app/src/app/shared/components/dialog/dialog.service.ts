import {
  ApplicationRef, ComponentRef, Type, createComponent,
  EnvironmentInjector, inject, Injectable,
} from '@angular/core';
import { Dialog } from './dialog';
import { DialogConfig } from './dialog.model';

/** A handle returned by `DialogService.open()` and `openForm()`. */
export interface DialogRef {
  /** Resolves `true` when confirmed, `false` when cancelled or dismissed. */
  result: Promise<boolean>;
  /** Closes the dialog programmatically and resolves the promise with `false`. */
  dismiss(): void;
}

@Injectable({ providedIn: 'root' })
export class DialogService {
  private appRef  = inject(ApplicationRef);
  private injector = inject(EnvironmentInjector);

  open(config: DialogConfig): DialogRef {
    let resolveResult!: (value: boolean) => void;
    const result = new Promise<boolean>(resolve => { resolveResult = resolve; });

    const ref: ComponentRef<Dialog> = createComponent(Dialog, {
      environmentInjector: this.injector,
    });

    ref.setInput('config', config);

    let destroyed = false;

    const destroyOnce = (): void => {
      if (destroyed) return;
      destroyed = true;
      sub.unsubscribe();
      this.destroy(ref);
    };

    const sub = ref.instance.result.subscribe(value => {
      resolveResult(value);
      destroyOnce();
    });

    this.appRef.attachView(ref.hostView);
    document.body.appendChild(ref.location.nativeElement);
    ref.changeDetectorRef.detectChanges();

    const dismiss = (): void => {
      resolveResult(false);
      destroyOnce();
    };

    return { result, dismiss };
  }

  /** Opens a dialog whose body is rendered by a custom component. */
  openForm<T>(
    component: Type<T>,
    config: Omit<DialogConfig, 'message' | 'bodyComponent' | 'bodyInputs'>,
    inputs?: Record<string, unknown>,
  ): DialogRef {
    return this.open({ ...config, bodyComponent: component as Type<unknown>, bodyInputs: inputs });
  }

  confirm(
    title: string,
    message?: string,
    partial?: Partial<DialogConfig>,
  ): Promise<boolean> {
    return this.open({ title, message, type: 'danger', ...partial }).result;
  }

  alert(
    title: string,
    message?: string,
    partial?: Partial<DialogConfig>,
  ): Promise<boolean> {
    return this.open({ title, message, type: 'info', showCancel: false, confirmLabel: 'OK', ...partial }).result;
  }

  private destroy(ref: ComponentRef<Dialog>): void {
    this.appRef.detachView(ref.hostView);
    ref.location.nativeElement.remove();
    ref.destroy();
  }
}
